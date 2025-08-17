package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.mikepenz.adbfriend.subcommands.mcp.utils.applyDefaultOutputSchema
import com.mikepenz.adbfriend.subcommands.mcp.utils.asStructuredResponse
import com.mikepenz.adbfriend.subcommands.mcp.utils.createTool
import com.mikepenz.adbfriend.subcommands.mcp.utils.inputSerial
import com.mikepenz.adbfriend.utils.convertGlobToRegex
import com.mikepenz.adbfriend.utils.packageParser
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

fun createGetInstalledPackagesTool(adb: AndroidDebugBridgeClient): RegisteredTool = createTool(
    name = "get-installed-packages",
    description = """
        Retrieves the information of all installed packages on the Android device for the provided serial.
        For each installed package `packageName`, `version` and `dataDir` will be returned.
        The `packageName` is the common identifier used to access any other package specific tool.
        Use the `third-party-only` flag to filter out system applications and only show third-party applications.
    """.trimIndent(),
    inputSchema = DEVICE_FILTER_TOOL_INPUT,
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema(
                successDescription = "Whether the installed packages were retrieved successfully",
                messageDescription = "An optional status message informing about errors during execution"
            )
            put("packages", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("description", JsonPrimitive("List of installed packages on the Android device"))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("packageName", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The package name"))
                        })
                        put("version", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The package version"))
                        })
                        put("dataDir", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The package data directory"))
                        })
                    })
                })
            })
        },
        required = listOf("success")
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

    val packageGlob = arguments["package-filter"]?.jsonPrimitive?.content
    val thirdPartyOnly = arguments["third-party-only"]?.jsonPrimitive?.content?.toBoolean() ?: false
    val regex = if (packageGlob != null) convertGlobToRegex(packageGlob) else null

    // Get all packages using dumpsys
    val response = adb.execute(request = ShellCommandRequest("dumpsys package packages"), serial = serial)
    val packages = packageParser(response.output) { }

    // Apply filters
    var filtered = packages

    // Apply third-party only filter if enabled
    if (thirdPartyOnly) {
        // Use pm list packages -3 to get third-party packages
        val thirdPartyResponse = adb.execute(request = ShellCommandRequest("pm list packages -3"), serial = serial)
        val thirdPartyPackageNames = thirdPartyResponse.output
            .lineSequence()
            .filter { it.startsWith("package:") }
            .map { it.substring("package:".length) }
            .toSet()

        // Filter packages based on the list from pm list packages -3
        filtered = filtered.filter { thirdPartyPackageNames.contains(it.packageName) }
    }

    // Apply package name filter if provided
    if (regex != null) {
        filtered = filtered.filter { regex.matches(it.packageName) }
    }

    val result = buildJsonObject {
        put("success", JsonPrimitive(true))
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
    result.asStructuredResponse()
}
