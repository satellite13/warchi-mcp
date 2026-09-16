package ru.kavader.warchimcp.tools

import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import ru.kavader.warchimcp.auth.ApiKeyContext
import ru.kavader.warchimcp.client.AreposApiClient
import ru.kavader.warchimcp.client.AreposAuthClient

/**
 * ensure_custom_properties targeting a node type WITHOUT a notation component
 * (e.g. the folder node type "Directory", which lives outside any notation):
 * componentId is omitted, nodeTypeId carries the schema.
 */
class EnsureCustomPropertiesNodeTypeTest {

    private val mapper = ObjectMapper().findAndRegisterModules()
    private lateinit var server: MockRestServiceServer
    private lateinit var notation: NotationTools

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl("http://localhost")
        server = MockRestServiceServer.bindTo(builder).build()
        val api = AreposApiClient(builder.build(), AreposAuthClient(builder.build()), mapper)
        notation = NotationTools(api, mapper)
        ApiKeyContext.set("Bearer warchi_ak_testkey0123456789abcdefghijklmnop")
    }

    @AfterEach
    fun tearDown() {
        ApiKeyContext.clear()
        server.reset()
    }

    private fun expectAuth() {
        server.expect(requestTo("http://localhost/api/v1/auth/api-keys/exchange"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"accessToken":"jwt-test","expiresIn":3600}""",
                    MediaType.APPLICATION_JSON
                )
            )
    }

    @Test
    fun `node type without component gets the schema`() {
        expectAuth()

        server.expect(requestTo("http://localhost/api/v1/node-types/dir-type-1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"id":"dir-type-1","name":"Directory","attrs":"{\"kind\":\"directory\",\"system\":true}"}""",
                    MediaType.APPLICATION_JSON
                )
            )

        server.expect(requestTo("http://localhost/api/v1/node-types/dir-type-1"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(
                withSuccess(
                    """{"id":"dir-type-1","name":"Directory"}""",
                    MediaType.APPLICATION_JSON
                )
            )

        val result = notation.ensureCustomProperties(
            componentId = null,
            nodeTypeId = "dir-type-1",
            propertiesJson = """[{"name":"humanId","type":"string","maxLength":40}]"""
        )
        val parsed = mapper.readTree(result)
        assertTrue(parsed.path("ok").asBoolean())
        assertNull(parsed.path("data").path("component").takeIf { !it.isNull }?.path("id")?.asText())
        assertEquals("dir-type-1", parsed.path("data").path("nodeType").path("id").asText())
        assertTrue(parsed.path("data").path("nodeType").path("changed").asBoolean())
        assertEquals("humanId", parsed.path("data").path("nodeType").path("added").get(0).asText())
        server.verify()
    }

    @Test
    fun `explicit nodeTypeId overrides the component node type`() {
        expectAuth()

        server.expect(requestTo(containsString("/api/v1/components/comp-1")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"id":"comp-1","name":"Application Component","nodeTypeId":"nt-from-comp",
                         "attrs":"{\"customProperties\":[{\"id\":\"p1\",\"name\":\"owner\",\"type\":\"string\"}]}"}""",
                    MediaType.APPLICATION_JSON
                )
            )
        server.expect(requestTo("http://localhost/api/v1/components/comp-1"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withSuccess("""{"id":"comp-1"}""", MediaType.APPLICATION_JSON))

        // node type merge must target the OVERRIDE id, not the component's own node type
        server.expect(requestTo("http://localhost/api/v1/node-types/nt-override"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess("""{"id":"nt-override","name":"Custom Node Type","attrs":"{}"}""", MediaType.APPLICATION_JSON)
            )
        server.expect(requestTo("http://localhost/api/v1/node-types/nt-override"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withSuccess("""{"id":"nt-override"}""", MediaType.APPLICATION_JSON))

        val result = notation.ensureCustomProperties(
            componentId = "comp-1",
            nodeTypeId = "nt-override",
            propertiesJson = """[{"name":"severity","type":"enum","enumValues":["Low","High"]}]"""
        )
        val data = mapper.readTree(result).path("data")
        assertEquals("comp-1", data.path("component").path("id").asText())
        assertEquals("nt-override", data.path("nodeType").path("id").asText())
        server.verify()
    }

    @Test
    fun `neither componentId nor nodeTypeId is rejected`() {
        val result = notation.ensureCustomProperties(
            componentId = null,
            nodeTypeId = null,
            propertiesJson = """[{"name":"x","type":"string"}]"""
        )
        val parsed = mapper.readTree(result)
        assertEquals(false, parsed.path("ok").asBoolean())
        assertEquals("either componentId or nodeTypeId must be provided", parsed.path("message").asText())
    }
}
