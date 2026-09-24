package groovy.CLIN

import org.springframework.http.HttpStatus
import org.slf4j.MDC
import com.metlife.eos.EOSUtils
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task
import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import org.springframework.beans.factory.annotation.Autowired

class ListFiles implements Task {

	Logger logger = LoggerFactory.getLogger(ListFiles)

	@Autowired
	EOSUtils eosUtils

	def static final SERVICE_ID = "0902"

	@Override
	public Object execute(WorkflowDomain workFlow) {

		logger.info "::: Storage Service Begin List Files Workflow :::"

		def startTime = System.nanoTime()
		def lfRequestParams = workFlow.getRequestParams()
		def lfTxnId = workFlow.getRequestHeader().get(Constants.GSSP_TRANX_ID_HEADER)
		def method = workFlow.getRequestMethod()
		def requestURI = workFlow.getRequestURI()
		def lfConnString = workFlow.getEnvPropertyFromContext("storage.connection-string")?.toString()?.trim()
		def lfStatusCode
		def lfResponse = [:]

		if(lfTxnId == null || lfTxnId.isEmpty()) {
			lfTxnId = MDC.get(Constants.TXN_ID)
		}

		try{
			logger.info "::: Storage Service checking request parameters :::"

			if(lfRequestParams){

				// Use standard query parameters instead of RSQL 'q'
				// Dynamic Container (domain) e.g., "claims", "policy"
				def lfContainerName = lfRequestParams?.get(Constants.CONTAINER_NAME)?.toString()
				def lfEntityId = lfRequestParams?.get(Constants.ENTITY_ID)?.toString()

				if (lfContainerName != null && lfContainerName != "null" && !lfContainerName.isBlank() &&
						lfEntityId != null && lfEntityId != "null" && !lfEntityId.isBlank()) {

					if (!eosUtils.validateInput(lfContainerName) || !eosUtils.validateInput(lfEntityId)) {
						logger.error("::: Invalid Characters in Input - Potential Path Traversal or Injection :::")
						lfStatusCode = HttpStatus.BAD_REQUEST
					} else {
						def params = [
								'containerName': lfContainerName,
								'entityId': lfEntityId
						]

						// Call EOSUtils to list blobs
						logger.info "::: Calling listAssetsFromBlobStorage with container: ${lfContainerName} and entityId: ${lfEntityId} :::"
						def listResponse = eosUtils.listAssetsFromBlobStorage(lfConnString, params)

						if(listResponse.containsKey('items')) {
							lfResponse << ['items' : listResponse.items]
							lfResponse << ['metadata' : eosUtils.createMetadataObject('List Files', method, requestURI, lfTxnId)]
							lfStatusCode = HttpStatus.OK
						} else if (listResponse.containsKey('error')) {
							lfStatusCode = listResponse.error
							if (lfStatusCode == HttpStatus.NOT_FOUND) {
								lfResponse = eosUtils.createNotAcceptableResp(requestURI, method, SERVICE_ID)
							} else {
								lfResponse = eosUtils.createSystemErrResp(requestURI, method, SERVICE_ID)
							}
						}
					}
				} else {
					logger.error "::: Invalid Request Parameters - Missing containerName or entityId :::"
					lfResponse = eosUtils.createBadReqErrResp(requestURI, method, SERVICE_ID)
					lfStatusCode = HttpStatus.BAD_REQUEST
				}

			} else {
				logger.error "::: Invalid Request Parameters :::"
				lfResponse = eosUtils.createBadReqErrResp(requestURI, method, SERVICE_ID)
				lfStatusCode = HttpStatus.BAD_REQUEST
			}
		} catch (any) {
			logger.error "::: Error in ListFiles Workflow: {} :::", any
			lfResponse = eosUtils.createSystemErrResp(requestURI, method, SERVICE_ID)
			lfStatusCode = HttpStatus.INTERNAL_SERVER_ERROR
		}

		def endTime = System.nanoTime()
		logger.info "::: Storage Service list overall transaction time ::: " + (endTime - startTime)

		if(lfStatusCode == HttpStatus.OK) {
			workFlow.addResponseBody(new EntityResult(lfResponse, true))
		} else {
			workFlow.addResponseBody(new EntityResult(lfResponse, false))
		}

		workFlow.addResponseStatus(lfStatusCode)
		workFlow.addResponseHeaders(eosUtils.createResponseHeader(Constants.GSSP_TRANX_ID_HEADER,lfTxnId))

		logger.info "::: Storage Service End List Files WorkFlow ::: "

		return workFlow
	}
}
