package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.mikepenz.adbfriend.utils.convertGlobToRegex
import com.mikepenz.adbfriend.utils.packageParser
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

fun createGetInstalledPackagesTool(adb: AndroidDebugBridgeClient): RegisteredTool {
    return RegisteredTool(
        Tool(
            name = "get-installed-packages",
            description = """
                Retrieves the information of all installed packages on the Android device for the provided serial.
                For each installed package `packageName`, `version` and `dataDir` will be returned.
                The `packageName` is the common identifier used to access any other package specific tool.
            """.trimIndent(),
            inputSchema = DEVICE_FILTER_TOOL_INPUT
        )
    ) { request ->
        val serial = request.arguments["serial"]?.jsonPrimitive?.content
        if (serial == null) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("The 'serial' parameter is required."))
            )
        }

        val packageGlob = request.arguments["package-filter"]?.jsonPrimitive?.content
        val regex = if (packageGlob != null) convertGlobToRegex(packageGlob) else null
        val response = adb.execute(request = ShellCommandRequest("dumpsys package packages"), serial = serial)
        val packages = packageParser(response.output) { }
        val filtered = if (regex != null) packages.filter { regex.matches(it.packageName) } else packages

        val result = buildJsonObject {
            put("packages", buildJsonArray {
                filtered.onEach { pack ->
                    add(buildJsonObject {
                        put("packageName", JsonPrimitive(pack.packageName))
                        put("version", JsonPrimitive(pack.versionName))
                        put("dataDir", JsonPrimitive(pack.dataDir))
                    })
                }
            })
        }

        CallToolResult(
            content = listOf(TextContent(result.toString()))
        )
    }
}