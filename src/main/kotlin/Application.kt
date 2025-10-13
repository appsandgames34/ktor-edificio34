package com.appsandgames34

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import java.time.Duration

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15) as kotlin.time.Duration?
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    configureMonitoring()
    configureSerialization()
    configureDatabases()
    configureSecurity()
    configureHTTP()
    configureRouting()
}
