package groovy.CLIN

import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.eos.EOSUtils
import com.metlife.eos.SoyioUtils
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus

class SoyioListApiKeys implements Task {

	Logger logger = LoggerFactory.getLogger(SoyioListApiKeys)

	@Autowired
	EOSUtils eosUtils

	@Autowired
	SoyioUtils soyioUtils

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
		logger.info("::: SoyioListApiKeys START method={} uri={} txnId={} :::", method, requestURI, txnId)

		try {
			def baseUrl = requiredProperty(workFlow, Constants.CONF_SOYIO_BASE_URL)
			def apiKey = requiredProperty(workFlow, Constants.CONF_SOYIO_API_KEY)
			logger.info("::: SoyioListApiKeys Config resolved baseUrl={} apiKeyPresent={} apiKeyLength={} :::", baseUrl, apiKey != null && !apiKey.isBlank(), apiKey?.length())

			logger.info("::: SoyioListApiKeys Calling Soyio /api_keys :::")
			def result = soyioUtils.listApiKeys(baseUrl, apiKey)
			def upstreamStatus = result.statusCode as int
			status = resolveHttpStatus(upstreamStatus)
			logger.info("::: SoyioListApiKeys Upstream response status={} mappedStatus={} :::", upstreamStatus, status)

			if (upstreamStatus >= 200 && upstreamStatus < 300) {
				def count = (result.response?.api_keys instanceof List) ? result.response.api_keys.size() : 0
				logger.info("::: SoyioListApiKeys Success apiKeysCount={} :::", count)
				response = [
					api_keys: result.response?.api_keys ?: [],
					metadata: eosUtils.createMetadataObject('Soyio List API Keys', method, requestURI, txnId)
				]
			} else {
				logger.error("::: SoyioListApiKeys Failed upstreamStatus={} upstreamBody={} :::", upstreamStatus, result.body)
				response = [
					errorCode: String.valueOf(upstreamStatus),
					statusCode: status,
					errorDescription: "Soyio list API keys failed",
					upstreamBody: result.body,
					metadata: eosUtils.createMetadataObject('Soyio List API Keys', method, requestURI, txnId)
				]
			}
		} catch (Exception ex) {
			logger.error("::: Soyio list API keys execution failed: {} :::", ex.getMessage(), ex)
			response = eosUtils.createSystemErrResp(requestURI, method, Constants.SERVICE_ID_SOYIO_LIST_API_KEYS)
			status = HttpStatus.INTERNAL_SERVER_ERROR
		} finally {
			def elapsedMs = System.currentTimeMillis() - startedAt
			logger.info("::: SoyioListApiKeys END status={} elapsedMs={} txnId={} :::", status, elapsedMs, txnId)
		}

		workFlow.addResponseBody(new EntityResult(response, status == HttpStatus.OK))
		workFlow.addResponseStatus(status)
		workFlow.addResponseHeaders(eosUtils.createResponseHeader(Constants.GSSP_TRANX_ID_HEADER, txnId))
		logger.info("::: SoyioListApiKeys Response attached success={} :::", status == HttpStatus.OK)
		return workFlow
	}

	private String requiredProperty(WorkflowDomain wf, String key) {
		def value = wf.getEnvPropertyFromContext(key)?.toString()?.trim()
		if (value == null || value.isEmpty()) {
			logger.error("::: SoyioListApiKeys Missing configuration key={} :::", key)
			throw new IllegalStateException("Missing required configuration property: ${key}")
		}
		logger.info("::: SoyioListApiKeys Config key {} available :::", key)
		return value
	}

	private HttpStatus resolveHttpStatus(int code) {
		def status = HttpStatus.resolve(code)
		return status ?: HttpStatus.INTERNAL_SERVER_ERROR
	}
}
