package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.mikepenz.adbfriend.utils.usbProtocolParser
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import com.mikepenz.adbfriend.subcommands.mcp.utils.createTool
import com.mikepenz.adbfriend.subcommands.mcp.utils.inputSerial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

fun createCheckAdbSpeedTool(): RegisteredTool = createTool(
    name = "check-adb-speed",
    description = """
        Checks the usb connection speed of the Android device for the provided serial.
    """.trimIndent(),
    inputSchema = DEVICE_FILTER_TOOL_INPUT
) {
    val serial = inputSerial

    val osName = System.getProperty("os.name")
    if (osName.contains("mac", true)) {
        val proc = withContext(Dispatchers.IO) {
            ProcessBuilder("system_profiler", "SPUSBDataType")
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start().also {
                    it.waitFor(60, TimeUnit.MINUTES)
                }
        }
        proc.errorReader().readText().trim().takeIf { it.isNotBlank() }?.let {
            CallToolResult(
                content = listOf(TextContent("Failed to retrieve `system_profiler SPUSBDataType` ($it)"))
            )
        }
        val usbInformation = usbProtocolParser(proc.inputReader().readText())

        val match = usbInformation.firstOrNull { it.serial.equals(serial, true) }
        val messagePrefix = serial
        if (match != null) {
            val speedMessage = if (match.speed.contains("Mb/s", true)) {
                "\uD83D\uDE82 ${match.speed}"
            } else {
                "\uD83D\uDE85\uD83D\uDCA8 ${match.speed}"
            }
            CallToolResult(
                content = listOf(TextContent("$messagePrefix connected with $speedMessage"))
            )
        } else {
            CallToolResult(
                content = listOf(TextContent("Failed to retrieve speed for $messagePrefix"))
            )
        }
    } else {
        CallToolResult(
            content = listOf(TextContent("This tool is only available on Mac OS X. Wrong system."))
        )
    }
}
