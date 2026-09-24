package com.metlife.eos

import com.azure.storage.blob.models.BlobStorageException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.publisher.Flux
import java.nio.ByteBuffer
import com.azure.core.http.rest.Response
import com.azure.core.util.FluxUtil
import com.azure.storage.blob.BlobAsyncClient
import com.azure.storage.blob.BlobContainerAsyncClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.models.BlockBlobItem
import com.azure.storage.blob.models.ListBlobsOptions
import com.azure.storage.blob.specialized.BlockBlobAsyncClient
import com.metlife.gssp.common.utils.NanoCommonUtil
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.azure.core.exception.HttpResponseException
import groovy.CLIN.Constants
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import com.azure.storage.blob.BlobServiceAsyncClient

@Component
class EOSUtils {

	Logger logger = LoggerFactory.getLogger(EOSUtils)
	
	private static final Map<String, BlobServiceAsyncClient> CLIENT_CACHE = new ConcurrentHashMap<>()

	private BlobServiceAsyncClient getOrCreateClient(String connectionString) {
		return CLIENT_CACHE.computeIfAbsent(connectionString) { cs ->
			new BlobServiceClientBuilder()
					.connectionString(cs)
					.buildAsyncClient()
		}
	}

	def createSystemErrResp (def uri, def method, def serviceId) {
		def response = [:]
		def resp = systemErrResp()
		response = createFinalErrorResponse(resp, uri, method, serviceId)
		response
	}

	def createBadReqErrResp (def uri, def method, def serviceId) {
		def response = [:]
		def resp = badReqErrResp()
		response = createFinalErrorResponse(resp, uri, method, serviceId)
		response
	}

	def createNotAcceptableResp (def uri, def method, def serviceId) {
		def response = [:]
		def resp = noRecordsFoundResp()
		response = createFinalErrorResponse(resp, uri, method, serviceId)
		response
	}

	def badReqErrResp () {
		logger.info "::: Enter Method badReqErrResp() :::"
		def response = [:]
		response << ['errorCode' : Constants.BAD_REQUEST_ERR_CODE]
		response << ['statusCode' : HttpStatus.BAD_REQUEST]
		response << ['errorDescription' : Constants.BAD_REQUEST]
		logger.info "::: Exit Method badReqErrResp() :::"
		response
	}

	def resourceNotFoundErrResp () {
		logger.info "::: Enter Method resourceNotFoundErrResp() :::"
		def response = [:]
		response << ['errorCode' : Constants.RESOURCE_NOT_FOUND_ERR_CODE]
		response << ['statusCode' : HttpStatus.NOT_FOUND]
		response << ['errorDescription' : Constants.RESOURCE_NOT_FOUND]
		logger.info "::: Exit Method resourceNotFoundErrResp() :::"
		response
	}

	def noRecordsFoundResp(){
		logger.info "::: Enter Method noRecordsFoundResp() :::"
		def response = [:]
		response << ['errorCode' : Constants.NO_RECORDS_FOUND_RESP_CODE]
		response << ['statusCode' : HttpStatus.NOT_ACCEPTABLE]
		response << ['errorDescription' : Constants.NO_RECORDS_FOUND]
		logger.info "::: Exit Method noRecordsFoundResp() :::"
		response
	}

	def systemErrResp () {
		logger.info "::: Enter Method systemErrResp() :::"
		def response = [:]
		response << ['errorCode' : Constants.SYSTEM_ERROR_ERR_CODE]
		response << ['statusCode' : HttpStatus.INTERNAL_SERVER_ERROR]
		response << ['errorDescription' : Constants.SYSTEM_ERROR]
		logger.info "::: Exit Method systemErrResp() :::"
		response
	}

    // New Helper for consistent Azure Error Mapping
    def handleAzureException(Exception e, String contextInfo) {
        def response = [:]
        def statusCode = HttpStatus.INTERNAL_SERVER_ERROR
        def errorCode = Constants.SYSTEM_ERROR_ERR_CODE
        def errorDesc = Constants.SYSTEM_ERROR

        Throwable current = e
        BlobStorageException blobEx = null
        while(current != null){
            if(current instanceof BlobStorageException){
                blobEx = (BlobStorageException) current
                break
            }
            current = current.getCause()
        }

        if (blobEx) {
            def azureCode = blobEx.getErrorCode()?.toString()
            logger.error("::: Azure Error [{}]: {} | Context: {} :::", azureCode, blobEx.getMessage(), contextInfo)
            
            if (azureCode == "ContainerNotFound") {
                statusCode = HttpStatus.NOT_FOUND
                errorCode = Constants.CONTAINER_NOT_FOUND_ERR_CODE
                errorDesc = Constants.CONTAINER_NOT_FOUND
            } else if (azureCode == "BlobNotFound") {
                statusCode = HttpStatus.NOT_FOUND
                errorCode = Constants.BLOB_NOT_FOUND_ERR_CODE
                errorDesc = Constants.BLOB_NOT_FOUND
            } else if (blobEx.getStatusCode() == 409) {
                statusCode = HttpStatus.CONFLICT
                errorCode = Constants.STORAGE_CONFLICT_ERR_CODE
                errorDesc = Constants.STORAGE_CONFLICT
            } else if (blobEx.getStatusCode() == 400) {
                 statusCode = HttpStatus.BAD_REQUEST
                 errorCode = Constants.BAD_REQUEST_ERR_CODE
                 errorDesc = Constants.BAD_REQUEST
            }
        } else {
             logger.error("::: Generic/Network Error: {} | Context: {} :::", e.getMessage(), contextInfo, e)
             if (e.getMessage()?.toLowerCase()?.contains("connection")) {
                 statusCode = HttpStatus.BAD_GATEWAY
                 errorCode = Constants.AZURE_CONNECTION_ERR_CODE
                 errorDesc = Constants.AZURE_CONNECTION_ERROR
             }
        }

        response << ['errorCode': errorCode]
        response << ['statusCode': statusCode]
        response << ['errorDescription': errorDesc]
        return response
    }


	def sendFileToBlobStorage (def connString, def params, def codedFile, def fileName, def metadata) {
		logger.info("::: Enter Method sendFileToBlobStorage() :::")
		def decodedBytes = codedFile.decodeBase64()
		def statusCode= 500
		def container = params.containerName
		def id = params.entityId

		try {

			BlobContainerAsyncClient blobContainerAsyncClient = getOrCreateClient(connString)
					.getBlobContainerAsyncClient(container)

			BlobAsyncClient blobAsyncClient = blobContainerAsyncClient.getBlobAsyncClient(id + Constants.SLASH + fileName)

			BlockBlobAsyncClient blockBlobAsyncClient = blobAsyncClient.getBlockBlobAsyncClient()

			try {

				Mono<Response<BlockBlobItem>> resp = blockBlobAsyncClient.uploadWithResponse(Flux.just(ByteBuffer.wrap(decodedBytes)), decodedBytes.size(), null, metadata, null, null, null)

				statusCode = resp.block().getStatusCode()
				logger.info("::: Exit Method sendFileToBlobStorage() :::")

			}catch (HttpResponseException error) {
				statusCode = error.getResponse().getStatusCode()
				logger.error("::: failed to upload file: {}, in blob: {} :::", error, fileName)
			}
			catch (Exception e) {
				Throwable currentCause = e
				BlobStorageException blobException = null

				while (currentCause != null) {
					if (currentCause instanceof BlobStorageException) {
						blobException = (BlobStorageException) currentCause
						break
					}
					currentCause = currentCause.getCause()
				}

				if (blobException) {
					statusCode = blobException.getStatusCode() // Asigna 409
					logger.error("::: failed to upload file (Unwrapped BlobStorageException) with status {}: {} :::", statusCode, blobException.getMessage(), fileName)
				} else {
					logger.error("::: failed to upload file (Internal/Unknown error): {} :::", e.message.toString(), fileName)
				}
			}
		} catch (Exception e) {
            // Refactored to use handleAzureException logic but return int status for this specific method signature
            def errMap = handleAzureException(e, "Upload File: $fileName")
            def status = errMap.get('statusCode')
            statusCode = (status instanceof HttpStatus) ? status.value() : 500
		}
		statusCode
	}

	def sendFileToBlobStorageBinary (def connString, def params, byte[] fileBytes, def fileName, def metadata) {
		logger.info("::: Enter Method sendFileToBlobStorageBinary() :::")
		def statusCode= 500
		def container = params.containerName
		def id = params.entityId

		try {
			BlobContainerAsyncClient blobContainerAsyncClient = getOrCreateClient(connString)
					.getBlobContainerAsyncClient(container)

			BlobAsyncClient blobAsyncClient = blobContainerAsyncClient.getBlobAsyncClient(id + Constants.SLASH + fileName)
			BlockBlobAsyncClient blockBlobAsyncClient = blobAsyncClient.getBlockBlobAsyncClient()

			try {
				Mono<Response<BlockBlobItem>> resp = blockBlobAsyncClient.uploadWithResponse(Flux.just(ByteBuffer.wrap(fileBytes)), fileBytes.size(), null, metadata, null, null, null)

				statusCode = resp.block().getStatusCode()
				logger.info("::: Exit Method sendFileToBlobStorageBinary() :::")

			} catch (Exception e) {
                 def errMap = handleAzureException(e, "Upload Binary: $fileName")
                 def status = errMap.get('statusCode')
                 statusCode = (status instanceof HttpStatus) ? status.value() : 500
			}
		} catch (Exception e) {
             logger.error("::: Critical error init client: {} :::", e.message)
		     statusCode = 500
		}
		statusCode
	}

	def retrieveFileFromBlobStorage (def connString, def params, def fileName) {
		logger.info("::: Enter Method retrieveFileFromBlobStorage() :::")
		def response = []
		def containerName = params.containerName
		def folderName = params.entityId

		try {
			BlobContainerAsyncClient blobContainerAsyncClient = getOrCreateClient(connString)
					.getBlobContainerAsyncClient(containerName)

			BlobAsyncClient blobAsyncClient = blobContainerAsyncClient.getBlobAsyncClient(folderName + Constants.SLASH + fileName)

			logger.info("::: getting file from Azure Storage :::")
			def fileBytes = null
			try {
				Flux<ByteBuffer> responseBytes = blobAsyncClient.downloadStream()
				fileBytes =  FluxUtil.collectBytesInByteBufferStream(responseBytes).block()
			} catch (HttpResponseException error) {
				def statusCode = error.getResponse()?.getStatusCode()
				logger.error("::: {}, failed to download file, with statuscode: {}", blobAsyncClient.getBlobUrl(), statusCode)
			}

			def codeBase64 = fileBytes.encodeBase64().toString()
			response << ['base64' : codeBase64]
		} catch (Exception any) {
             def errMap = handleAzureException(any, "Retrieve: $fileName")
             response << ['error': errMap.get('statusCode')]
             response << ['details': errMap] // Optional: pass full details if caller wants it
		}
		logger.info("::: Exit Method retrieveFileFromBlobStorage() :::")
		response
	}


	def retrieveFileFromBlobStorageBinary (def connString, def params, def fileName) {
		logger.info("::: Enter Method retrieveFileFromBlobStorageBinary() :::")
		def response = [:]
		def containerName = params.containerName
		def folderName = params.entityId

		try {
			BlobContainerAsyncClient blobContainerAsyncClient = getOrCreateClient(connString)
					.getBlobContainerAsyncClient(containerName)

			BlobAsyncClient blobAsyncClient = blobContainerAsyncClient.getBlobAsyncClient(folderName + Constants.SLASH + fileName)

			logger.info("::: getting file from Azure Storage :::")
			byte[] fileBytes = null
			try {
				Flux<ByteBuffer> responseBytes = blobAsyncClient.downloadStream()
				fileBytes =  FluxUtil.collectBytesInByteBufferStream(responseBytes).block()
				response << ['fileBytes' : fileBytes]
			} catch (HttpResponseException error) {
				def statusCode = error.getResponse()?.getStatusCode()
				logger.error("::: {}, failed to download file, with statuscode: {}", blobAsyncClient.getBlobUrl(), statusCode)
				if (statusCode == 404) {
					response << ['error': HttpStatus.NOT_FOUND]
				} else {
					response << ['error': HttpStatus.INTERNAL_SERVER_ERROR]
				}
			}
		} catch (Exception any) {
             def errMap = handleAzureException(any, "Retrieve Binary: $fileName")
             response << ['error': errMap.get('statusCode')]
		}
		logger.info("::: Exit Method retrieveFileFromBlobStorageBinary() :::")
		response
	}

	def listAssetsFromBlobStorage(def connString, def params) {
		logger.info("::: Enter Method listAssetsFromBlobStorage() :::")
		def response = [:]
		def items = []
		def containerName = params.containerName
		def folderName = params.entityId

		try {
			BlobContainerAsyncClient blobContainerAsyncClient = getOrCreateClient(connString)
					.getBlobContainerAsyncClient(containerName)

			// Normalize folder path to ensure it ends with slash for prefix matching
			def prefix = folderName + Constants.SLASH

			logger.info("::: listing files from Azure Storage container: {}, prefix: {} :::", containerName, prefix)

			ListBlobsOptions options = new ListBlobsOptions().setPrefix(prefix)
			blobContainerAsyncClient.listBlobs(options)
					.toIterable()
					.each { blobItem ->
						def item = [
								'name': blobItem.getName().replace(prefix, ""), // Return relative name
								'size': blobItem.getProperties().getContentLength(),
								'createdOn': blobItem.getProperties().getCreationTime().toString(),
								'lastModified': blobItem.getProperties().getLastModified().toString()
						]
						items.add(item)
					}

			response << ['items': items]

		} catch (Exception any) {
             def errMap = handleAzureException(any, "List Files: $containerName / $folderName")
             response << ['error': errMap.get('statusCode')]
             response << ['details': errMap]
		}
		logger.info("::: Exit Method listAssetsFromBlobStorage() :::")
		response
	}

	static def createMetadataObject (def methodName, def methodType, def methodURI, def gsspTrxId) {
		def scnfMetadata = [:]
		scnfMetadata << ["gsspTrxId" : gsspTrxId]
		scnfMetadata << ["apiName" : Constants.API_NAME]
		scnfMetadata << ["apiVersion" : Constants.API_VERSION]
		scnfMetadata << ["methodName" : methodName]
		scnfMetadata << ["methodURI" : methodType + " " + methodURI]

		def timestampMillis = System.currentTimeMillis()
		def instant = Instant.ofEpochMilli(timestampMillis)
		def formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS").withZone(ZoneId.systemDefault())

		scnfMetadata << ["timestamp" : formatter.format(instant)]

		scnfMetadata
	}
	def validateInput(def input) {
		// Allow alphanumeric, dashes, dots, underscores, AND slashes. 
		// Explicitly prevent path traversal ("..").
		if (input == null || input.contains("..")) {
			return false
		}
		return input ==~ /^[a-zA-Z0-9._\-\/]+$/
	}

	def createFinalErrorResponse(def resp, def uri, def method, def serviceId) {
		logger.info "::: Enter Method createFinalErrorResponse() :::"
		def response = [:]
		def providerDescription = generateProviderDescription(resp?.getAt(Constants.ERROR_DESCRIPTION),
				uri, method)
		response = generateErrorResponse(serviceId, resp?.getAt(Constants.ERROR_CODE),
				resp?.getAt(Constants.ERROR_DESCRIPTION), providerDescription)
		logger.info "::: Exit Method createFinalErrorResponse() :::"
		response
	}

	def generateProviderDescription(def errorDescription, def uri, def apiHttpMethod) {
		logger.info "::: Enter Method generateProviderDescription() :::"
		def response
		response = errorDescription + ' ' + Constants.FOR + ' ' + apiHttpMethod + uri
		logger.info "::: Exit Method generateProviderDescription() :::"
		response
	}

	def generateErrorResponse(def serviceId, def errorCode, def errorDescription, def providerDescription) {
		logger.info "::: Enter Method generateErrorResponse() :::"
		def errorResponse = [:]
		errorResponse << ['code' : "API-$serviceId-$errorCode".toString()]
		errorResponse << ['description' : "$errorDescription".toString()]
		errorResponse << ['element' : '']
		def extension = [:]
		extension << ['providerCode': errorCode]
		extension << ['providerMessage': "$providerDescription".toString()]
		errorResponse << ['extension': extension]
		def response = ['errors':[errorResponse]]
		logger.info "::: Exit Method generateErrorResponse() :::"
		response
	}

	def createResponseHeader(def headerName,  def headerValue) {
		logger.info("::: Enter Method createResponseHeader() :::")
		HttpHeaders headers = new HttpHeaders()
		//headers.add('x-gssp-transactionid', txnId.toString())
		headers.add(headerName, headerValue)
		logger.info("::: Exit Method createResponseHeader() :::")
		headers
	}

}