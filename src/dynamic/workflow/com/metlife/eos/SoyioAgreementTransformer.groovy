package com.metlife.eos

import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import groovy.json.JsonSlurper
import org.springframework.stereotype.Component

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Component
class SoyioAgreementTransformer {

    private static final List<String> OUTPUT_COLUMNS = [
        'rut', 'rut_normalized', 'user_reference', 'agreement_id', 'created_at', 'version',
        'subject_id', 'subject_type', 'version_source_type', 'version_source_id', 'consent_status',
        'data_category', 'data_label', 'data_use', 'data_subject', 'scope_type', 'scope_id',
        'scope_version', 'expires_at', 'previous_evidence_ids', 'resolution_status',
        'resolution_error', 'data_permissions_raw', 'source_file', 'processed_at'
    ]

    Logger logger = LoggerFactory.getLogger(SoyioAgreementTransformer)

    Map transform(byte[] sourceBytes, String sourceFileName, Closure resolveReference, int batchSize, int delayMillis) {
        def startedAt = Instant.now()
        logger.info('::: Agreement transform START sourceFile={} bytes={} batchSize={} delayMillis={} :::', sourceFileName, sourceBytes?.length, batchSize, delayMillis)
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new IllegalArgumentException('Agreement source CSV is empty')
        }

        def rows = parseCsv(new String(sourceBytes, StandardCharsets.UTF_8))
        if (rows.size() < 2) {
            throw new IllegalArgumentException('Agreement source CSV does not contain data rows')
        }
        def headers = rows[0].collect { it?.trim() }
        def requiredColumns = ['created_at', 'id', 'version', 'user_reference', 'previous_evidence_ids', 'subject_id', 'subject_type', 'version_source_type', 'version_source_id', 'data_permissions']
        def missingColumns = requiredColumns.findAll { !headers.contains(it) }
        if (!missingColumns.isEmpty()) {
            throw new IllegalArgumentException("Agreement source CSV missing columns: ${missingColumns.join(', ')}")
        }

        def sourceRows = rows.drop(1).findAll { row -> row.any { value -> value != null && !value.isEmpty() } }
        def references = sourceRows.collect { row -> rowToMap(headers, row).user_reference?.toString() }.findAll { it != null && !it.isBlank() }.unique()
        def resolutionCache = [:]
        def validReferences = references.findAll { isApiReference(it) }
        def invalidReferences = references.findAll { !isApiReference(it) }
        invalidReferences.each { reference ->
            resolutionCache[reference] = [value: null, status: 'invalid_format', error: 'user_reference does not match API format (39-512 URL-safe characters)']
        }
        logger.info('::: Agreement transform references total={} validForApi={} invalidFormat={} sourceRows={} :::', references.size(), validReferences.size(), invalidReferences.size(), sourceRows.size())

        def segments = validReferences.collate(Math.max(batchSize, 1))
        segments.eachWithIndex { segment, segmentIndex ->
            logger.info('::: Agreement resolve segment {}/{} START size={} :::', segmentIndex + 1, segments.size(), segment.size())
            segment.eachWithIndex { reference, referenceIndex ->
                try {
                    resolutionCache[reference] = resolveReference.call(reference) ?: [value: null, status: 'failed', error: 'Resolver returned no result']
                } catch (Exception ex) {
                    logger.error('::: Agreement resolve failed segment={}/{} item={}/{} referenceLength={} error={} :::', segmentIndex + 1, segments.size(), referenceIndex + 1, segment.size(), reference.length(), ex.getMessage(), ex)
                    resolutionCache[reference] = [value: null, status: 'failed', error: ex.getMessage() ?: ex.class.simpleName]
                }
                if (delayMillis > 0 && !(segmentIndex == segments.size() - 1 && referenceIndex == segment.size() - 1)) {
                    Thread.sleep(delayMillis)
                }
            }
            logger.info('::: Agreement resolve segment {}/{} END :::', segmentIndex + 1, segments.size())
        }

        def outputRows = []
        def metrics = [sourceRows: sourceRows.size(), outputRows: 0, permissionRows: 0, eventRows: 0, resolved: 0, invalidFormat: invalidReferences.size(), failed: 0, revoked: 0]
        def processedAt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC).format(Instant.now())
        sourceRows.eachWithIndex { row, rowIndex ->
            def source = rowToMap(headers, row)
            def reference = source.user_reference?.toString() ?: ''
            def resolution = resolutionCache[reference] ?: [value: null, status: 'failed', error: 'Reference was not resolved']
            if (resolution.status == 'resolved') metrics.resolved++
            if (resolution.status != 'resolved' && resolution.status != 'invalid_format') metrics.failed++
            def permissions = parsePermissions(source.data_permissions?.toString(), rowIndex + 2, metrics)
            def consentStatus = source.version_source_type?.toString() == 'CompanyConsentRevocation' ? 'revoked' : 'active'
            if (consentStatus == 'revoked') metrics.revoked++

            if (permissions.isEmpty()) {
                outputRows << outputRow(source, [:], resolution, consentStatus, sourceFileName, processedAt)
                metrics.eventRows++
            } else {
                permissions.each { permission ->
                    outputRows << outputRow(source, permission as Map, resolution, consentStatus, sourceFileName, processedAt)
                    metrics.permissionRows++
                }
            }
        }
        metrics.outputRows = outputRows.size()
        def csv = writeCsv(outputRows)
        logger.info('::: Agreement transform END sourceFile={} metrics={} elapsedMs={} :::', sourceFileName, metrics, java.time.Duration.between(startedAt, Instant.now()).toMillis())
        [bytes: csv.getBytes(StandardCharsets.UTF_8), metrics: metrics]
    }

    private List<Map> parsePermissions(String raw, int sourceLine, Map metrics) {
        if (raw == null || raw.isBlank()) return []
        try {
            def parsed = new JsonSlurper().parseText(raw)
            if (!(parsed instanceof List)) {
                logger.warn('::: Agreement permissions not an array sourceLine={} :::', sourceLine)
                metrics.failed++
                return []
            }
            return parsed.findAll { it instanceof Map }.collect { it as Map }
        } catch (Exception ex) {
            logger.error('::: Agreement permissions JSON parse failed sourceLine={} error={} :::', sourceLine, ex.getMessage(), ex)
            metrics.failed++
            return []
        }
    }

    private Map outputRow(Map source, Map permission, Map resolution, String consentStatus, String sourceFileName, String processedAt) {
        def rawRut = resolution.value?.toString()
        return [
            rut: rawRut,
            rut_normalized: normalizeRut(rawRut),
            user_reference: source.user_reference,
            agreement_id: source.id,
            created_at: source.created_at,
            version: source.version,
            subject_id: source.subject_id,
            subject_type: source.subject_type,
            version_source_type: source.version_source_type,
            version_source_id: source.version_source_id,
            consent_status: consentStatus,
            data_category: permission.data_category,
            data_label: permission.data_label,
            data_use: permission.data_use,
            data_subject: permission.data_subject,
            scope_type: permission.scope_type,
            scope_id: permission.scope_id,
            scope_version: permission.scope_version,
            expires_at: permission.expires_at,
            previous_evidence_ids: source.previous_evidence_ids,
            resolution_status: resolution.status,
            resolution_error: resolution.error,
            data_permissions_raw: permission.isEmpty() ? source.data_permissions : null,
            source_file: sourceFileName,
            processed_at: processedAt
        ]
    }

    private Map rowToMap(List<String> headers, List<String> values) {
        def row = [:]
        headers.eachWithIndex { header, index -> row[header] = index < values.size() ? values[index] : '' }
        row
    }

    private boolean isApiReference(String reference) {
        return reference ==~ /[A-Za-z0-9_-]{39,512}/
    }

    private String normalizeRut(String value) {
        if (value == null || value.isBlank()) return null
        def cleaned = value.trim().toUpperCase()
        if (cleaned ==~ /\d{7,8}-[0-9K]/) return cleaned
        if (cleaned ==~ /\d{7,8}[0-9K]/) return cleaned[0..-2] + '-' + cleaned[-1]
        return null
    }

    private List<List<String>> parseCsv(String text) {
        def rows = []
        def currentRow = []
        def current = new StringBuilder()
        boolean quoted = false
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index)
            if (character == '"') {
                if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    current.append('"')
                    index++
                } else {
                    quoted = !quoted
                }
            } else if (character == ',' && !quoted) {
                currentRow << current.toString()
                current.setLength(0)
            } else if ((character == '\n' || character == '\r') && !quoted) {
                if (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++
                currentRow << current.toString()
                current.setLength(0)
                rows << currentRow
                currentRow = []
            } else {
                current.append(character)
            }
        }
        if (current.length() > 0 || !currentRow.isEmpty()) {
            currentRow << current.toString()
            rows << currentRow
        }
        rows
    }

    private String writeCsv(List<Map> rows) {
        def builder = new StringBuilder()
        builder.append(OUTPUT_COLUMNS.collect { escapeCsv(it) }.join(',')).append('\n')
        rows.each { row ->
            builder.append(OUTPUT_COLUMNS.collect { escapeCsv(row[it]) }.join(',')).append('\n')
        }
        builder.toString()
    }

    private String escapeCsv(def value) {
        if (value == null) return ''
        def text = value instanceof String ? value : groovy.json.JsonOutput.toJson(value)
        return '"' + text.replace('"', '""') + '"'
    }
}
