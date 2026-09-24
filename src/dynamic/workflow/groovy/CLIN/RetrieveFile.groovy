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

class RetrieveFile implements Task {
	
	Logger logger = LoggerFactory.getLogger(RetrieveFile)
	
	@Autowired
	EOSUtils eosUtils
	
	def static final SERVICE_ID = "0902"
    def static final DEFAULT_CONTAINER_NAME = "agreement-files" // Can be parameterized if needed
	
	@Override 
	public Object execute(WorkflowDomain workFlow) {
		
		logger.info "::: Storage Service Begin Retrieve File Workflow :::"
		
		def startTime = System.nanoTime()
		def rfRequestParams = workFlow.getRequestParams()
		def rfTxnId = workFlow.getRequestHeader().get(Constants.GSSP_TRANX_ID_HEADER)
		def method = workFlow.getRequestMethod()
		def requestURI = workFlow.getRequestURI()
		def rfConnString = workFlow.getEnvPropertyFromContext("storage.connection-string")?.toString()?.trim()
		def rfStatusCode
		def rfResponse = [:]
		
		if (rfTxnId == null || rfTxnId.isEmpty()) {
			rfTxnId = MDC.get(Constants.TXN_ID)
		}
			
		try{
			logger.info "::: Storage Service checking request parameters :::"

			if(rfRequestParams){
				// Use standard query parameters instead of RSQL 'q'
				def rfEntityId = rfRequestParams?.get(Constants.ENTITY_ID)?.toString()
				def rfFileName = rfRequestParams?.get(Constants.FILE_NAME)?.toString()
				def rfContainerName = rfRequestParams?.get(Constants.CONTAINER_NAME)?.toString()
				
				if (rfEntityId != null && rfEntityId != "null" && !rfEntityId.isBlank() 
					&& rfFileName != null && rfFileName != "null" && !rfFileName.isBlank()
					&& rfContainerName != null && rfContainerName != "null" && !rfContainerName.isBlank()) {
					
                    // Security Check
                    if (!eosUtils.validateInput(rfContainerName) || !eosUtils.validateInput(rfEntityId)) {
                        logger.error("::: Invalid Characters in Input - Potential Path Traversal or Injection :::")
                        rfStatusCode = HttpStatus.BAD_REQUEST
                    } else {
                        def params = [
                            'containerName': rfContainerName,
                            'entityId': rfEntityId
                        ]
                        
                        def rpfBlobStorageResponse = eosUtils.retrieveFileFromBlobStorage(rfConnString, params, rfFileName)
                        
                        if(rpfBlobStorageResponse.any({it.containsKey('base64')})) {
                            rfResponse << ['entityId' : rfEntityId]
                            rfResponse << ['fileName' : rfFileName]
                            rfResponse << ['base64' : rpfBlobStorageResponse.getAt('base64')[0]]
                            rfResponse << ['metadata' : eosUtils.createMetadataObject('Retrieve File', method, requestURI, rfTxnId)]
                            
                            rfStatusCode = HttpStatus.OK
                        } else {
                            def errStatus = rpfBlobStorageResponse.getAt('error')[0]
                            rfStatusCode = (errStatus) ? errStatus : HttpStatus.INTERNAL_SERVER_ERROR
                        }
                    }
				} else {					
					rfStatusCode = HttpStatus.BAD_REQUEST					
				}				
			} else {				
				rfStatusCode = HttpStatus.BAD_REQUEST				
			}			
		} catch (any) {			
			logger.error("::: retrieve file: {} ::: ", any)
			rfStatusCode = HttpStatus.INTERNAL_SERVER_ERROR			
		}
		
		def endTime = System.nanoTime()		
		logger.info "::: Storage Service retrieve overall transaction time ::: " + (endTime - startTime)
		
		if(rfStatusCode == HttpStatus.OK) {		
			workFlow.addResponseBody(new EntityResult(rfResponse, true))
			workFlow.addResponseStatus(HttpStatus.OK)			
		} else {			
			switch (rfStatusCode) {				
				case HttpStatus.BAD_REQUEST:
					 rfResponse = eosUtils.createBadReqErrResp(requestURI, method, SERVICE_ID)
					 workFlow.addResponseStatus(HttpStatus.BAD_REQUEST)
					 break
				case HttpStatus.INTERNAL_SERVER_ERROR:
					 rfResponse = eosUtils.createSystemErrResp(requestURI, method, SERVICE_ID)
					 workFlow.addResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
					 break
				case HttpStatus.NOT_FOUND:
					 rfResponse = eosUtils.createFinalErrorResponse(eosUtils.resourceNotFoundErrResp(), requestURI, method, SERVICE_ID)
					 workFlow.addResponseStatus(HttpStatus.NOT_FOUND)
					 break					 
			   }
			   
			   workFlow.addResponseBody(new EntityResult(rfResponse, true))
		}

		workFlow.addResponseHeaders(eosUtils.createResponseHeader(Constants.GSSP_TRANX_ID_HEADER,rfTxnId))	
		
		logger.info "::: Storage Service End Retrieve File WorkFlow ::: "
        
        return workFlow
	}	
}
