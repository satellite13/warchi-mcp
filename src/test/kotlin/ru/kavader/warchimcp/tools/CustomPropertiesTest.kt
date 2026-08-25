package ru.kavader.warchimcp.tools

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CustomPropertiesTest {

    private val mapper = ObjectMapper().findAndRegisterModules()

    private fun definitions(vararg items: String): List<com.fasterxml.jackson.databind.node.ObjectNode> =
        CustomProperties.parseDefinitions("[${items.joinToString(",") }]", mapper)

    @Test
    fun `parseDefinitions normalizes with defaults`() {
        val defs = CustomProperties.parseDefinitions(
            """[{"name":"owner","type":"string","maxLength":40},{"name":"severity","type":"enum","enumValues":["Low","High"]}]""",
            mapper
        )
        assertEquals(2, defs.size)
        val owner = defs[0]
        assertEquals("owner", owner.path("name").asText())
        assertEquals("string", owner.path("type").asText())
        assertEquals(40, owner.path("maxLength").asInt())
        assertFalse(owner.path("required").asBoolean())
        assertFalse(owner.path("system").asBoolean())
        assertEquals("", owner.path("regex").asText())
        assertTrue(owner.path("id").asText().isNotBlank())
        assertTrue(owner.path("min").isNull)
        assertTrue(owner.path("max").isNull)
        assertTrue(owner.path("enumValues").isArray)
        assertEquals(0, owner.path("enumValues").size())

        val severity = defs[1]
        assertEquals("enum", severity.path("type").asText())
        assertEquals(2, severity.path("enumValues").size())
        assertEquals("Low", severity.path("enumValues").get(0).asText())
    }

    @Test
    fun `parseDefinitions keeps explicit id and passes through optional fields`() {
        val defs = CustomProperties.parseDefinitions(
            """[{"id":"fixed-id","name":"label","type":"string","required":true,"system":true,"regex":"^[\\W]+$","interactive":true,"interactiveKind":"url","interactiveIcon":"link","defaultValue":"x"}]""",
            mapper
        )
        val p = defs[0]
        assertEquals("fixed-id", p.path("id").asText())
        assertTrue(p.path("required").asBoolean())
        assertTrue(p.path("system").asBoolean())
        assertEquals("^[\\W]+$", p.path("regex").asText())
        assertTrue(p.path("interactive").asBoolean())
        assertEquals("url", p.path("interactiveKind").asText())
        assertEquals("link", p.path("interactiveIcon").asText())
        assertEquals("x", p.path("defaultValue").asText())
    }

    @Test
    fun `parseDefinitions validates input`() {
        var ex = assertThrows<IllegalArgumentException> {
            CustomProperties.parseDefinitions("nope", mapper)
        }
        assertTrue(ex.message!!.contains("not valid JSON"))
        ex = assertThrows<IllegalArgumentException> {
            CustomProperties.parseDefinitions("""{"name":"a","type":"string"}""", mapper)
        }
        assertTrue(ex.message!!.contains("JSON array"))
        ex = assertThrows<IllegalArgumentException> {
            CustomProperties.parseDefinitions("[]", mapper)
        }
        assertTrue(ex.message!!.contains("empty"))
        ex = assertThrows<IllegalArgumentException> {
            CustomProperties.parseDefinitions("""[{"type":"string"}]""", mapper)
        }
        assertTrue(ex.message!!.contains("missing 'name'"))
        ex = assertThrows<IllegalArgumentException> {
            CustomProperties.parseDefinitions("""[{"name":"a","type":"int"}]""", mapper)
        }
        assertTrue(ex.message!!.contains("type must be one of"))
        ex = assertThrows<IllegalArgumentException> {
            CustomProperties.parseDefinitions("""[{"name":"a","type":"enum"}]""", mapper)
        }
        assertTrue(ex.message!!.contains("enumValues"))
        ex = assertThrows<IllegalArgumentException> {
            CustomProperties.parseDefinitions("""[{"name":"a","type":"enum","enumValues":[]}]""", mapper)
        }
        assertTrue(ex.message!!.contains("enumValues"))
        ex = assertThrows<IllegalArgumentException> {
            CustomProperties.parseDefinitions("""[{"name":"a","type":"string"},{"name":"a","type":"string"}]""", mapper)
        }
        assertTrue(ex.message!!.contains("duplicate"))
        assertThrows<IllegalArgumentException> {
            CustomProperties.parseDefinitions("""[1]""", mapper)
        }
    }

    @Test
    fun `merge adds missing definitions and preserves other attrs`() {
        val current = """{"tags":["x"],"diagramStyle":{"width":180},"customProperties":[{"id":"1","name":"label","type":"string"}]}"""
        val result = CustomProperties.merge(current, definitions("""{"name":"owner","type":"string","maxLength":40}"""), mapper)
        assertTrue(result.changed)
        assertEquals(listOf("owner"), result.added)
        assertEquals(emptyList<String>(), result.existing)

        val root = mapper.readTree(result.attrs)
        assertEquals("x", root.path("tags").get(0).asText())
        assertEquals(180, root.path("diagramStyle").path("width").asInt())
        val cp = root.path("customProperties")
        assertEquals(2, cp.size())
        assertEquals("label", cp.get(0).path("name").asText())
        assertEquals("1", cp.get(0).path("id").asText())
        assertEquals("owner", cp.get(1).path("name").asText())
        assertEquals(40, cp.get(1).path("maxLength").asInt())
    }

    @Test
    fun `merge is a no-op when all definitions exist`() {
        val current = """{"customProperties":[{"id":"1","name":"owner","type":"string","maxLength":99}]}"""
        val result = CustomProperties.merge(
            current, definitions("""{"name":"owner","type":"string","maxLength":40}"""), mapper
        )
        assertFalse(result.changed)
        assertEquals(emptyList<String>(), result.added)
        assertEquals(listOf("owner"), result.existing)
        assertEquals(mapper.readTree(current), mapper.readTree(result.attrs))
    }

    @Test
    fun `merge starts from an empty object when attrs is null`() {
        val result = CustomProperties.merge(null, definitions("""{"name":"tag","type":"string"}"""), mapper)
        assertTrue(result.changed)
        val root = mapper.readTree(result.attrs)
        assertEquals("tag", root.path("customProperties").get(0).path("name").asText())
    }

    @Test
    fun `merge creates customProperties array when absent`() {
        val result = CustomProperties.merge(
            """{"documentFileId":"abc"}""",
            definitions("""{"name":"code","type":"string"}"""), mapper
        )
        assertTrue(result.changed)
        val root = mapper.readTree(result.attrs)
        assertEquals("abc", root.path("documentFileId").asText())
        assertEquals("code", root.path("customProperties").get(0).path("name").asText())
    }

    @Test
    fun `merge refuses unreadable attrs instead of wiping it`() {
        val ex = assertThrows<IllegalArgumentException> {
            CustomProperties.merge("not json", definitions("""{"name":"a","type":"string"}"""), mapper)
        }
        assertTrue(ex.message!!.contains("not a valid JSON"))
        assertThrows<IllegalArgumentException> {
            CustomProperties.merge("""[1,2]""", definitions("""{"name":"a","type":"string"}"""), mapper)
        }
    }
}
