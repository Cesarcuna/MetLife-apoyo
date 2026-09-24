package groovy.CLIN

import org.springframework.http.HttpStatus

import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task

class HealthCheck implements Task{

	Logger logger = LoggerFactory.getLogger(HealthCheck)

	@Override
	public Object execute(WorkflowDomain workFlow) {
		def healthResponse = [:]
		healthResponse << ['health': Constants.HEALTH_CHECK]
		workFlow.addResponseBody(new EntityResult(healthResponse, true))
		workFlow.addResponseStatus(HttpStatus.OK)
	}
}
