package com.mikepenz.adbfriend.subcommands.mcp.utils

import adbfriend_root.adbfriend_cli.BuildConfig
import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.device.Device
import com.mikepenz.adbfriend.subcommands.mcp.SseOptions
import com.mikepenz.adbfriend.subcommands.mcp.tools.buildTools
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.Job
import kotlinx.io.asSink
import kotlinx.io.buffered

suspend fun AndroidDebugBridgeClient.setupServer(sseOptions: SseOptions?, devices: List<Device>, echo: (String) -> Unit, enableLog: Boolean = false) {
    val server = configureServer(devices)
    val sseOptions = sseOptions
    if (sseOptions?.sse == true) {
        if (enableLog) echo("Starting SSE MCP server on port ${sseOptions.port}")
        embeddedServer(CIO, host = "0.0.0.0", port = sseOptions.port) {
            mcp {
                return@mcp server
            }
        }.start(wait = true)
    } else {
        if (enableLog) echo("Starting STDIO MCP server")
        val transport = StdioServerTransport(
            System.`in`.asInput(),
            System.out.asSink().buffered()
        )

        server.connect(transport)
        val done = Job()
        server.onClose {
            done.complete()
        }
        done.join()
    }
}

private fun AndroidDebugBridgeClient.configureServer(devices: List<Device>): Server {
    val server = Server(
        Implementation(
            name = "mcp-kotlin AdbFriend server",
            version = BuildConfig.APP_VERSION
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = true),
                resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                tools = ServerCapabilities.Tools(listChanged = true)
            )
        )
    )
    server.addTools(buildTools(this, devices))
    return server
}