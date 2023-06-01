package com.marfeel.compass.network

import com.marfeel.compass.core.model.PingData
import com.marfeel.compass.core.model.compass.RFV
import com.marfeel.compass.core.model.compass.RfvPayloadData
import com.marfeel.compass.core.model.compass.UserType
import junit.framework.TestCase.assertEquals
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test

class ApiClientTest {
	private val server: MockWebServer = MockWebServer()
	private val baseUrl = server.url("/").toString()

	private val anyPingData = PingData(
		accountId = "accountId",
		sessionTimeStamp = 1234L,
		url = "url",
		canonicalUrl = "url",
		previousUrl = "previousUrl",
		pageId = "pageId",
		originalUserId = "userId",
		sessionId = "sessionId",
		pingCounter = 5678,
		currentTimeStamp = 9012L,
		userType = UserType.Anonymous,
		registeredUserId = "registeredUserId",
		firsVisitTimeStamp = 3456L,
		previousSessionTimeStamp = null,
		version = "1.0",
		pageVars = mapOf("pepe" to "pepa", "lolo" to "lola"),
		userVars = mapOf("user" to "var"),
		sessionVars = mapOf("session" to "var"),
		userSegments = listOf("user", "segment"),
		pageType = 4
	)

	private val anyRfvPayloadData = RfvPayloadData(
		accountId = "accountId",
		registeredUserId = "userId",
		originalUserId = "SGIJSPDGIJSDPG",
		previousSessionTimeStamp = null
	)

	@After
	fun tearDown() {
		server.shutdown()
	}

	@Test
	fun sendsPingRequestWithTheExpectedVerb() {
		enqueueApiResponse(200)
		givenAnApiClient().ping(PingPaths.INGEST, anyPingData)
		assertEquals("POST", server.takeRequest().method)
	}

	@Test
	fun sendsPingRequestWithTheExpectedHeaders() {
		enqueueApiResponse(200)
		givenAnApiClient().ping(PingPaths.INGEST, anyPingData)
		assertEquals(
			"application/x-www-form-urlencoded",
			server.takeRequest().headers["content-type"]
		)
	}

	@Test
	fun sendsRfvRequestWithTheExpectedVerb() {
		enqueueApiResponse(200)
		givenAnApiClient().getRfv(anyRfvPayloadData)
		assertEquals("POST", server.takeRequest().method)
	}

	@Test
	fun sendsRfvRequestWithTheExpectedHeaders() {
		enqueueApiResponse(200)
		givenAnApiClient().getRfv(anyRfvPayloadData)
		assertEquals(
			"text/plain; charset=utf-8",
			server.takeRequest().headers["content-type"]
		)
	}

	@Test
	fun returnsRfvResponseBodyIfApiCallSucceeds() {
		val responseBody = "{\"rfv\": 0, \"r\": 0, \"f\": 0, \"v\": 0}"
		enqueueApiResponse(200, responseBody)
		val response = givenAnApiClient().getRfv(anyRfvPayloadData)
		val expected = Result.success(RFV(0f, 0, 0, 0))

		assertEquals(expected, response)
	}

	private fun enqueueApiResponse(statusCode: Int = 200, body: String? = null) {
		val response = MockResponse()
		response.setResponseCode(statusCode)
		if (body != null) {
			response.setBody(body)
		}

		server.enqueue(response)
	}

	private fun givenAnApiClient(): ApiClient {
		val httpClient = OkHttpClient()
		return ApiClient(httpClient, baseUrl, baseUrl)
	}
}
