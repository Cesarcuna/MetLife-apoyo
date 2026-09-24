package groovy.CLIN;

class Constants {

	def static final TEMPLATE_DEFAULT_PATH = "src/main/resources/templates/"
	
	def static final APPLICATION_PROFILE_PATH = "src/main/resources/"
	
	def static final APPLICATION = "application"
	
	def static final YML = "yml"
	
	def static final FOR = "for"
	
	def static final TENANT_ID = "tenantId"
	
	def static final TXN_ID = "gsspTrxId"
	
	def static final EMPTY_STRING = ""
	
	def static final STATUS_CODE = "statusCode"
	
	def static final ERROR_CODE = "errorCode"
	
	def static final ERROR_DESCRIPTION = "errorDescription"
	
	def static final RESPONSE = "response"
	
	def static final TRANSLATED_RESPONSE = "translatedResponse"
	
	def static final BAD_REQUEST = "Bad request(ex- missing mandatory data)"
	
	def static final BAD_REQUEST_ERR_CODE = "400"
	
	def static final RESOURCE_NOT_FOUND = "Resource not found for id or name provided in ther URI"
	
	def static final RESOURCE_NOT_FOUND_ERR_CODE = "404"
	
	def static final SYSTEM_ERROR = "Internal System Error"
	
	def static final SYSTEM_ERROR_ERR_CODE = "500"

    def static final CONTAINER_NOT_FOUND = "The specified container does not exist"
    def static final CONTAINER_NOT_FOUND_ERR_CODE = "404.1"

    def static final BLOB_NOT_FOUND = "The specified file/blob does not exist"
    def static final BLOB_NOT_FOUND_ERR_CODE = "404.2"

    def static final STORAGE_CONFLICT = "Storage conflict (e.g. lease active or existing file)"
    def static final STORAGE_CONFLICT_ERR_CODE = "409"

    def static final AZURE_CONNECTION_ERROR = "Failed to connect to Azure Storage"
    def static final AZURE_CONNECTION_ERR_CODE = "502"
	
	def static final HEALTH_CHECK = "Welcome API REST - GKE"
	
	def static final CLAIMS_PROVIDER_CODE = "9005" 

	def static final NO_RECORDS_FOUND = "No records found to match search criteria"

	def static final NO_RECORDS_FOUND_RESP_CODE = "406"
	
	def static final ENTITY_ID = "entityId"
	def static final CONTAINER_NAME = "containerName"
	
	def static final SLASH = "/"

	def static final API_NAME = "Chile Insurance Storage API"

	def static final API_VERSION = "v1"

	def static final FILE_NAME = "fileName"
	
	def static final BASE64 = "base64"
	
	def static final GSSP_TRANX_ID_HEADER = "x-gssp-transactionid"
	
	def static final APIM_REQUEST_ID_HEADER = "Ocp-Apim-Trace"

	def static final SUCCESSFUL_RESPONSE = "File Uploaded Successfully"

	def static final SERVICE_ID_SOYIO_SYNC = "0910"
	def static final SERVICE_ID_SOYIO_SYNC_STATUS = "0911"
	def static final SERVICE_ID_SOYIO_LIST_API_KEYS = "0912"

	def static final SOYIO_STATUS_ACCEPTED = "accepted"
	def static final SOYIO_STATUS_RUNNING = "running"
	def static final SOYIO_STATUS_COMPLETED = "completed"
	def static final SOYIO_STATUS_COMPLETED_WITH_ERRORS = "completed_with_errors"
	def static final SOYIO_STATUS_FAILED = "failed"
	def static final SOYIO_STATUS_NEVER = "never"

	def static final DEFAULT_SOYIO_RESOURCE = "consent_actions"
	def static final DEFAULT_SOYIO_OUTPUT_FORMAT = "csv"

	def static final WATERMARK_SOURCE_BLOB = "blob-control-file"
	def static final WATERMARK_FILE_NAME = "watermark.json"
	def static final SUCCESS_FILE_NAME = "_SUCCESS"
	def static final DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"
	def static final STORAGE_TIMESTAMP_PATTERN = "yyyyMMdd'T'HHmmss"
	def static final YYYY_MM_DD_PATTERN = "yyyy/MM/dd"

	def static final CONF_SOYIO_BASE_URL = "soyio.base-url"
	def static final CONF_SOYIO_API_KEY = "soyio.api-key"
	def static final CONF_SOYIO_DEFAULT_RESOURCE = "soyio.default-resource"
	def static final CONF_SOYIO_DEFAULT_OUTPUT_FORMAT = "soyio.default-output-format"
	def static final CONF_SOYIO_DEFAULT_OVERLAP_MINUTES = "soyio.default-overlap-minutes"
	def static final CONF_SOYIO_INITIAL_LOOKBACK_HOURS = "soyio.initial-lookback-hours"
	def static final CONF_SOYIO_POLL_INTERVAL_SECONDS = "soyio.poll-interval-seconds"
	def static final CONF_SOYIO_POLL_TIMEOUT_SECONDS = "soyio.poll-timeout-seconds"
	def static final CONF_SOYIO_MAX_DOWNLOAD_BYTES = "soyio.max-download-bytes"
	def static final CONF_SOYIO_SEGMENT_MINUTES = "soyio.segment-minutes"
	def static final CONF_SOYIO_MAX_SEGMENTS_PER_RUN = "soyio.max-segments-per-run"
	def static final CONF_SOYIO_EXPORT_ORDER_BY = "soyio.export-order-by"
	def static final CONF_SOYIO_TARGET_CONTAINER = "soyio.target-container"
	def static final CONF_SOYIO_TARGET_ENTITY_ID = "soyio.target-entity-id"
	def static final CONF_SOYIO_CONTROL_CONTAINER = "soyio.control-container"
	def static final CONF_SOYIO_CONTROL_ENTITY_ID = "soyio.control-entity-id"
	def static final CONF_SOYIO_CONTROL_FILE_NAME = "soyio.control-file-name"
	def static final CONF_SOYIO_USER_REFERENCE_BASE_URL = "soyio.user-reference.base-url"
	def static final CONF_SOYIO_USER_REFERENCE_RESOLVE_PATH = "soyio.user-reference.resolve-path"
	def static final CONF_SOYIO_USER_REFERENCE_TENANT_ID = "soyio.user-reference.tenant-id"
	def static final CONF_SOYIO_USER_REFERENCE_ENVIRONMENT = "soyio.user-reference.environment"
	def static final CONF_SOYIO_USER_REFERENCE_CLIENT_ID = "soyio.user-reference.client-id"
	def static final CONF_SOYIO_USER_REFERENCE_SCOPE = "soyio.user-reference.scope"
	def static final CONF_SOYIO_USER_REFERENCE_ASSERTION_URL = "soyio.user-reference.client-assertion-url"
	def static final CONF_SOYIO_USER_REFERENCE_API_KEY = "soyio.user-reference.api-key"
	def static final CONF_SOYIO_USER_REFERENCE_SUBSCRIPTION_KEY = "soyio.user-reference.subscription-key"
	def static final CONF_SOYIO_USER_REFERENCE_BATCH_SIZE = "soyio.user-reference.batch-size"
	def static final CONF_SOYIO_USER_REFERENCE_DELAY_MILLIS = "soyio.user-reference.delay-millis"
	def static final CONF_SOYIO_USER_REFERENCE_MAX_RETRIES = "soyio.user-reference.max-retries"
	def static final CONF_SOYIO_NORMALIZED_CONTAINER = "soyio.normalized-output.container"
	def static final CONF_SOYIO_NORMALIZED_ENTITY_ID = "soyio.normalized-output.entity-id"
	
}
