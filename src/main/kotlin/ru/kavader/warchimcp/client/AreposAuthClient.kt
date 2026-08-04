package ru.kavader.warchimcp.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import ru.kavader.warchimcp.auth.ApiKeyContext
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class AreposAuthClient(
    private val restClient: RestClient
) {
    private val cache = ConcurrentHashMap<String, CachedToken>()

    fun accessToken(): String {
        val apiKey = ApiKeyContext.requireApiKey()
        val cached = cache[apiKey]
        if (cached != null && cached.expiresAt.isAfter(Instant.now().plusSeconds(30))) {
            return cached.accessToken
        }
        return exchange(apiKey)
    }

    fun invalidate() {
        runCatching { ApiKeyContext.requireApiKey() }.getOrNull()?.let { cache.remove(it) }
    }

    private fun exchange(apiKey: String): String {
        try {
            val response = restClient.post()
                .uri("/api/v1/auth/api-keys/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("apiKey" to apiKey))
                .retrieve()
                .body(ExchangeResponse::class.java)
                ?: throw AreposClientException(401, "Empty exchange response")

            cache[apiKey] = CachedToken(
                accessToken = response.accessToken,
                expiresAt = Instant.now().plusSeconds(response.expiresIn.coerceAtLeast(60))
            )
            return response.accessToken
        } catch (ex: RestClientResponseException) {
            throw AreposClientException(ex.statusCode.value(), safeMessage(ex.responseBodyAsString))
        }
    }

    private fun safeMessage(body: String): String =
        body.take(300).ifBlank { "API key exchange failed" }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ExchangeResponse(
        val accessToken: String,
        val expiresIn: Long
    )

    private data class CachedToken(
        val accessToken: String,
        val expiresAt: Instant
    )
}
