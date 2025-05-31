package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.Feature
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.malinskiy.adam.request.sync.v2.PullFileRequest
import com.mikepenz.adbfriend.subcommands.mcp.exception.ToolException
import com.mikepenz.adbfriend.subcommands.mcp.utils.createTool
import com.mikepenz.adbfriend.subcommands.mcp.utils.inputOutputPath
import com.mikepenz.adbfriend.subcommands.mcp.utils.inputSerial
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File

fun createCaptureScreenshotTool(adb: AndroidDebugBridgeClient, hostAllowedPaths: List<String>? = null): RegisteredTool = createTool(
    name = "capture-screenshot",
    description = """
        Captures a screenshot from the Android device and saves it to the host system.
        The screenshot is first saved on the device at and then pulled to the host system, tot he provided path.
    """.trimIndent(),
    inputSchema = CAPTURE_SCREENSHOT_TOOL_INPUT
) {
    val serial = inputSerial
    val outputPath = inputOutputPath

    // Use provided host allowed paths or default if not provided
    val effectiveHostAllowedPaths = hostAllowedPaths ?: getDefaultHostAllowedPaths()

    // Create directories for host allowed paths if they don't exist
    effectiveHostAllowedPaths.forEach { path ->
        val directory = File(path)
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }

    // Verify output path is allowed if provided
    val isAllowed = effectiveHostAllowedPaths.any { allowedPath ->
        outputPath.startsWith(allowedPath)
    }

    if (!isAllowed) {
        throw ToolException("Output path is not within allowed host paths: $effectiveHostAllowedPaths")
    }

    // Ensure the directory exists
    val directory = File(outputPath).parentFile
    if (directory != null && !directory.exists()) {
        directory.mkdirs()
    }

    // Capture screenshot on the device
    val deviceScreenshotPath = "/sdcard/adbfriend-screenshot.png"
    val captureResult = adb.execute(
        request = ShellCommandRequest("screencap -p $deviceScreenshotPath"),
        serial = serial
    )

    if (captureResult.exitCode != 0) {
        throw ToolException("Failed to capture screenshot: ${captureResult.output}")
    }

    // Pull the screenshot from the device to the host
    val outputFile = File(outputPath)
    try {
        withContext(Dispatchers.IO) {
            outputFile.parentFile?.mkdirs()
            val pullChannel = adb.execute(
                request = PullFileRequest(
                    remotePath = deviceScreenshotPath,
                    local = outputFile,
                    supportedFeatures = listOf(Feature.LS_V2, Feature.STAT_V2, Feature.SENDRECV_V2)
                ),
                scope = CoroutineScope(Dispatchers.IO),
                serial = serial
            )

            @Suppress("ControlFlowWithEmptyBody")
            for (percentageDouble in pullChannel) {
                // wait until the file is fully pulled
            }

            // Clean up the temporary file on the device
            adb.execute(
                request = ShellCommandRequest("rm $deviceScreenshotPath"),
                serial = serial
            )
        }
    } catch (e: Exception) {
        throw ToolException("Failed to pull screenshot from device: ${e.message}")
    }

    // Return the result
    CallToolResult(
        content = listOf(TextContent(buildJsonObject {
            put("success", JsonPrimitive(true))
            put("screenshot_path", JsonPrimitive(outputPath))
        }.toString()))
    )
}
