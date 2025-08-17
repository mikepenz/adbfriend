package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.framebuffer.RawImageScreenCaptureAdapter
import com.malinskiy.adam.request.framebuffer.ScreenCaptureRequest
import com.mikepenz.adbfriend.subcommands.mcp.exception.ToolException
import com.mikepenz.adbfriend.subcommands.mcp.utils.*
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import javax.imageio.ImageIO

fun createCaptureScreenshotTool(adb: AndroidDebugBridgeClient, hostAllowedPaths: List<String>? = null): RegisteredTool = createTool(
    name = "capture-screenshot",
    description = """
        Captures a screenshot from the Android device, saves it temporarily, and then transfers it to the specified output path on the host system.
        Use with caution as this can overwrite existing files on the host system.
    """.trimIndent(),
    inputSchema = CAPTURE_SCREENSHOT_TOOL_INPUT,
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema(
                successDescription = "Whether the screenshot was captured successfully",
                messageDescription = "An optional status message informing about errors during execution"
            )
            put("screenshot_path", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The path where the screenshot was saved on the host system"))
            })
        },
        required = listOf("success")
    ),
    annotations = {
        copy(
            readOnlyHint = false,
            openWorldHint = false,
            destructiveHint = true,
        )
    }
) {
    val serial = inputSerial
    val outputPath = inputOutputPath

    // Use provided host allowed paths or default if not provided
    verifyHostPathAllowed(outputPath, hostAllowedPaths ?: getDefaultHostAllowedPaths())

    // Ensure the directory exists
    val directory = File(outputPath).parentFile
    if (directory != null && !directory.exists()) {
        directory.mkdirs()
    }

    // Capture screenshot on the device
    val adapter = RawImageScreenCaptureAdapter()
    val image = adb.execute(
        request = ScreenCaptureRequest(adapter),
        serial = serial
    ).toBufferedImage()

    val outputFile = File(outputPath)
    if (!ImageIO.write(image, "png", outputFile)) {
        throw ToolException("Failed to capture screenshot: $outputPath")
    }

    // Return the result
    buildJsonObject {
        put("success", JsonPrimitive(true))
        put("screenshot_path", JsonPrimitive(outputPath))
    }.asStructuredResponse()
}
