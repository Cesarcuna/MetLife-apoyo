package groovy.CLIN

import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.eos.EOSUtils
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task
import groovy.json.JsonSlurper
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus

import java.nio.charset.StandardCharsets

class SoyioSyncStatus implements Task {

	Logger logger = LoggerFactory.getLogger(SoyioSyncStatus)

	@Autowired
	EOSUtils eosUtils

	@Override
	Object execute(WorkflowDomain workFlow) {
		def method = workFlow.getRequestMethod()
		def requestURI = workFlow.getRequestURI()
		def response = [:]
		def status = HttpStatus.OK
		def startedAt = System.currentTimeMillis()

		def txnId = workFlow.getRequestHeader().get(Constants.GSSP_TRANX_ID_HEADER)
		if (txnId == null || txnId.isEmpty()) {
			txnId = MDC.get(Constants.TXN_ID)
		}
		logger.info("::: SoyioSyncStatus START method={} uri={} txnId={} :::", method, requestURI, txnId)

		try {
			def connString = requiredProperty(workFlow, "storage.connection-string")
			def controlContainer = requiredProperty(workFlow, Constants.CONF_SOYIO_CONTROL_CONTAINER)
			def controlEntityId = requiredProperty(workFlow, Constants.CONF_SOYIO_CONTROL_ENTITY_ID)
			def controlFileName = optionalProperty(workFlow, Constants.CONF_SOYIO_CONTROL_FILE_NAME, Constants.WATERMARK_FILE_NAME)
			logger.info("::: SoyioSyncStatus Config resolved connStringPresent={} controlContainer={} controlEntityId={} controlFileName={} :::", connString != null && !connString.isBlank(), controlContainer, controlEntityId, controlFileName)

			logger.info("::: SoyioSyncStatus Reading watermark file from blob :::")
			def raw = eosUtils.retrieveFileFromBlobStorageBinary(
				connString,
				[containerName: controlContainer, entityId: controlEntityId],
				controlFileName
			)
			logger.info("::: SoyioSyncStatus Blob response keys={} error={} :::", raw?.keySet(), raw?.error)

			if (raw == null || raw.error == HttpStatus.NOT_FOUND) {
				logger.info("::: SoyioSyncStatus Watermark not found, returning never status :::")
				response = [
					lastSuccessfulSyncAt: null,
					lastAttemptAt: null,
					lastStatus: Constants.SOYIO_STATUS_NEVER,
					lastExportId: null,
					resource: null,
					watermarkSource: Constants.WATERMARK_SOURCE_BLOB,
					metadata: eosUtils.createMetadataObject('Soyio Sync Status', method, requestURI, txnId)
				]
			} else if (raw.error != null) {
				logger.error("::: SoyioSyncStatus Blob returned error={} :::", raw.error)
				throw new IllegalStateException("Unable to read watermark status file")
			} else {
				def text = new String((byte[]) raw.fileBytes, StandardCharsets.UTF_8)
				logger.info("::: SoyioSyncStatus Watermark bytes={} :::", ((byte[]) raw.fileBytes).length)
				def parsed = text.isBlank() ? [:] : (new JsonSlurper().parseText(text) as Map)
				logger.info("::: SoyioSyncStatus Parsed watermark keys={} :::", parsed.keySet())

				response = [
					lastSuccessfulSyncAt: parsed.last_successful_sync_at,
					lastAttemptAt: parsed.last_attempt_at,
					lastStatus: parsed.last_status ?: Constants.SOYIO_STATUS_NEVER,
					lastExportId: parsed.last_export_id,
					resource: parsed.resource,
					watermarkSource: Constants.WATERMARK_SOURCE_BLOB,
					metadata: eosUtils.createMetadataObject('Soyio Sync Status', method, requestURI, txnId)
				]
				logger.info("::: SoyioSyncStatus Returning status={} lastExportId={} :::", response.lastStatus, response.lastExportId)
			}
		} catch (Exception ex) {
			logger.error("::: Soyio sync status failed: {} :::", ex.getMessage(), ex)
			response = eosUtils.createSystemErrResp(requestURI, method, Constants.SERVICE_ID_SOYIO_SYNC_STATUS)
			status = HttpStatus.INTERNAL_SERVER_ERROR
		} finally {
			def elapsedMs = System.currentTimeMillis() - startedAt
			logger.info("::: SoyioSyncStatus END status={} elapsedMs={} txnId={} :::", status, elapsedMs, txnId)
		}

		workFlow.addResponseBody(new EntityResult(response, status == HttpStatus.OK))
		workFlow.addResponseStatus(status)
		workFlow.addResponseHeaders(eosUtils.createResponseHeader(Constants.GSSP_TRANX_ID_HEADER, txnId))
		logger.info("::: SoyioSyncStatus Response attached success={} :::", status == HttpStatus.OK)
		return workFlow
	}

	private String requiredProperty(WorkflowDomain wf, String key) {
		def value = wf.getEnvPropertyFromContext(key)?.toString()?.trim()
		if (value == null || value.isEmpty()) {
			logger.error("::: SoyioSyncStatus Missing configuration key={} :::", key)
			throw new IllegalStateException("Missing required configuration property: ${key}")
		}
		logger.info("::: SoyioSyncStatus Config key {} available :::", key)
		return value
	}

	private String optionalProperty(WorkflowDomain wf, String key, String defaultValue) {
		def value = wf.getEnvPropertyFromContext(key)?.toString()?.trim()
		def resolved = (value == null || value.isEmpty()) ? defaultValue : value
		logger.info("::: SoyioSyncStatus optionalProperty key={} usingDefault={} resolvedValue={} :::", key, value == null || value.isEmpty(), resolved)
		return resolved
	}
}