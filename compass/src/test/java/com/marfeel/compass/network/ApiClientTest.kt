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
		conversions = "someone@email.com"
    )

    private val anyRfvData = RfvData(
        accountId = "accountId",
        userId = "userId",
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
    fun sendsPingRequestParametersWithProperNamesAndValues() {
        enqueueApiResponse(200)
        givenAnApiClient().ping(anyPingData)
        val formParams: Map<String, String> =
            server.takeRequest().body.readUtf8().split("&").associate { field ->
                val parts = field.split("=")
                parts[0] to parts[1]
            }
        assertEquals("accountId", formParams["ac"])
        assertEquals("1234", formParams["t"])
        assertEquals("referralUrl", formParams["r"])
        assertEquals("url", formParams["url"])
        assertEquals("url", formParams["c"])
        assertEquals("previousUrl", formParams["pp"])
        assertEquals("pageId", formParams["p"])
        assertEquals("userId", formParams["u"])
        assertEquals("sessionId", formParams["s"])
        assertEquals("0.2", formParams["v"])
        assertEquals("5678", formParams["a"])
        assertEquals("9012", formParams["n"])
        assertEquals("7980", formParams["l"])
        assertEquals("1234", formParams["ps"])
        assertEquals("1", formParams["ut"])
        assertEquals("false", formParams["uc"])
        assertEquals("5", formParams["sc"])
        assertEquals("3456", formParams["fv"])
        assertEquals("null", formParams["lv"])
        assertEquals("4", formParams["pageType"])
        assertEquals("someone@email.com", formParams["conv"])
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
            "application/x-www-form-urlencoded",
            server.takeRequest().headers["content-type"]
        )
    }

    @Test
    fun sendsRfvRequestParametersWithProperNamesAndValues() {
        enqueueApiResponse(200)
        givenAnApiClient().getRfv(anyRfvData)
        val formParams: Map<String, String> =
            server.takeRequest().body.readUtf8().split("&").associate { field ->
                val parts = field.split("=")
                parts[0] to parts[1]
            }
        assertEquals("accountId", formParams["ac"])
        assertEquals("userId", formParams["u"])
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
        return ApiClient(httpClient, baseUrl)
    }
}
