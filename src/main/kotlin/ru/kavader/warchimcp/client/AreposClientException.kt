package ru.kavader.warchimcp.client

class AreposClientException(
    val status: Int,
    override val message: String,
    val body: String? = null
) : RuntimeException(message)
