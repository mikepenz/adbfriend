package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.mikepenz.adbfriend.utils.convertGlobToRegex
import com.mikepenz.adbfriend.utils.packageParser
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*

fun Server.addGetInstalledPackagesTool(adb: AndroidDebugBridgeClient) {
    addTool(
        name = "get-installed-packages",
        description = "The get installed packages endpoint returns a list of installed packages on the Android device for the provided serial.",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "serial" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The Android device serial string")
                        )
                    ),
                    "package-filter" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("An optional package name glob to filter the output list")
                        )
                    )
                )
            ),
            required = listOf("serial")
        )
    ) { request ->
        val serial = request.arguments["serial"]?.jsonPrimitive?.content
        if (serial == null) {
            return@addTool CallToolResult(
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