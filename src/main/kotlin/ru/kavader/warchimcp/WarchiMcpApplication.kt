package ru.kavader.warchimcp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class WarchiMcpApplication

fun main(args: Array<String>) {
    runApplication<WarchiMcpApplication>(*args)
}
