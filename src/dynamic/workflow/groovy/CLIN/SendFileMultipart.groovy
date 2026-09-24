package groovy.CLIN

import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.multipart.MultipartFile
import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.eos.EOSUtils
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task

class SendFileMultipart implements Task {
	
	Logger logger = LoggerFactory.getLogger(SendFileMultipart)
	
	@Autowired
	EOSUtils eosUtils

	def static final SERVICE_ID = "0905"
	
	@Override
    Object execute(WorkflowDomain workFlow) {

		logger.info "::: Storage Service Begin Send File Multipart Workflow :::"
		
		def startTime = System.nanoTime()
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
			logger.info "::: Storage Service checking request parameters (Multipart) :::"
            
            // Retrieving File from Multipart
            MultipartFile uploadedFile = workFlow.getMultipartFile()
            
            // Retrieving metadata parameters. 
            // In Multipart, these might come as 'Request Parameters' or mapped Body. 
            // We check both for robustness.
            def reqBody = workFlow.getRequestBody()
            def sfContainerName = reqBody?.getAt(Constants.CONTAINER_NAME)?.toString()
            def sfEntityId = reqBody?.getAt(Constants.ENTITY_ID)?.toString()

            // If not found in body, check specific request params (e.g. if sent as Query Params or Form Fields mapped differently)
            if (sfContainerName == null) sfContainerName = workFlow.getRequestParams()?.get(Constants.CONTAINER_NAME)
            if (sfEntityId == null) sfEntityId = workFlow.getRequestParams()?.get(Constants.ENTITY_ID)

            // Filename: Prefer one sent in params, fallback to original filename
            def sfFileName = reqBody?.getAt(Constants.FILE_NAME)?.toString()
            if (sfFileName == null) sfFileName = workFlow.getRequestParams()?.get(Constants.FILE_NAME)
            if (sfFileName == null && uploadedFile != null) sfFileName = uploadedFile.getOriginalFilename()
			
			if(uploadedFile != null && !uploadedFile.isEmpty() && sfContainerName != null && !sfContainerName.isEmpty() && sfEntityId != null && !sfEntityId.isEmpty()) {
                
                 if (!eosUtils.validateInput(sfContainerName) || !eosUtils.validateInput(sfEntityId)) {
                    logger.error("::: Invalid Characters in Input - Potential Path Traversal or Injection :::")
                    sfStatusCode = HttpStatus.BAD_REQUEST
                } else {
                    def metadata = eosUtils.createMetadataObject('Send File Multipart', method, requestURI, sfTxnId)
                    
                    def params = [
                        'containerName': sfContainerName,
                        'entityId': sfEntityId
                    ]
                    
                    byte[] fileBytes = uploadedFile.getBytes()

                    // Call BINARY method reusing logic
                    sfStatusCode = eosUtils.sendFileToBlobStorageBinary(sfConnString, params, fileBytes, sfFileName, metadata)

                    if (sfStatusCode >= 200 && sfStatusCode <= 299) {
                        sfResponse << ['message': Constants.SUCCESSFUL_RESPONSE]
                        sfResponse << ['statusCode' : HttpStatus.OK]
                        sfResponse << ['fileName': sfFileName]
                        sfResponse << ['size': fileBytes.length]
                        sfResponse << ['metadata' : eosUtils.createMetadataObject('Send File Multipart', method, requestURI, sfTxnId)]
                        sfStatusCode = HttpStatus.OK
                    } else if (sfStatusCode == 409 || sfStatusCode == 400 || sfStatusCode == 404) {
                        // Pass specific status code through to switch handler
                        if (sfStatusCode == 409) sfStatusCode = HttpStatus.BAD_REQUEST // Map Conflict to Bad Request as per GSSP norm if no specific conflict handler
                    } else {
                        sfStatusCode = HttpStatus.INTERNAL_SERVER_ERROR
                    }
                }

			} else {
                logger.error("::: Missing Multipart File or Parameters. Container: {}, Entity: {}, FilePresent: {} :::", sfContainerName, sfEntityId, (uploadedFile != null))
				sfStatusCode = HttpStatus.BAD_REQUEST					
			}			
			} catch (any) {
			logger.error("::: send file multipart error: {} ::: ", any)
			sfStatusCode = HttpStatus.INTERNAL_SERVER_ERROR			
		}
		
		def endTime = System.nanoTime()
		logger.info "::: Storage Service send multipart overall transaction time ::: " + (endTime - startTime)
		
		if(sfStatusCode == HttpStatus.OK) {			
			workFlow.addResponseBody(new EntityResult(sfResponse, true))
			workFlow.addResponseStatus(HttpStatus.OK)			
		} else {
             // Standard GSSP Error Response Mapping
			switch (sfStatusCode) {
				case HttpStatus.BAD_REQUEST:
					sfResponse = eosUtils.createBadReqErrResp(requestURI, method, SERVICE_ID)
					workFlow.addResponseStatus(HttpStatus.BAD_REQUEST)
					break
                case HttpStatus.NOT_FOUND:
					// Use createFinalErrorResponse with resourceNotFoundErrResp
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
		
		logger.info "::: Storage Service End Send File Multipart WorkFlow ::: "

		workFlow
	}
}
