package com.marfeel.compass.network

import com.marfeel.compass.core.PingData
import com.marfeel.compass.core.RfvData
import com.marfeel.compass.core.UserType
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
		scrollPercent = 5,
		firsVisitTimeStamp = 3456L,
		previousSessionTimeStamp = null,
		timeOnPage = 7980,
		pageStartTimeStamp = 1234L,
		conversions = "someone@email.com",
		version = "1.0"
	)

	private val anyRfvData = RfvData(
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
		givenAnApiClient().ping(anyPingData)
		assertEquals("POST", server.takeRequest().method)
	}

	@Test
	fun sendsPingRequestWithTheExpectedHeaders() {
		enqueueApiResponse(200)
		givenAnApiClient().ping(anyPingData)
		assertEquals(
			"application/x-www-form-urlencoded",
			server.takeRequest().headers["content-type"]
		)
	}

	@Test
	fun sendsRfvRequestWithTheExpectedVerb() {
		enqueueApiResponse(200)
		givenAnApiClient().getRfv(anyRfvData)
		assertEquals("POST", server.takeRequest().method)
	}

	@Test
	fun sendsRfvRequestWithTheExpectedHeaders() {
		enqueueApiResponse(200)
		givenAnApiClient().getRfv(anyRfvData)
		assertEquals(
			"text/plain; charset=utf-8",
			server.takeRequest().headers["content-type"]
		)
	}

	@Test
	fun returnsRfvResponseBodyIfApiCallSucceeds() {
		val responseBody = "response-body"
		enqueueApiResponse(200, responseBody)
		val response = givenAnApiClient().getRfv(anyRfvData)
		val expected = Result.success(responseBody)
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
