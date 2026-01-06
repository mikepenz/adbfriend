package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.mikepenz.adbfriend.subcommands.mcp.utils.applyDefaultOutputSchema
import com.mikepenz.adbfriend.subcommands.mcp.utils.asStructuredResponse
import com.mikepenz.adbfriend.subcommands.mcp.utils.createTool
import com.mikepenz.adbfriend.subcommands.mcp.utils.inputSerial
import com.mikepenz.adbfriend.utils.usbProtocolParser
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import java.util.concurrent.TimeUnit

fun createCheckAdbSpeedTool(): RegisteredTool = createTool(
    name = "check-adb-speed",
    description = """
        Checks the usb connection speed of the Android device for the provided serial.
    """.trimIndent(),
    inputSchema = SERIAL_ONLY_TOOL_INPUT,
    outputSchema = ToolSchema(
        properties = buildJsonObject {
            applyDefaultOutputSchema(messageDescription = "A message containing the USB connection speed or an error message. In case of failure, the message will contain the error details.")
        },
        required = listOf("success", "message")
    ),
    annotations = {
        copy(
            readOnlyHint = true,
            openWorldHint = false,
            destructiveHint = false,
        )
    }
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
        if (match != null) {
            val speedMessage = if (match.speed.contains("Mb/s", true)) {
                "\uD83D\uDE82 ${match.speed}"
            } else {
                "\uD83D\uDE85\uD83D\uDCA8 ${match.speed}"
            }
            "$serial connected with $speedMessage".asStructuredResponse(successful = true)
        } else {
            "Failed to retrieve speed for $serial".asStructuredResponse()
        }
    } else {
        "This tool is only available on Mac OS X. Wrong system.".asStructuredResponse()
    }
}
