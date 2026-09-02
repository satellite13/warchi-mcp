package ru.kavader.warchimcp.tools

import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

class EnsureWikiTest {

    private val mapper = ObjectMapper().findAndRegisterModules()
    private lateinit var server: MockRestServiceServer
    private lateinit var wiki: WikiTools

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl("http://localhost")
        server = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()
        val auth = AreposAuthClient(restClient)
        val api = AreposApiClient(restClient, auth, mapper)
        wiki = WikiTools(api, mapper)
        ApiKeyContext.set("Bearer warchi_ak_testkey0123456789abcdefghijklmnop")
    }

    @AfterEach
    fun tearDown() {
        ApiKeyContext.clear()
        server.reset()
    }

    @Test
    fun `ensure_wiki updates when attrs documentFileId exists`() {
        expectAuth()
        server.expect(requestTo("http://localhost/api/v1/diagrams/diag-1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"id":"diag-1","attrs":"{\"documentFileId\":\"file-9\"}"}""",
                    MediaType.APPLICATION_JSON
                )
            )
        server.expect(requestTo("http://localhost/api/v1/files/file-9/markdown"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withSuccess("""{"id":"file-9"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://localhost/api/v1/diagrams/diag-1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"id":"diag-1","attrs":"{\"documentFileId\":\"file-9\"}"}""",
                    MediaType.APPLICATION_JSON
                )
            )
        server.expect(requestTo("http://localhost/api/v1/diagrams/diag-1"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withSuccess("""{"id":"diag-1"}""", MediaType.APPLICATION_JSON))

        val raw = wiki.ensureWiki(
            entityKind = "diagram",
            entityId = "diag-1",
            content = "# Hello",
            modelId = "model-1"
        )
        val root = mapper.readTree(raw)
        assertTrue(root.path("ok").asBoolean())
        assertEquals("file-9", root.path("data").path("fileId").asText())
        assertFalse(root.path("data").path("created").asBoolean())
        assertTrue(root.path("data").path("updated").asBoolean())
        server.verify()
    }

    @Test
    fun `ensure_wiki creates when no existing wiki`() {
        expectAuth()
        server.expect(requestTo("http://localhost/api/v1/diagrams/diag-2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"id":"diag-2","attrs":"{}"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo(containsString("/api/v1/documents")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"items":[]}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://localhost/api/v1/files/upload-markdown"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"id":"file-new","filename":"diagram-diag-2.md"}""",
                    MediaType.APPLICATION_JSON
                )
            )
        server.expect(requestTo("http://localhost/api/v1/documents"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"id":"ref-1"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://localhost/api/v1/diagrams/diag-2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"id":"diag-2","attrs":"{}"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://localhost/api/v1/diagrams/diag-2"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withSuccess("""{"id":"diag-2"}""", MediaType.APPLICATION_JSON))

        val raw = wiki.ensureWiki(
            entityKind = "diagram",
            entityId = "diag-2",
            content = "# New",
            modelId = "model-1"
        )
        val root = mapper.readTree(raw)
        assertTrue(root.path("ok").asBoolean())
        assertEquals("file-new", root.path("data").path("fileId").asText())
        assertTrue(root.path("data").path("created").asBoolean())
        assertFalse(root.path("data").path("updated").asBoolean())
        server.verify()
    }

    @Test
    fun `ensure_wiki returns AMBIGUOUS_WIKI when multiple docs and no documentFileId`() {
        expectAuth()
        server.expect(requestTo("http://localhost/api/v1/diagrams/diag-3"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"id":"diag-3","attrs":{}}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo(containsString("/api/v1/documents")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"items":[{"fileId":"a"},{"fileId":"b"}]}""",
                    MediaType.APPLICATION_JSON
                )
            )

        val raw = wiki.ensureWiki(
            entityKind = "diagram",
            entityId = "diag-3",
            content = "# X"
        )
        val root = mapper.readTree(raw)
        assertFalse(root.path("ok").asBoolean())
        assertEquals("AMBIGUOUS_WIKI", root.path("code").asText())
        server.verify()
    }

    @Test
    fun `mergeDocumentFileId preserves object attrs and sets documentFileId`() {
        val merged = wiki.mergeDocumentFileId(
            mapper.readTree("""{"keep":true}"""),
            "file-1"
        )
        val root = mapper.readTree(merged)
        assertTrue(root.path("keep").asBoolean())
        assertEquals("file-1", root.path("documentFileId").asText())
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
}
