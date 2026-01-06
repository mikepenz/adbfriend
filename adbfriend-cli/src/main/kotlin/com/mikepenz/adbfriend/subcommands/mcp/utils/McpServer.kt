package com.mikepenz.adbfriend.subcommands.mcp.utils

import adbfriend_root.adbfriend_cli.BuildConfig
import com.mikepenz.adbfriend.subcommands.mcp.SseOptions
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Job
import kotlinx.io.asSink
import kotlinx.io.buffered

suspend fun setupServer(sseOptions: SseOptions?, builtTools: List<RegisteredTool>, echo: (String) -> Unit, enableLog: Boolean = false) {
    val server = configureServer(builtTools)
    if (sseOptions?.sse == true) {
        if (enableLog) echo("Starting SSE MCP server on port ${sseOptions.port}")
        embeddedServer(Netty, host = "0.0.0.0", port = sseOptions.port) {
            mcp {
                server
            }
        }.start(wait = true)
    } else {
        if (enableLog) echo("Starting STDIO MCP server")
        val transport = StdioServerTransport(
            System.`in`.asInput(),
            System.out.asSink().buffered()
        )

        server.createSession(transport)
        val done = Job()
        server.onClose {
            done.complete()
        }
        done.join()
    }
}

private fun configureServer(builtTools: List<RegisteredTool>): Server {
    val server = Server(
        Implementation(
            name = "mcp-kotlin AdbFriend server",
            version = BuildConfig.APP_VERSION
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
                logging = null
            )
        )
    )
    server.addTools(builtTools)
    return server
}