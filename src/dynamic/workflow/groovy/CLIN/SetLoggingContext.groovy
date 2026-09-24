package groovy.CLIN

import org.apache.commons.lang3.StringUtils
import org.slf4j.MDC

import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.taskflow.Task

class SetLoggingContext implements Task{

	private static final String X_GSSP_MICROSERVICE_TRX_ID="X-GSSPMicroservice-TrxId";


	def execute(WorkflowDomain workFlow) {

		def requestPathParams = workFlow.getRequestPathParams()
		def headers = workFlow.getRequestHeader()
		def xGsspTrxIdKey = workFlow.getEnvPropertyFromContext('header.transactionId-key')
		def tenantId =    requestPathParams?.tenantId
		def trxIdFromHeader = headers?.getAt(xGsspTrxIdKey)
		def transactionId

		if(trxIdFromHeader instanceof Collection){
			transactionId = trxIdFromHeader?.getAt(0)
		}else {
			transactionId = trxIdFromHeader
		}


		if (StringUtils.isEmpty(transactionId)) {
			transactionId = (String) headers.getAt(X_GSSP_MICROSERVICE_TRX_ID);
			headers.put(xGsspTrxIdKey, transactionId);
		}
		MDC.put(Constants.TENANT_ID, tenantId)
		MDC.put(Constants.TXN_ID, transactionId)
	}
}