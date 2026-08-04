package ru.kavader.warchimcp.client

import org.springframework.boot.web.client.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import ru.kavader.warchimcp.config.WarchiMcpProperties
import java.net.http.HttpClient

@Configuration
class AreposRestClientConfig {

    @Bean
    fun restClient(builder: RestClient.Builder, properties: WarchiMcpProperties): RestClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(properties.readTimeout)
        }
        return builder
            .baseUrl(properties.areposBaseUrl.trimEnd('/'))
            .requestFactory(requestFactory)
            .build()
    }

    @Bean
    fun restClientCustomizer(): RestClientCustomizer = RestClientCustomizer { }
}
