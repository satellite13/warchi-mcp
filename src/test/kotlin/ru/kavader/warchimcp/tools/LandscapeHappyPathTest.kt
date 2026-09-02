package ru.kavader.warchimcp.tools

import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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

/**
 * Contract test for the landscape happy-path recipe:
 * search_catalog → search_notation → ensure_node → ensure_link →
 * ensure_diagram → add_diagram_instances → ensure_wiki
 */
class LandscapeHappyPathTest {

    private val mapper = ObjectMapper().findAndRegisterModules()
    private lateinit var server: MockRestServiceServer
    private lateinit var search: SearchTools
    private lateinit var write: ModelWriteTools
    private lateinit var wiki: WikiTools

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl("http://localhost")
        server = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()
        val auth = AreposAuthClient(restClient)
        val api = AreposApiClient(restClient, auth, mapper)
        search = SearchTools(api)
        write = ModelWriteTools(api, mapper)
        wiki = WikiTools(api, mapper)
        ApiKeyContext.set("Bearer warchi_ak_testkey0123456789abcdefghijklmnop")
    }

    @AfterEach
    fun tearDown() {
        ApiKeyContext.clear()
        server.reset()
    }

    @Test
    fun `happy-path recipe calls arepos ensure endpoints in order`() {
        expectAuth()

        server.expect(requestTo(containsString("/api/v1/search/catalog")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"items":[
                      {"kind":"model","id":"model-1","name":"Payments"},
                      {"kind":"notation","id":"not-1","name":"ArchiMate"}
                    ]}""",
                    MediaType.APPLICATION_JSON
                )
            )

        server.expect(requestTo(containsString("/api/v1/search/notations/not-1")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"items":[{"kind":"component","id":"comp-1","name":"Application Component"}]}""",
                    MediaType.APPLICATION_JSON
                )
            )

        server.expect(requestTo("http://localhost/api/v1/nodes/ensure"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"node":{"id":"node-a","name":"A"},"created":true}""",
                    MediaType.APPLICATION_JSON
                )
            )
        server.expect(requestTo("http://localhost/api/v1/nodes/ensure"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"node":{"id":"node-b","name":"B"},"created":true}""",
                    MediaType.APPLICATION_JSON
                )
            )

        server.expect(requestTo(containsString("/api/v1/search/notations/not-1")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"items":[{"kind":"relation","id":"rel-1","name":"Serving"}]}""",
                    MediaType.APPLICATION_JSON
                )
            )

        server.expect(requestTo("http://localhost/api/v1/links/ensure"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"link":{"id":"link-1"},"created":true}""",
                    MediaType.APPLICATION_JSON
                )
            )

        server.expect(requestTo("http://localhost/api/v1/diagrams/ensure"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"diagram":{"id":"diag-1","updatedAt":"2026-09-02T00:00:00Z"},"created":true}""",
                    MediaType.APPLICATION_JSON
                )
            )

        server.expect(requestTo("http://localhost/api/v1/diagrams/diag-1/instances:merge"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"id":"diag-1","updatedAt":"2026-09-02T00:01:00Z"}""",
                    MediaType.APPLICATION_JSON
                )
            )

        server.expect(requestTo("http://localhost/api/v1/diagrams/diag-1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"id":"diag-1","attrs":{}}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo(containsString("/api/v1/documents")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"items":[]}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://localhost/api/v1/files/upload-markdown"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"id":"file-1","filename":"d.md"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://localhost/api/v1/documents"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"id":"ref-1"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://localhost/api/v1/diagrams/diag-1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"id":"diag-1","attrs":{}}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://localhost/api/v1/diagrams/diag-1"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withSuccess("""{"id":"diag-1"}""", MediaType.APPLICATION_JSON))

        assertTrue(mapper.readTree(search.searchCatalog("Payments", "models,notations", 20)).path("ok").asBoolean())
        assertTrue(
            mapper.readTree(search.searchNotation("not-1", "Application Component", "components", null))
                .path("ok").asBoolean()
        )

        val nodeA = mapper.readTree(
            write.ensureNode(
                modelId = "model-1",
                name = "A",
                notationId = "not-1",
                componentName = "Application Component"
            )
        )
        assertEquals("node-a", nodeA.path("data").path("node").path("id").asText())

        val nodeB = mapper.readTree(
            write.ensureNode(
                modelId = "model-1",
                name = "B",
                notationId = "not-1",
                componentName = "Application Component"
            )
        )
        assertEquals("node-b", nodeB.path("data").path("node").path("id").asText())

        assertTrue(
            mapper.readTree(search.searchNotation("not-1", "Serving", "relations", null)).path("ok").asBoolean()
        )

        val link = mapper.readTree(
            write.ensureLink(
                modelId = "model-1",
                sourceId = "node-a",
                targetId = "node-b",
                notationId = "not-1",
                relationName = "Serving"
            )
        )
        assertEquals("link-1", link.path("data").path("link").path("id").asText())

        val diagram = mapper.readTree(
            write.ensureDiagram(
                modelId = "model-1",
                name = "Payments Landscape",
                notationId = "not-1"
            )
        )
        assertEquals("diag-1", diagram.path("data").path("diagram").path("id").asText())

        assertTrue(
            mapper.readTree(
                write.addDiagramInstances(
                    diagramId = "diag-1",
                    nodesJson = """[{"modelNodeId":"node-a","x":0,"y":0},{"modelNodeId":"node-b","x":200,"y":0}]""",
                    edgesJson = """[{"modelLinkId":"link-1"}]""",
                    baseUpdatedAt = "2026-09-02T00:00:00Z"
                )
            ).path("ok").asBoolean()
        )

        val wikiResult = mapper.readTree(
            wiki.ensureWiki(
                entityKind = "diagram",
                entityId = "diag-1",
                content = "# Payments Landscape",
                modelId = "model-1"
            )
        )
        assertTrue(wikiResult.path("ok").asBoolean())
        assertTrue(wikiResult.path("data").path("created").asBoolean())
        assertEquals("file-1", wikiResult.path("data").path("fileId").asText())

        server.verify()
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
