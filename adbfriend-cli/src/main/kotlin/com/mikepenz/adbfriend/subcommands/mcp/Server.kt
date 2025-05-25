package com.mikepenz.adbfriend.subcommands.mcp

import adbfriend_root.adbfriend_cli.BuildConfig
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import com.malinskiy.adam.request.device.Device
import com.mikepenz.adbfriend.subcommands.AdbCommand
import com.mikepenz.adbfriend.subcommands.mcp.tools.addCheckAdbSpeedTool
import com.mikepenz.adbfriend.subcommands.mcp.tools.addConnectedDevicesTool
import com.mikepenz.adbfriend.subcommands.mcp.tools.addGetInstalledPackagesTool
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.io.asSink
import kotlinx.io.buffered

class SseOptions : OptionGroup() {
    val sse by option().boolean().required()
    val port by option().int().default(3001)
}

class Server : AdbCommand() {
    val sseOptions by SseOptions().cooccurring()

    override val requireAdbServer = false
    override val failOnNoDevice = false
    override val enableLog = false

    override fun help(context: Context) = """
        Starts up a MCP server.
    """.trimIndent()

    override suspend fun runWithAdb(devices: List<Device>) {
        val server = configureServer(devices)
        val sseOptions = sseOptions
        if (sseOptions?.sse == true) {
            if (enableLog) echo("Starting SSE MCP server on port ${sseOptions.port}")
            val servers = ConcurrentMap<String, Server>()
            embeddedServer(CIO, host = "0.0.0.0", port = sseOptions.port) {
                install(SSE)
                routing {
                    sse("/sse") {
                        val transport = SseServerTransport("/message", this)
                        servers[transport.sessionId] = server
                        server.onClose {
                            servers.remove(transport.sessionId)
                        }
                        server.connect(transport)
                    }
                    post("/message") {
                        val sessionId: String = call.request.queryParameters["sessionId"]!!
                        val transport = servers[sessionId]?.transport as? SseServerTransport
                        if (transport == null) {
                            call.respond("Session not found", null)
                            return@post
                        }
                        transport.handlePostMessage(call)
                    }
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

    private fun configureServer(devices: List<Device>): Server {
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
        server.addConnectedDevicesTool(adb, devices)
        server.addGetInstalledPackagesTool(adb)
        server.addCheckAdbSpeedTool(adb)
        return server
    }
}