package ru.kavader.warchimcp.client

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest
import org.springframework.web.client.RestClient
import ru.kavader.warchimcp.auth.ApiKeyContext

class AreposAuthClientTest {

    private lateinit var server: MockRestServiceServer
    private lateinit var authClient: AreposAuthClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl("http://localhost")
        server = MockRestServiceServer.bindTo(builder).build()
        authClient = AreposAuthClient(builder.build())
    }

    @AfterEach
    fun cleanup() {
        ApiKeyContext.clear()
        server.reset()
    }

    @Test
    fun `exchanges api key and caches token`() {
        ApiKeyContext.set("Bearer warchi_ak_testkey0123456789abcdefghijklmnop")
        server.expect(requestTo("http://localhost/api/v1/auth/api-keys/exchange"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"accessToken":"jwt-1","expiresIn":1200}""",
                    MediaType.APPLICATION_JSON
                )
            )

        assertEquals("jwt-1", authClient.accessToken())
        assertEquals("jwt-1", authClient.accessToken())
        server.verify()
    }

    @Test
    fun `propagates unauthorized exchange`() {
        ApiKeyContext.set("Bearer warchi_ak_testkey0123456789abcdefghijklmnop")
        server.expect(requestTo("http://localhost/api/v1/auth/api-keys/exchange"))
            .andRespond(withUnauthorizedRequest())

        assertThrows(AreposClientException::class.java) {
            authClient.accessToken()
        }
    }
}
