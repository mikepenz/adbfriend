package com.mikepenz.adbfriend.subcommands.mcp

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import com.malinskiy.adam.request.device.Device
import com.mikepenz.adbfriend.subcommands.AdbCommand
import com.mikepenz.adbfriend.subcommands.mcp.tools.DEFAULT_ALLOWED_PATHS
import com.mikepenz.adbfriend.subcommands.mcp.tools.buildTools
import com.mikepenz.adbfriend.subcommands.mcp.tools.getDefaultHostAllowedPaths
import com.mikepenz.adbfriend.subcommands.mcp.utils.setupServer

class SseOptions : OptionGroup() {
    val sse by option().boolean().required()
    val port by option().int().default(3001)
}

class Server : AdbCommand() {
    val sseOptions by SseOptions().cooccurring()
    val tools by option().flag()
    val allowedPaths: List<String> by option(help = "List of allowed paths for file system operations on the Android device", envvar = "adbfriend-allowed-paths").varargValues().default(DEFAULT_ALLOWED_PATHS)
    val hostAllowedPaths: List<String> by option(help = "List of allowed paths for file system operations on the host system", envvar = "adbfriend-host-allowed-paths").varargValues().default(getDefaultHostAllowedPaths())

    override val requireAdbServer = false
    override val failOnNoDevice = false
    override val enableLog = false

    override fun help(context: Context) = """
        Starts up a MCP server. The server provides tools for interacting with Android devices via ADB.
        File system operations on the Android device are restricted to the provided paths. (By default only `/sdcard/Download/`)
        File system operations on the host system are restricted to the provided host paths. (By default only `~/adbfriend/`)
    """.trimIndent()

    override suspend fun runWithAdb(devices: List<Device>) {
        val builtTools = buildTools(adb, devices, allowedPaths, hostAllowedPaths)

        if (tools) {
            echo()
            echo("Available MCP Tools:")
            echo()
            builtTools.forEach { echo("- ${it.tool.name} :: ${it.tool.description}") }
            return
        }

        setupServer(sseOptions, builtTools, ::echo)
    }
}
