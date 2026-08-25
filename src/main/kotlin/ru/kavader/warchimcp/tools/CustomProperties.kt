package ru.kavader.warchimcp.tools

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID

/**
 * Create-if-missing merge of `attrs.customProperties` (wArchi notation property schema).
 *
 * Properties are matched BY NAME: an existing definition with the same name is left
 * untouched (never mutated), a missing one is appended with canonical defaults filled in
 * (id generated, min/max -> null, ...). All other attrs fields are preserved as-is.
 *
 * Pure functions — no REST access — so the logic is unit-testable.
 */
object CustomProperties {

    val PROPERTY_TYPES = setOf("string", "number", "boolean", "enum")

    data class MergeResult(
        val attrs: String,
        val changed: Boolean,
        val added: List<String>,
        val existing: List<String>
    )

    /** Validates a property definition (JSON array) string and normalizes each item. */
    fun parseDefinitions(propertiesJson: String, mapper: ObjectMapper): List<ObjectNode> {
        val array: JsonNode = try {
            mapper.readTree(propertiesJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("propertiesJson is not valid JSON: ${e.message}", e)
        }
        if (!array.isArray) throw IllegalArgumentException("propertiesJson must be a JSON array of property definitions")
        if (array.isEmpty) throw IllegalArgumentException("propertiesJson must not be empty")
        val seen = linkedSetOf<String>()
        return array.withIndex().map { (i, node) ->
            if (!node.isObject) throw IllegalArgumentException("property definition #$i must be a JSON object")
            val name = node.path("name").asText(null)?.trim()
            if (name.isNullOrBlank()) throw IllegalArgumentException("property definition #$i is missing 'name'")
            if (!seen.add(name)) throw IllegalArgumentException("duplicate property name '$name'")
            normalize(node as ObjectNode, name, i, mapper)
        }
    }

    /** Merge [definitions] into the entity attrs (add-if-missing, matched by name). */
    fun merge(attrsRaw: String?, definitions: List<ObjectNode>, mapper: ObjectMapper): MergeResult {
        val root: ObjectNode = when {
            attrsRaw.isNullOrBlank() -> mapper.createObjectNode()
            else -> {
                val parsed: JsonNode = try {
                    mapper.readTree(attrsRaw)
                } catch (e: Exception) {
                    // attrs is jsonb in arepos and is always valid JSON; failing loudly
                    // here prevents wiping an unreadable attrs object.
                    throw IllegalArgumentException(
                        "current attrs is not a valid JSON object: ${attrsRaw.take(120)}", e
                    )
                }
                if (parsed !is ObjectNode) {
                    throw IllegalArgumentException("current attrs is not a JSON object: ${attrsRaw.take(120)}")
                }
                parsed
            }
        }
        val arrNode: ArrayNode =
            if (root.path("customProperties").isArray) root.get("customProperties") as ArrayNode
            else root.putArray("customProperties")

        val existingNames = linkedSetOf<String>()
        for (e in arrNode) {
            e.path("name").asText(null)?.takeIf { it.isNotBlank() }?.let(existingNames::add)
        }

        val added = mutableListOf<String>()
        val existing = mutableListOf<String>()
        for (def in definitions) {
            val name = def.get("name").asText()
            if (name in existingNames) existing += name
            else {
                arrNode.add(def)
                added += name
            }
        }
        return MergeResult(mapper.writeValueAsString(root), added.isNotEmpty(), added, existing)
    }

    private fun normalize(node: ObjectNode, name: String, index: Int, mapper: ObjectMapper): ObjectNode {
        val p = mapper.createObjectNode()
        p.put("id", node.path("id").asText(null)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString())
        p.put("name", name)
        val type = node.path("type").asText("string")
        if (type !in PROPERTY_TYPES) {
            throw IllegalArgumentException("property '$name': type must be one of $PROPERTY_TYPES")
        }
        p.put("type", type)
        p.put("required", node.path("required").asBoolean(false))
        p.put("system", node.path("system").asBoolean(false))
        p.put("regex", node.path("regex").asText(""))
        putNumberOrNull(p, "min", node.path("min"))
        putNumberOrNull(p, "max", node.path("max"))
        putIntOrNull(p, "maxLength", node.path("maxLength"))
        if (type == "enum") {
            val values = node.path("enumValues")
            if (!values.isArray || values.isEmpty) {
                throw IllegalArgumentException("property '$name' (type=enum) requires a non-empty 'enumValues' array")
            }
            val dest = p.putArray("enumValues")
            for (v in values) {
                if (!v.isTextual) throw IllegalArgumentException("property '$name': enumValues must be strings")
                dest.add(v.asText())
            }
        } else {
            p.putArray("enumValues")
        }
        copyIfPresent(p, node, "defaultValue")
        copyIfPresent(p, node, "enumDefault")
        copyIfPresent(p, node, "interactive")
        copyIfPresent(p, node, "interactiveKind")
        copyIfPresent(p, node, "interactiveIcon")
        return p
    }

    private fun putNumberOrNull(dest: ObjectNode, field: String, value: JsonNode) {
        when {
            value.isInt -> dest.put(field, value.asInt())
            value.isNumber -> dest.put(field, value.decimalValue())
            else -> dest.putNull(field)
        }
    }

    private fun putIntOrNull(dest: ObjectNode, field: String, value: JsonNode) {
        when {
            value.isInt && value.asInt() >= 0 -> dest.put(field, value.asInt())
            else -> dest.putNull(field)
        }
    }

    private fun copyIfPresent(dest: ObjectNode, node: ObjectNode, field: String) {
        node.path(field).takeIf { !it.isNull && !it.isMissingNode }?.let { dest.set<JsonNode>(field, it) }
    }
}
