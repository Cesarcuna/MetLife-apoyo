package com.metlife.eos

import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.springframework.stereotype.Component

import java.nio.charset.StandardCharsets

@Component
class SoyioUtils {

	Logger logger = LoggerFactory.getLogger(SoyioUtils)

	def createExport(String baseUrl, String apiKey, String resource, String outputFormat, String whereJson, String orderBy) {
		def requestBody = [
			resource: resource,
			output_format: outputFormat,
			where: whereJson
		]

		if (orderBy != null && !orderBy.isBlank()) {
			requestBody.order_by = orderBy
		}

		def url = "${normalizeBaseUrl(baseUrl)}/exports"
		requestJson(url, "POST", apiKey, requestBody)
	}

	def getExportStatus(String baseUrl, String apiKey, String exportId) {
		def url = "${normalizeBaseUrl(baseUrl)}/exports/${exportId}"
		requestJson(url, "GET", apiKey, null)
	}

	def listApiKeys(String baseUrl, String apiKey) {
		def url = "${normalizeBaseUrl(baseUrl)}/api_keys"
		requestJson(url, "GET", apiKey, null)
	}

	def getUserReferenceAccessToken(String tenantId, String clientId, String scope, String assertionUrl, String environment) {
		def assertionEndpoint = "${assertionUrl}${assertionUrl.contains('?') ? '&' : '?'}env=${urlEncode(environment)}"
		def assertionResult = requestJson(assertionEndpoint, "GET", null, null)
		if (assertionResult.statusCode < 200 || assertionResult.statusCode >= 300) {
			return [statusCode: assertionResult.statusCode, response: [:], body: assertionResult.body, stage: 'client_assertion']
		}

		def assertion = assertionResult.response?.assertion?.toString()
		if (assertion == null || assertion.isBlank()) {
			return [statusCode: 502, response: [:], body: 'Assertion endpoint did not return assertion', stage: 'client_assertion']
		}

		def tokenUrl = "https://login.microsoftonline.com/${tenantId}/oauth2/v2.0/token"
		def form = [
			client_id: clientId,
			scope: scope,
			client_assertion: assertion,
			client_assertion_type: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer',
			grant_type: 'client_credentials'
		].collect { key, value -> "${urlEncode(key)}=${urlEncode(value)}" }.join('&')
		def tokenResult = requestForm(tokenUrl, form)
		if (tokenResult.statusCode < 200 || tokenResult.statusCode >= 300) {
			return [statusCode: tokenResult.statusCode, response: [:], body: tokenResult.body, stage: 'access_token']
		}

		def accessToken = tokenResult.response?.access_token?.toString()
		if (accessToken == null || accessToken.isBlank()) {
			return [statusCode: 502, response: [:], body: 'Token endpoint did not return access_token', stage: 'access_token']
		}
		return [statusCode: 200, accessToken: accessToken, expiresIn: tokenResult.response?.expires_in]
	}

	def resolveUserReference(String baseUrl, String resolvePath, String accessToken, String apiKey, String subscriptionKey, String userReference) {
		def url = "${normalizeBaseUrl(baseUrl)}/${resolvePath?.toString()?.replaceAll('^/', '')}"
		def headers = [
			Authorization: "Bearer ${accessToken}",
			'X-Api-Key': apiKey,
			'Ocp-Apim-Subscription-Key': subscriptionKey
		]
		requestJson(url, "POST", null, [user_reference: userReference], headers)
	}

	byte[] downloadSignedFile(String downloadUrl) {
		HttpURLConnection connection = null
		try {
			connection = (HttpURLConnection) new URL(downloadUrl).openConnection()
			connection.setRequestMethod("GET")
			connection.setConnectTimeout(30000)
			connection.setReadTimeout(60000)

			int status = connection.getResponseCode()
			if (status < 200 || status >= 300) {
				def errorBody = readBody(connection.getErrorStream())
				throw new IllegalStateException("Download failed with status ${status}: ${errorBody}")
			}

			return readBytes(connection.getInputStream())
		} finally {
			if (connection != null) {
				connection.disconnect()
			}
		}
	}

	private def requestJson(String rawUrl, String method, String apiKey, Map body, Map extraHeaders = [:]) {
		HttpURLConnection connection = null
		try {
			connection = (HttpURLConnection) new URL(rawUrl).openConnection()
			connection.setRequestMethod(method)
			connection.setConnectTimeout(30000)
			connection.setReadTimeout(60000)
			connection.setRequestProperty("Accept", "application/json")
			if (apiKey != null && !apiKey.isBlank()) {
				connection.setRequestProperty("Authorization", "Bearer ${apiKey}")
			}
			extraHeaders?.each { key, value ->
				if (value != null && !value.toString().isBlank()) {
					connection.setRequestProperty(key.toString(), value.toString())
				}
			}

			if (body != null) {
				connection.setDoOutput(true)
				connection.setRequestProperty("Content-Type", "application/json")
				def requestText = JsonOutput.toJson(body)
				connection.getOutputStream().withWriter(StandardCharsets.UTF_8.name()) { writer ->
					writer << requestText
				}
			}

			int statusCode = connection.getResponseCode()
			def responseText = statusCode >= 200 && statusCode < 300
				? readBody(connection.getInputStream())
				: readBody(connection.getErrorStream())

			def responseJson = [:]
			if (responseText != null && !responseText.isBlank()) {
				def parsed = new JsonSlurper().parseText(responseText)
				if (parsed instanceof Map) {
					responseJson = parsed as Map
				}
			}

			return [
				statusCode: statusCode,
				response: responseJson,
				body: responseText
			]
		} catch (Exception ex) {
			logger.error("::: Soyio request failed [{} {}]: {} :::", method, rawUrl, ex.getMessage(), ex)
			return [
				statusCode: 500,
				response: [:],
				body: ex.getMessage()
			]
		} finally {
			if (connection != null) {
				connection.disconnect()
			}
		}
	}

	private def requestForm(String rawUrl, String formBody) {
		HttpURLConnection connection = null
		try {
			connection = (HttpURLConnection) new URL(rawUrl).openConnection()
			connection.setRequestMethod('POST')
			connection.setConnectTimeout(30000)
			connection.setReadTimeout(60000)
			connection.setDoOutput(true)
			connection.setRequestProperty('Accept', 'application/json')
			connection.setRequestProperty('Content-Type', 'application/x-www-form-urlencoded')
			connection.getOutputStream().withWriter(StandardCharsets.UTF_8.name()) { writer -> writer << formBody }

			int statusCode = connection.getResponseCode()
			def responseText = statusCode >= 200 && statusCode < 300 ? readBody(connection.getInputStream()) : readBody(connection.getErrorStream())
			def responseJson = [:]
			if (responseText != null && !responseText.isBlank()) {
				def parsed = new JsonSlurper().parseText(responseText)
				if (parsed instanceof Map) responseJson = parsed as Map
			}
			return [statusCode: statusCode, response: responseJson, body: responseText]
		} catch (Exception ex) {
			logger.error("::: Token form request failed [{}]: {} :::", rawUrl, ex.getMessage(), ex)
			return [statusCode: 500, response: [:], body: ex.getMessage()]
		} finally {
			connection?.disconnect()
		}
	}

	private String urlEncode(def value) {
		return URLEncoder.encode(value?.toString() ?: '', StandardCharsets.UTF_8.name())
	}

	private String normalizeBaseUrl(String baseUrl) {
		if (baseUrl == null) {
			return ""
		}
		return baseUrl.endsWith("/") ? baseUrl[0..-2] : baseUrl
	}

	private String readBody(InputStream inputStream) {
		if (inputStream == null) {
			return ""
		}

		inputStream.withCloseable {
			return new String(readBytes(it), StandardCharsets.UTF_8)
		}
	}

	private byte[] readBytes(InputStream inputStream) {
		if (inputStream == null) {
			return new byte[0]
		}

		inputStream.withCloseable { stream ->
			ByteArrayOutputStream buffer = new ByteArrayOutputStream()
			byte[] data = new byte[8192]
			int nRead
			while ((nRead = stream.read(data, 0, data.length)) != -1) {
				buffer.write(data, 0, nRead)
			}
			buffer.flush()
			return buffer.toByteArray()
		}
	}
}