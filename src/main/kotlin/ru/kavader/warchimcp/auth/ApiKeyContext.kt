package ru.kavader.warchimcp.auth

/**
 * Holds the inbound API key (Authorization header value) for the current HTTP request.
 */
object ApiKeyContext {
    private val holder = ThreadLocal<String?>()

    fun set(authorizationHeader: String?) {
        holder.set(authorizationHeader)
    }

    fun get(): String? = holder.get()

    fun clear() {
        holder.remove()
    }

    fun requireApiKey(): String {
        val header = get()?.trim().orEmpty()
        val key = when {
            header.startsWith("Bearer ", ignoreCase = true) -> header.substring(7).trim()
            else -> header
        }
        require(key.startsWith("warchi_ak_") && key.length > 20) {
            "Missing or invalid API key. Pass Authorization: Bearer warchi_ak_…"
        }
        return key
    }
}
