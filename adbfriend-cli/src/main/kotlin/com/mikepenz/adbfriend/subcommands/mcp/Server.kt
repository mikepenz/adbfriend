package com.mikepenz.adbfriend.subcommands.mcp

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
import com.mikepenz.adbfriend.subcommands.mcp.utils.setupServer

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
        adb.setupServer(sseOptions, devices, ::echo)
    }
}