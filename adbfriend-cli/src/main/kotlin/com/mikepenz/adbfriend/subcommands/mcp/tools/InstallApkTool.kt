package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.Feature
import com.malinskiy.adam.request.pkg.InstallRemotePackageRequest
import com.malinskiy.adam.request.sync.v2.PushFileRequest
import com.mikepenz.adbfriend.subcommands.mcp.exception.ToolException
import com.mikepenz.adbfriend.subcommands.mcp.utils.createTool
import com.mikepenz.adbfriend.subcommands.mcp.utils.inputApkPath
import com.mikepenz.adbfriend.subcommands.mcp.utils.inputSerial
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File

/**
 * Creates a tool for installing an APK on an Android device.
 * The APK file must be located on the host system within the allowed paths.
 */
fun createInstallApkTool(
    adb: AndroidDebugBridgeClient,
    hostAllowedPaths: List<String>? = null
): RegisteredTool = createTool(
    name = "install-apk",
    description = """
        Installs an APK on the Android device from a file on the host system.
        The APK file must be located within the allowed host paths.
        Use with caution as this can install potentially harmful applications.
    """.trimIndent(),
    inputSchema = INSTALL_APK_TOOL_INPUT
) {
    val serial = inputSerial
    val apkPath = inputApkPath

    // Use provided host allowed paths or default if not provided
    verifyHostPathAllowed(apkPath, hostAllowedPaths ?: getDefaultHostAllowedPaths())

    // Check if the APK file exists
    val apkFile = File(apkPath)
    if (!apkFile.exists() || !apkFile.isFile) {
        throw ToolException("APK file not found: $apkPath")
    }

    try {
        // Push the APK file to the device
        val channel = adb.execute(
            request = PushFileRequest(
                local = apkFile,
                remotePath = "/data/local/tmp/${apkFile.name}",
                supportedFeatures = listOf(Feature.LS_V2, Feature.STAT_V2, Feature.SENDRECV_V2)
            ),
            scope = CoroutineScope(Dispatchers.IO),
            serial = serial
        )

        for (installProgress in channel) {
            // wait until the file is fully pulled
        }

        val installResponse = adb.execute(
            request = InstallRemotePackageRequest(
                "/data/local/tmp/${apkFile.name}",
                true,
            ),
            serial = serial
        )

        // Check if installation was successful
        val success = installResponse.output.trim().contains("Success")

        if (success) {
            // Return success result
            CallToolResult(
                content = listOf(TextContent(buildJsonObject {
                    put("success", JsonPrimitive(true))
                    put("apk_path", JsonPrimitive(apkPath))
                    put("message", JsonPrimitive("APK installed successfully"))
                }.toString()))
            )
        } else {
            // Return error result
            CallToolResult(
                content = listOf(TextContent(buildJsonObject {
                    put("success", JsonPrimitive(false))
                    put("apk_path", JsonPrimitive(apkPath))
                    put("message", JsonPrimitive("Failed to install APK: ${installResponse.output.trim()}"))
                }.toString()))
            )
        }
    } catch (e: Exception) {
        CallToolResult(
            content = listOf(TextContent("Failed to install APK: ${e.message}"))
        )
    }
}
