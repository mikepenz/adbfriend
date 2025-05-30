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
import com.mikepenz.adbfriend.subcommands.mcp.utils.setupServer

class SseOptions : OptionGroup() {
    val sse by option().boolean().required()
    val port by option().int().default(3001)
}

class Server : AdbCommand() {
    val sseOptions by SseOptions().cooccurring()
    val tools by option().flag()
    val allowedPaths: List<String> by option(help = "List of allowed paths for file system operations").varargValues().default(DEFAULT_ALLOWED_PATHS)

    override val requireAdbServer = false
    override val failOnNoDevice = false
    override val enableLog = false

    override fun help(context: Context) = """
        Starts up a MCP server. The server provides tools for interacting with Android devices via ADB.
        File system operations are restricted to the provided paths. (By default only `/sdcard/Download/`)
    """.trimIndent()

    override suspend fun runWithAdb(devices: List<Device>) {
        val builtTools = buildTools(adb, devices, allowedPaths)

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
