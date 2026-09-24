package groovy.CLIN

import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.eos.EOSUtils
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task

class SendFile implements Task {
	
	Logger logger = LoggerFactory.getLogger(SendFile)
	
	@Autowired
	EOSUtils eosUtils

	
	def static final SERVICE_ID = "0901"
	
	@Override
    Object execute(WorkflowDomain workFlow) {

		logger.info "::: Storage Service Begin Send File Workflow :::"
		
		def startTime = System.nanoTime()
		def sfRequestBody = workFlow.getRequestBody()
		def sfTxnId = workFlow.getRequestHeader().get(Constants.GSSP_TRANX_ID_HEADER)
		def method = workFlow.getRequestMethod()
		def requestURI = workFlow.getRequestURI()
		def sfConnString = workFlow.getEnvPropertyFromContext("storage.connection-string")?.toString()?.trim()
		def sfStatusCode
		def sfResponse = [:]
		
		if(sfTxnId == null || sfTxnId.isEmpty()) {
			sfTxnId = MDC.get(Constants.TXN_ID)
		}
				
		try{
			
			logger.info "::: Storage Service checking request parameters :::"
			
			if(sfRequestBody?.containsKey(Constants.ENTITY_ID) && sfRequestBody?.containsKey(Constants.FILE_NAME)
				&& sfRequestBody?.containsKey(Constants.BASE64) && sfRequestBody?.containsKey(Constants.CONTAINER_NAME)) {
				
				// Dynamic Container (domain) e.g., "claims", "policy"
				def sfEntityId = sfRequestBody?.getAt(Constants.ENTITY_ID)?.toString()
				def sfFileName = sfRequestBody?.getAt(Constants.FILE_NAME)?.toString()
				def sfBase64 = sfRequestBody?.getAt(Constants.BASE64)?.toString()
				def sfContainerName = sfRequestBody?.getAt(Constants.CONTAINER_NAME)?.toString()
				
				if(sfEntityId != null && !sfEntityId.isEmpty() && sfFileName != null && !sfFileName.isEmpty()
					&& sfBase64 != null && !sfBase64.isEmpty() && sfContainerName != null && !sfContainerName.isEmpty()) {

                    // Security / Robustness Check
                    if (!eosUtils.validateInput(sfContainerName) || !eosUtils.validateInput(sfEntityId)) {
                        logger.error("::: Invalid Characters in Input - Potential Path Traversal or Injection :::")
                        sfStatusCode = HttpStatus.BAD_REQUEST
                    } else {
                        def metadata = eosUtils.createMetadataObject('Send File', method, requestURI, sfTxnId)
                        
                        def params = [
                            'containerName': sfContainerName,
                            'entityId': sfEntityId
                        ]
                        
                        sfStatusCode = eosUtils.sendFileToBlobStorage(sfConnString, params, sfBase64, sfFileName, metadata)

                        if (sfStatusCode >= 200 && sfStatusCode <= 299) {
                            sfResponse << ['message': Constants.SUCCESSFUL_RESPONSE]
                            sfResponse << ['statusCode' : HttpStatus.OK]
                            sfResponse << ['metadata' : eosUtils.createMetadataObject('Send File', method, requestURI, sfTxnId)]
                            sfStatusCode = HttpStatus.OK
                        } else if (sfStatusCode == 409 || sfStatusCode == 400 || sfStatusCode == 404) {
                             if (sfStatusCode == 409) sfStatusCode = HttpStatus.BAD_REQUEST
                        } else {
                            sfStatusCode = HttpStatus.INTERNAL_SERVER_ERROR
                        }
                    }
				} else {
					sfStatusCode = HttpStatus.BAD_REQUEST					
				}				
			} else {				
				sfStatusCode = HttpStatus.BAD_REQUEST				
			}			
		} catch (any) {
			logger.error("::: send file: {} ::: ", any)
			sfStatusCode = HttpStatus.INTERNAL_SERVER_ERROR			
		}
		
		def endTime = System.nanoTime()
		logger.info "::: Storage Service retrieve overall transaction time ::: " + (endTime - startTime)
		
		if(sfStatusCode == HttpStatus.OK) {			
			workFlow.addResponseBody(new EntityResult(sfResponse, true))
			workFlow.addResponseStatus(HttpStatus.OK)			
		} else {
			switch (sfStatusCode) {
				case HttpStatus.BAD_REQUEST:
					sfResponse = eosUtils.createBadReqErrResp(requestURI, method, SERVICE_ID)
					workFlow.addResponseStatus(HttpStatus.BAD_REQUEST)
					break
                case HttpStatus.NOT_FOUND:
					sfResponse = eosUtils.createFinalErrorResponse(eosUtils.resourceNotFoundErrResp(), requestURI, method, SERVICE_ID)
					workFlow.addResponseStatus(HttpStatus.NOT_FOUND)
					break
				default:
					sfResponse = eosUtils.createSystemErrResp(requestURI, method, SERVICE_ID)
					workFlow.addResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
					break
			}
			workFlow.addResponseBody(new EntityResult(sfResponse, true))
		}
		

		workFlow.addResponseHeaders(eosUtils.createResponseHeader(Constants.GSSP_TRANX_ID_HEADER, sfTxnId))
		
		logger.info "::: Storage Service End Send File WorkFlow ::: "

		workFlow
	}
}
