package ru.kavader.warchimcp.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponentsBuilder

@Component
class AreposApiClient(
    private val restClient: RestClient,
    private val authClient: AreposAuthClient,
    private val objectMapper: ObjectMapper
) {

    fun getJson(path: String, query: Map<String, Any?> = emptyMap()): JsonNode =
        exchange("GET", path, query = query)

    fun postJson(path: String, body: Any?): JsonNode =
        exchange("POST", path, body = body)

    fun putJson(path: String, body: Any?): JsonNode =
        exchange("PUT", path, body = body)

    fun deleteJson(path: String): JsonNode =
        exchange("DELETE", path)

    fun getText(path: String, query: Map<String, Any?> = emptyMap()): String {
        val uri = buildUri(path, query)
        return withAuthRetry { token ->
            restClient.get()
                .uri(uri)
                .header("Authorization", "Bearer $token")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String::class.java)
                ?: ""
        }
    }

    /** Download file/markdown body (bytes decoded as UTF-8). */
    fun getRawText(path: String, query: Map<String, Any?> = emptyMap()): String {
        val uri = buildUri(path, query)
        return withAuthRetry { token ->
            val bytes = restClient.get()
                .uri(uri)
                .header("Authorization", "Bearer $token")
                .accept(MediaType.ALL)
                .retrieve()
                .body(ByteArray::class.java)
                ?: ByteArray(0)
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun exchange(
        method: String,
        path: String,
        query: Map<String, Any?> = emptyMap(),
        body: Any? = null
    ): JsonNode {
        val uri = buildUri(path, query)
        val raw = withAuthRetry { token ->
            val spec = when (method) {
                "GET" -> restClient.get().uri(uri)
                "DELETE" -> restClient.delete().uri(uri)
                "POST" -> restClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body ?: emptyMap<String, Any>())
                "PUT" -> restClient.put().uri(uri).contentType(MediaType.APPLICATION_JSON).body(body ?: emptyMap<String, Any>())
                else -> throw IllegalArgumentException("Unsupported method $method")
            }
            spec.header("Authorization", "Bearer $token")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String::class.java)
                ?: "{}"
        }
        return objectMapper.readTree(raw)
    }

    private fun <T> withAuthRetry(block: (String) -> T): T {
        return try {
            block(authClient.accessToken())
        } catch (ex: RestClientResponseException) {
            if (ex.statusCode.value() == 401) {
                authClient.invalidate()
                try {
                    block(authClient.accessToken())
                } catch (retryEx: RestClientResponseException) {
                    throw toClientException(retryEx)
                }
            } else {
                throw toClientException(ex)
            }
        }
    }

    private fun toClientException(ex: RestClientResponseException): AreposClientException {
        val body = ex.responseBodyAsString
        val message = try {
            val node = objectMapper.readTree(body)
            sequenceOf("message", "error", "detail")
                .mapNotNull { node.path(it).asText(null)?.takeIf(String::isNotBlank) }
                .firstOrNull()
                ?: body.take(400).ifBlank { ex.message ?: "Arepos request failed" }
        } catch (_: Exception) {
            body.take(400).ifBlank { ex.message ?: "Arepos request failed" }
        }
        return AreposClientException(ex.statusCode.value(), message, body)
    }

    private fun buildUri(path: String, query: Map<String, Any?>): String {
        val builder = UriComponentsBuilder.fromPath(path)
        query.forEach { (key, value) ->
            if (value != null) {
                builder.queryParam(key, value)
            }
        }
        return builder.build(true).toUriString()
    }

}
