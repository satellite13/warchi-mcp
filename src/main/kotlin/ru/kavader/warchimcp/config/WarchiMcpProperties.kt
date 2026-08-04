package ru.kavader.warchimcp.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "warchi.mcp")
data class WarchiMcpProperties(
    val areposBaseUrl: String = "http://localhost:8080",
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(60)
)
