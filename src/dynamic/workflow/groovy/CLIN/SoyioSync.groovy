package groovy.CLIN

import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.eos.EOSUtils
import com.metlife.eos.SoyioUtils
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class SoyioSync implements Task {

	Logger logger = LoggerFactory.getLogger(SoyioSync)

	@Autowired
	EOSUtils eosUtils

	@Autowired
	SoyioUtils soyioUtils

	@Autowired
	SoyioAgreementTransformer agreementTransformer

	@Override
	Object execute(WorkflowDomain workFlow) {
		def method = workFlow.getRequestMethod()
		def requestURI = workFlow.getRequestURI()
		def requestBody = workFlow.getRequestBody() ?: [:]
		def response = [:]
		def status = HttpStatus.OK

		def txnId = workFlow.getRequestHeader().get(Constants.GSSP_TRANX_ID_HEADER)
		if (txnId == null || txnId.isEmpty()) {
			txnId = MDC.get(Constants.TXN_ID)
		}

		def startedAt = Instant.now()
		def runId = "run_${DateTimeFormatter.ofPattern(Constants.STORAGE_TIMESTAMP_PATTERN).withZone(ZoneOffset.UTC).format(startedAt)}"
		logger.info("::: SoyioSync START runId={} method={} uri={} :::", runId, method, requestURI)
		logger.info("::: SoyioSync Request body keys={} :::", requestBody.keySet())

		try {
			def connString = requiredProperty(workFlow, "storage.connection-string")
			logger.info("::: SoyioSync storage.connection-string resolved present={} :::", connString != null && !connString.isBlank())

			def baseUrl = requiredProperty(workFlow, Constants.CONF_SOYIO_BASE_URL)
			def apiKey = requiredProperty(workFlow, Constants.CONF_SOYIO_API_KEY)
			def defaultResource = optionalProperty(workFlow, Constants.CONF_SOYIO_DEFAULT_RESOURCE, Constants.DEFAULT_SOYIO_RESOURCE)
			def defaultOutputFormat = optionalProperty(workFlow, Constants.CONF_SOYIO_DEFAULT_OUTPUT_FORMAT, Constants.DEFAULT_SOYIO_OUTPUT_FORMAT)
			def defaultOverlapMinutes = optionalIntProperty(workFlow, Constants.CONF_SOYIO_DEFAULT_OVERLAP_MINUTES, 10)
			def initialLookbackHours = optionalIntProperty(workFlow, Constants.CONF_SOYIO_INITIAL_LOOKBACK_HOURS, 6)
			def pollIntervalSeconds = optionalIntProperty(workFlow, Constants.CONF_SOYIO_POLL_INTERVAL_SECONDS, 20)
			def pollTimeoutSeconds = optionalIntProperty(workFlow, Constants.CONF_SOYIO_POLL_TIMEOUT_SECONDS, 900)
			def maxDownloadBytes = optionalLongProperty(workFlow, Constants.CONF_SOYIO_MAX_DOWNLOAD_BYTES, 104857600L)
			def exportOrderBy = optionalProperty(workFlow, Constants.CONF_SOYIO_EXPORT_ORDER_BY, "created_at DESC")
			def normalizedContainer = requiredProperty(workFlow, Constants.CONF_SOYIO_NORMALIZED_CONTAINER)
			def normalizedEntityId = requiredProperty(workFlow, Constants.CONF_SOYIO_NORMALIZED_ENTITY_ID)
			def userReferenceBaseUrl = requiredProperty(workFlow, Constants.CONF_SOYIO_USER_REFERENCE_BASE_URL)
			def userReferenceResolvePath = optionalProperty(workFlow, Constants.CONF_SOYIO_USER_REFERENCE_RESOLVE_PATH, "/v1/user-references/resolve")
			def userReferenceTenantId = requiredProperty(workFlow, Constants.CONF_SOYIO_USER_REFERENCE_TENANT_ID)
			def userReferenceEnvironment = requiredProperty(workFlow, Constants.CONF_SOYIO_USER_REFERENCE_ENVIRONMENT)
			def userReferenceClientId = requiredProperty(workFlow, Constants.CONF_SOYIO_USER_REFERENCE_CLIENT_ID)
			def userReferenceScope = requiredProperty(workFlow, Constants.CONF_SOYIO_USER_REFERENCE_SCOPE)
			def userReferenceAssertionUrl = requiredProperty(workFlow, Constants.CONF_SOYIO_USER_REFERENCE_ASSERTION_URL)
			def userReferenceApiKey = requiredProperty(workFlow, Constants.CONF_SOYIO_USER_REFERENCE_API_KEY)
			def userReferenceSubscriptionKey = requiredProperty(workFlow, Constants.CONF_SOYIO_USER_REFERENCE_SUBSCRIPTION_KEY)
			def userReferenceBatchSize = optionalIntProperty(workFlow, Constants.CONF_SOYIO_USER_REFERENCE_BATCH_SIZE, 50)
			def userReferenceDelayMillis = optionalIntProperty(workFlow, Constants.CONF_SOYIO_USER_REFERENCE_DELAY_MILLIS, 100)
			def userReferenceMaxRetries = optionalIntProperty(workFlow, Constants.CONF_SOYIO_USER_REFERENCE_MAX_RETRIES, 2)

			def targetContainer = requiredProperty(workFlow, Constants.CONF_SOYIO_TARGET_CONTAINER)
			def targetEntityId = requiredProperty(workFlow, Constants.CONF_SOYIO_TARGET_ENTITY_ID)
			def controlContainer = requiredProperty(workFlow, Constants.CONF_SOYIO_CONTROL_CONTAINER)
			def controlEntityId = requiredProperty(workFlow, Constants.CONF_SOYIO_CONTROL_ENTITY_ID)
			def controlFileName = optionalProperty(workFlow, Constants.CONF_SOYIO_CONTROL_FILE_NAME, Constants.WATERMARK_FILE_NAME)
			logger.info("::: SoyioSync Config summary baseUrl={} apiKeyPresent={} apiKeyLength={} defaultResource={} defaultOutputFormat={} overlapDefault={} lookbackHours={} pollInterval={} pollTimeout={} maxDownloadBytes={} exportOrderBy={} targetContainer={} targetEntityId={} controlContainer={} controlEntityId={} controlFileName={} :::",
				baseUrl,
				apiKey != null && !apiKey.isBlank(),
				apiKey?.length(),
				defaultResource,
				defaultOutputFormat,
				defaultOverlapMinutes,
				initialLookbackHours,
				pollIntervalSeconds,
				pollTimeoutSeconds,
				maxDownloadBytes,
				exportOrderBy,
				targetContainer,
				targetEntityId,
				controlContainer,
				controlEntityId,
				controlFileName)

			def resource = (requestBody.resource ?: defaultResource)?.toString()
			def outputFormat = (requestBody.outputFormat ?: defaultOutputFormat)?.toString()?.toLowerCase()
			def overlapMinutes = parseIntOrDefault(requestBody.overlapMinutes, defaultOverlapMinutes)
			logger.info("::: SoyioSync Effective input resource={} outputFormat={} overlapMinutes={} :::", resource, outputFormat, overlapMinutes)

			if (outputFormat != "csv") {
				throw new IllegalArgumentException("outputFormat must be csv for agreement normalization")
			}
			if (userReferenceBatchSize <= 0 || userReferenceDelayMillis < 0 || userReferenceMaxRetries < 0) {
				throw new IllegalArgumentException("user-reference batch and retry settings are invalid")
			}

			def controlParams = [containerName: controlContainer, entityId: controlEntityId]
			logger.info("::: SoyioSync Reading control state from container={} entityId={} file={} :::", controlContainer, controlEntityId, controlFileName)
			def controlState = readControlState(connString, controlParams, controlFileName)
			logger.info("::: SoyioSync Control state loaded keys={} lastSuccessfulSyncAt={} :::", controlState.keySet(), controlState.last_successful_sync_at)

			def now = Instant.now()
			def toDateTime = parseInstantOrDefault(requestBody.toDateTime, now)
			def fromDateTime = requestBody.fromDateTime
				? parseInstant(requestBody.fromDateTime?.toString())
				: defaultFromDate(controlState.last_successful_sync_at?.toString(), overlapMinutes, initialLookbackHours, now)
			logger.info("::: SoyioSync Window computed fromDateTime={} toDateTime={} :::", formatInstantUtc(fromDateTime), formatInstantUtc(toDateTime))

			if (!fromDateTime.isBefore(toDateTime)) {
				logger.warn("::: SoyioSync No-op window fromDateTime={} toDateTime={} :::", formatInstantUtc(fromDateTime), formatInstantUtc(toDateTime))
				def finishedAtNoOp = Instant.now()
				response = [
					runId: runId,
					status: Constants.SOYIO_STATUS_COMPLETED,
					resource: resource,
					exportId: null,
					recordsCount: 0,
					fromDateTime: formatInstantUtc(fromDateTime),
					toDateTime: formatInstantUtc(toDateTime),
					segmentsPlanned: 0,
					segmentsProcessed: 0,
					startedAt: formatInstantUtc(startedAt),
					finishedAt: formatInstantUtc(finishedAtNoOp),
					message: "No sync needed because fromDateTime is not before toDateTime",
					metadata: eosUtils.createMetadataObject('Soyio Sync', method, requestURI, txnId)
				]
			} else {
				def whereJson = JsonOutput.toJson([created_at: [
					">": formatInstantUtc(fromDateTime),
					"<=": formatInstantUtc(toDateTime)
				]])
				def createResult = soyioUtils.createExport(baseUrl, apiKey, resource, outputFormat, whereJson, exportOrderBy)
				if (createResult.statusCode < 200 || createResult.statusCode >= 300) {
					throw new IllegalStateException("Soyio create export failed with status ${createResult.statusCode}: ${createResult.body}")
				}

				def exportNode = (createResult.response?.export ?: [:]) as Map
				def exportId = exportNode.id?.toString()
				if (exportId == null || exportId.isBlank()) {
					throw new IllegalStateException("Soyio create export response did not include export.id")
				}

				def completedExport = waitForExportCompletion(baseUrl, apiKey, exportId, pollIntervalSeconds, pollTimeoutSeconds).export as Map
				def downloadUrl = completedExport.download_url?.toString()
				if (downloadUrl == null || downloadUrl.isBlank()) {
					throw new IllegalStateException("Export completed but download_url is missing for export ${exportId}")
				}

				def bytes = soyioUtils.downloadSignedFile(downloadUrl)
				if (bytes == null || bytes.length == 0) {
					throw new IllegalStateException("Downloaded export file is empty")
				}
				if (bytes.length > maxDownloadBytes) {
					throw new IllegalStateException("Downloaded export size ${bytes.length} exceeds configured limit ${maxDownloadBytes}")
				}

				def fileName = "soyio_${toDateTime.toString().replaceAll('[^0-9]', '').substring(0, 14)}.${outputFormat}"
				def datePath = DateTimeFormatter.ofPattern(Constants.YYYY_MM_DD_PATTERN).withZone(ZoneOffset.UTC).format(toDateTime)
				def finalEntity = "${targetEntityId}/${resource}/${datePath}"
				def uploadStatus = eosUtils.sendFileToBlobStorageBinary(connString, [containerName: targetContainer, entityId: finalEntity], bytes, fileName, eosUtils.createMetadataObject('Soyio Sync Upload', method, requestURI, txnId))
				if (uploadStatus < 200 || uploadStatus >= 300) {
					throw new IllegalStateException("Failed to upload export to blob, status ${uploadStatus}")
				}
				logger.info("::: SoyioSync Raw CSV uploaded container={} entity={} fileName={} bytes={} :::", targetContainer, finalEntity, fileName, bytes.length)

				def accessToken = null
				def authAttempted = false
				def authFailure = null
				def tokenRefreshAttempted = false

				def transformed = agreementTransformer.transform(bytes, fileName, { String reference ->
					if (!authAttempted) {
						authAttempted = true
						def tokenResult = soyioUtils.getUserReferenceAccessToken(userReferenceTenantId, userReferenceClientId, userReferenceScope, userReferenceAssertionUrl, userReferenceEnvironment)
						if (tokenResult.statusCode < 200 || tokenResult.statusCode >= 300) {
							authFailure = "Authentication failed at ${tokenResult.stage} with status ${tokenResult.statusCode}: ${truncate(tokenResult.body?.toString())}"
							logger.error("::: SoyioSync User-reference authentication failed stage={} status={} :::", tokenResult.stage, tokenResult.statusCode)
						} else {
							accessToken = tokenResult.accessToken?.toString()
							logger.info("::: SoyioSync User-reference authentication completed tokenPresent={} expiresIn={} :::", accessToken != null && !accessToken.isBlank(), tokenResult.expiresIn)
						}
					}
					if (authFailure != null) {
						return [value: null, status: 'auth_failed', error: authFailure]
					}
					def resolution = resolveReferenceWithRetry(reference, userReferenceBaseUrl, userReferenceResolvePath, accessToken, userReferenceApiKey, userReferenceSubscriptionKey, userReferenceMaxRetries)
					if (resolution.status == 'api_401' && !tokenRefreshAttempted) {
						tokenRefreshAttempted = true
						def refreshResult = soyioUtils.getUserReferenceAccessToken(userReferenceTenantId, userReferenceClientId, userReferenceScope, userReferenceAssertionUrl, userReferenceEnvironment)
						if (refreshResult.statusCode >= 200 && refreshResult.statusCode < 300) {
							accessToken = refreshResult.accessToken?.toString()
							logger.warn("::: SoyioSync User-reference token refreshed after 401 expiresIn={} :::", refreshResult.expiresIn)
							resolution = resolveReferenceWithRetry(reference, userReferenceBaseUrl, userReferenceResolvePath, accessToken, userReferenceApiKey, userReferenceSubscriptionKey, userReferenceMaxRetries)
						} else {
							logger.error("::: SoyioSync User-reference token refresh failed stage={} status={} :::", refreshResult.stage, refreshResult.statusCode)
						}
					}
					resolution
				}, userReferenceBatchSize, userReferenceDelayMillis)
				def normalizedFileName = "agreements_${toDateTime.toString().replaceAll('[^0-9]', '').substring(0, 14)}.csv"
				def normalizedUploadStatus = eosUtils.sendFileToBlobStorageBinary(
					connString,
					[containerName: normalizedContainer, entityId: normalizedEntityId],
					transformed.bytes,
					normalizedFileName,
					eosUtils.createMetadataObject('Soyio Agreement Normalization', method, requestURI, txnId)
				)
				if (normalizedUploadStatus < 200 || normalizedUploadStatus >= 300) {
					throw new IllegalStateException("Failed to upload normalized agreements to DDZ, status ${normalizedUploadStatus}")
				}
				logger.info("::: SoyioSync Normalized CSV uploaded container={} entity={} fileName={} bytes={} metrics={} :::", normalizedContainer, normalizedEntityId, normalizedFileName, transformed.bytes.length, transformed.metrics)
				def processingStatus = (transformed.metrics.invalidFormat > 0 || transformed.metrics.failed > 0)
					? Constants.SOYIO_STATUS_COMPLETED_WITH_ERRORS
					: Constants.SOYIO_STATUS_COMPLETED

				writeControlState(connString, [containerName: normalizedContainer, entityId: normalizedEntityId], Constants.SUCCESS_FILE_NAME, [status: processingStatus, runId: runId, timestamp: formatInstantUtc(toDateTime), metrics: transformed.metrics], method, requestURI, txnId)

				def successState = [status: Constants.SOYIO_STATUS_COMPLETED, runId: runId, timestamp: formatInstantUtc(toDateTime)]
				writeControlState(connString, [containerName: targetContainer, entityId: targetEntityId], Constants.SUCCESS_FILE_NAME, successState, method, requestURI, txnId)

				def completedAt = Instant.now()
				def watermarkState = [
					last_successful_sync_at: formatInstantUtc(toDateTime),
					last_attempt_at: formatInstantUtc(completedAt),
					last_status: processingStatus,
					last_export_id: exportId,
					resource: resource,
					last_run_id: runId,
					updated_at: formatInstantUtc(completedAt)
				]
				writeControlState(connString, controlParams, controlFileName, watermarkState, method, requestURI, txnId)

				def historyState = [runId: runId, exportId: exportId, resource: resource, startDate: formatInstantUtc(fromDateTime), endDate: formatInstantUtc(toDateTime), status: processingStatus, fileName: fileName, normalizedFileName: normalizedFileName, normalizedMetrics: transformed.metrics, errorMessage: null]
				writeControlState(connString, [containerName: controlContainer, entityId: "${controlEntityId}/history"], "${runId}.json", historyState, method, requestURI, txnId)

				def finishedAt = Instant.now()
				response = [
					runId: runId,
					status: processingStatus,
					resource: resource,
					exportId: exportId,
					recordsCount: safeLong(completedExport.records_count),
					fromDateTime: formatInstantUtc(fromDateTime),
					toDateTime: formatInstantUtc(toDateTime),
					segmentsPlanned: 1,
					segmentsProcessed: 1,
					fileName: fileName,
					normalizedFileName: normalizedFileName,
					normalizedMetrics: transformed.metrics,
					startedAt: formatInstantUtc(startedAt),
					finishedAt: formatInstantUtc(finishedAt),
					message: "Soyio export synced successfully",
					metadata: eosUtils.createMetadataObject('Soyio Sync', method, requestURI, txnId)
				]
			}
		} catch (IllegalArgumentException badInputEx) {
			logger.error("::: Soyio sync bad input: {} :::", badInputEx.getMessage())
			response = eosUtils.createBadReqErrResp(requestURI, method, Constants.SERVICE_ID_SOYIO_SYNC)
			status = HttpStatus.BAD_REQUEST
		} catch (Exception ex) {
			logger.error("::: Soyio sync execution failed: {} :::", ex.getMessage(), ex)
			response = eosUtils.createSystemErrResp(requestURI, method, Constants.SERVICE_ID_SOYIO_SYNC)
			status = HttpStatus.INTERNAL_SERVER_ERROR
		}

		def elapsedMs = Duration.between(startedAt, Instant.now()).toMillis()
		logger.info("::: SoyioSync END runId={} status={} elapsedMs={} :::", runId, status, elapsedMs)

		workFlow.addResponseBody(new EntityResult(response, status == HttpStatus.OK))
		workFlow.addResponseStatus(status)
		workFlow.addResponseHeaders(eosUtils.createResponseHeader(Constants.GSSP_TRANX_ID_HEADER, txnId))
		logger.info("::: SoyioSync Response attached success={} runId={} :::", status == HttpStatus.OK, runId)
		return workFlow
	}

	private Map resolveReferenceWithRetry(String reference, String baseUrl, String resolvePath, String accessToken, String apiKey, String subscriptionKey, int maxRetries) {
		def attempts = 0
		while (attempts <= maxRetries) {
			attempts++
			def result = soyioUtils.resolveUserReference(baseUrl, resolvePath, accessToken, apiKey, subscriptionKey, reference)
			def statusCode = result.statusCode as int
			logger.info("::: User-reference resolve attempt={} status={} referenceLength={} :::", attempts, statusCode, reference.length())
			if (statusCode >= 200 && statusCode < 300) {
				def value = result.response?.data?.toString()
				if (value == null || value.isBlank()) {
					return [value: null, status: 'failed', error: 'API returned 2xx without data']
				}
				return [value: value, status: 'resolved', error: null]
			}

			def error = "HTTP ${statusCode}: ${truncate(result.body?.toString())}"
			if (statusCode == 429 || statusCode >= 500) {
				if (attempts <= maxRetries) {
					def backoffMillis = Math.min(10000L, 500L * (1L << Math.min(attempts - 1, 4)))
					logger.warn("::: User-reference transient failure status={} retryInMs={} attempt={} maxRetries={} :::", statusCode, backoffMillis, attempts, maxRetries)
					Thread.sleep(backoffMillis)
					continue
				}
			}
			return [value: null, status: "api_${statusCode}", error: error]
		}
		return [value: null, status: 'failed', error: 'Resolver retry loop exhausted']
	}

	private String truncate(String value) {
		if (value == null) return ''
		return value.length() > 500 ? value.substring(0, 500) : value
	}

	private Map waitForExportCompletion(String baseUrl, String apiKey, String exportId, int pollIntervalSeconds, int pollTimeoutSeconds) {
		def start = Instant.now()
		def attempts = 0
		logger.info("::: SoyioSync waitForExportCompletion START exportId={} :::", exportId)
		while (Duration.between(start, Instant.now()).seconds <= pollTimeoutSeconds) {
			attempts++
			def result = soyioUtils.getExportStatus(baseUrl, apiKey, exportId)
			logger.info("::: SoyioSync poll attempt={} exportId={} statusCode={} :::", attempts, exportId, result.statusCode)
			if (result.statusCode < 200 || result.statusCode >= 300) {
				throw new IllegalStateException("Soyio export status failed with status ${result.statusCode}: ${result.body}")
			}

			def exportNode = (result.response?.export ?: [:]) as Map
			def status = exportNode.status?.toString()?.toLowerCase()
			logger.info("::: SoyioSync poll exportId={} status={} elapsedSeconds={} :::", exportId, status, Duration.between(start, Instant.now()).seconds)
			if (status == Constants.SOYIO_STATUS_COMPLETED) {
				logger.info("::: SoyioSync waitForExportCompletion COMPLETE exportId={} attempts={} :::", exportId, attempts)
				return result.response as Map
			}
			if (status == Constants.SOYIO_STATUS_FAILED) {
				def errMessage = exportNode.error_message ?: "unknown"
				logger.error("::: SoyioSync waitForExportCompletion FAILED exportId={} attempts={} errorMessage={} :::", exportId, attempts, errMessage)
				throw new IllegalStateException("Soyio export failed: ${errMessage}")
			}

			Thread.sleep(Math.max(pollIntervalSeconds, 5) * 1000L)
		}

		logger.error("::: SoyioSync waitForExportCompletion TIMEOUT exportId={} timeoutSeconds={} :::", exportId, pollTimeoutSeconds)
		throw new IllegalStateException("Timeout waiting for Soyio export completion")
	}

	private Map readControlState(String connString, Map params, String fileName) {
		logger.info("::: SoyioSync readControlState START container={} entityId={} fileName={} :::", params.containerName, params.entityId, fileName)
		def raw = eosUtils.retrieveFileFromBlobStorageBinary(connString, params, fileName)
		if (raw == null || raw.error == HttpStatus.NOT_FOUND) {
			logger.info("::: SoyioSync readControlState NOT_FOUND fileName={} :::", fileName)
			return [:]
		}
		if (raw.error != null) {
			logger.error("::: SoyioSync readControlState ERROR status={} fileName={} :::", raw.error, fileName)
			throw new IllegalStateException("Unable to read control state from blob")
		}
		def text = new String((byte[]) raw.fileBytes, StandardCharsets.UTF_8)
		if (text.isBlank()) {
			logger.info("::: SoyioSync readControlState EMPTY fileName={} :::", fileName)
			return [:]
		}
		def parsed = new JsonSlurper().parseText(text)
		logger.info("::: SoyioSync readControlState SUCCESS fileName={} parsedType={} :::", fileName, parsed?.getClass()?.getSimpleName())
		return parsed instanceof Map ? (parsed as Map) : [:]
	}

	private void writeControlState(String connString, Map params, String fileName, Map state, String method, String requestURI, String txnId) {
		logger.info("::: SoyioSync writeControlState START container={} entityId={} fileName={} stateKeys={} :::", params.containerName, params.entityId, fileName, state.keySet())
		def payloadBytes = JsonOutput.prettyPrint(JsonOutput.toJson(state)).getBytes(StandardCharsets.UTF_8)
		def status = eosUtils.sendFileToBlobStorageBinary(
			connString,
			params,
			payloadBytes,
			fileName,
			eosUtils.createMetadataObject('Soyio Sync Control State', method, requestURI, txnId)
		)
		logger.info("::: SoyioSync writeControlState uploadStatus={} payloadBytes={} :::", status, payloadBytes.length)

		if (status < 200 || status >= 300) {
			throw new IllegalStateException("Failed to write control state to blob, status ${status}")
		}
		logger.info("::: SoyioSync writeControlState SUCCESS fileName={} :::", fileName)
	}

	private String requiredProperty(WorkflowDomain wf, String key) {
		def value = wf.getEnvPropertyFromContext(key)?.toString()?.trim()
		if (value == null || value.isEmpty()) {
			logger.error("::: SoyioSync requiredProperty MISSING key={} :::", key)
			throw new IllegalStateException("Missing required configuration property: ${key}")
		}
		logger.info("::: SoyioSync requiredProperty FOUND key={} :::", key)
		return value
	}

	private String optionalProperty(WorkflowDomain wf, String key, String defaultValue) {
		def value = wf.getEnvPropertyFromContext(key)?.toString()?.trim()
		def resolved = (value == null || value.isEmpty()) ? defaultValue : value
		logger.info("::: SoyioSync optionalProperty key={} usingDefault={} resolvedValue={} :::", key, value == null || value.isEmpty(), resolved)
		return resolved
	}

	private int optionalIntProperty(WorkflowDomain wf, String key, int defaultValue) {
		def value = wf.getEnvPropertyFromContext(key)?.toString()?.trim()
		if (value == null || value.isEmpty()) {
			logger.info("::: SoyioSync optionalIntProperty key={} usingDefault={} :::", key, defaultValue)
			return defaultValue
		}
		try {
			def parsed = Integer.parseInt(value)
			logger.info("::: SoyioSync optionalIntProperty key={} parsedValue={} :::", key, parsed)
			return parsed
		} catch (Exception ex) {
			logger.warn("::: SoyioSync optionalIntProperty key={} invalidValue={} usingDefault={} :::", key, value, defaultValue)
			return defaultValue
		}
	}

	private long optionalLongProperty(WorkflowDomain wf, String key, long defaultValue) {
		def value = wf.getEnvPropertyFromContext(key)?.toString()?.trim()
		if (value == null || value.isEmpty()) {
			logger.info("::: SoyioSync optionalLongProperty key={} usingDefault={} :::", key, defaultValue)
			return defaultValue
		}
		try {
			def parsed = Long.parseLong(value)
			logger.info("::: SoyioSync optionalLongProperty key={} parsedValue={} :::", key, parsed)
			return parsed
		} catch (Exception ex) {
			logger.warn("::: SoyioSync optionalLongProperty key={} invalidValue={} usingDefault={} :::", key, value, defaultValue)
			return defaultValue
		}
	}

	private int parseIntOrDefault(def value, int defaultValue) {
		if (value == null) {
			logger.info("::: SoyioSync parseIntOrDefault value=null usingDefault={} :::", defaultValue)
			return defaultValue
		}
		try {
			def parsed = Integer.parseInt(value.toString())
			logger.info("::: SoyioSync parseIntOrDefault parsedValue={} :::", parsed)
			return parsed
		} catch (Exception ex) {
			logger.warn("::: SoyioSync parseIntOrDefault invalidValue={} usingDefault={} :::", value, defaultValue)
			return defaultValue
		}
	}

	private Instant defaultFromDate(String watermark, int overlapMinutes, int initialLookbackHours, Instant now) {
		if (watermark != null && !watermark.isBlank()) {
			def base = parseInstant(watermark)
			def computed = base.minus(Duration.ofMinutes(Math.max(overlapMinutes, 0)))
			logger.info("::: SoyioSync defaultFromDate using watermark={} overlapMinutes={} computed={} :::", watermark, overlapMinutes, formatInstantUtc(computed))
			return computed
		}
		def computed = now.minus(Duration.ofHours(Math.max(initialLookbackHours, 1)))
		logger.info("::: SoyioSync defaultFromDate using initialLookbackHours={} computed={} :::", initialLookbackHours, formatInstantUtc(computed))
		return computed
	}

	private Instant parseInstantOrDefault(def value, Instant defaultValue) {
		if (value == null || value.toString().isBlank()) {
			logger.info("::: SoyioSync parseInstantOrDefault using default={} :::", formatInstantUtc(defaultValue))
			return defaultValue
		}
		def parsed = parseInstant(value.toString())
		logger.info("::: SoyioSync parseInstantOrDefault parsed={} :::", formatInstantUtc(parsed))
		return parsed
	}

	private Instant parseInstant(String value) {
		logger.info("::: SoyioSync parseInstant value={} :::", value)
		return Instant.parse(value)
	}

	private long safeLong(def value) {
		if (value == null) {
			return 0L
		}
		try {
			return Long.parseLong(value.toString())
		} catch (Exception ex) {
			logger.warn("::: SoyioSync safeLong invalidValue={} using 0 :::", value)
			return 0L
		}
	}

	private String formatInstantUtc(Instant instant) {
		return DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN).withZone(ZoneOffset.UTC).format(instant)
	}
}