package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*


fun Server.addInstalledPackageTools(adb: AndroidDebugBridgeClient) {
    addInstalledPackageTool(
        adb = adb,
        name = "clear-installed-package",
        description = "The clear installed package endpoint clears an installed packages data for the provided packages names from the Android device for the provided serial.",
    ) { adb, serial, packageNames ->
        packageNames.associateWith { p ->
            adb.execute(
                request = ShellCommandRequest("pm clear $p"), serial = serial
            ).errorOutput.trim().takeIf { it.isNotBlank() }?.let {
                false
            } ?: true
        }
    }

    addInstalledPackageTool(
        adb = adb,
        name = "set-immersive-full-for-package",
        description = "The set immersive full for installed package endpoint sets the 'immersive-full' flag for the provided package names on the Android device for the provided serial.",
    ) { adb, serial, packageNames ->
        packageNames.associateWith { p ->
            adb.execute(
                request = ShellCommandRequest("settings put global policy_control immersive.full=$p"), serial = serial
            ).errorOutput.trim().takeIf { it.isNotBlank() }?.let {
                false
            } ?: true
        }
    }

    addInstalledPackageTool(
        adb = adb,
        name = "force-stop-process",
        description = "The force stop process endpoint force closes the process of the provided package names on the Android device for the provided serial.",
    ) { adb, serial, packageNames ->
        packageNames.associateWith { p ->
            adb.execute(
                request = ShellCommandRequest("am force-stop $p"), serial = serial
            ).errorOutput.trim().takeIf { it.isNotBlank() }?.let {
                false
            } ?: true
        }
    }
}


private fun Server.addInstalledPackageTool(
    adb: AndroidDebugBridgeClient,
    name: String,
    description: String,
    block: suspend (adb: AndroidDebugBridgeClient, serial: String, packageNames: Array<String>) -> Map<String, Boolean>
) {
    addTool(
        name = name,
        description = description,
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "serial" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The Android device serial string to filter the output list")
                        )
                    ),
                    "package-names" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("array"),
                            "description" to JsonPrimitive("The array of package names to clear the data for.")
                        )
                    ),
                )
            ),
            required = listOf(
                "serial", "package-names"
            )
        )
    ) { request ->
        val serial = request.arguments["serial"]?.jsonPrimitive?.content?.trim()
        val packageNames = request.arguments["package-names"]?.jsonArray

        if (serial.isNullOrBlank()) {
            return@addTool CallToolResult(
                content = listOf(TextContent("The 'serial' parameter is required."))
            )
        } else if (packageNames.isNullOrEmpty()) {
            return@addTool CallToolResult(
                content = listOf(TextContent("The 'package-names' parameter is required."))
            )
        }

        val results = block(adb, serial, packageNames.map { it.jsonPrimitive.content }.toTypedArray())

        val result = buildJsonObject {
            put("results", buildJsonArray {
                results.onEach { (packageName, result) ->
                    add(buildJsonObject {
                        put("packageName", JsonPrimitive(packageName))
                        put("successful", JsonPrimitive(result))
                    })
                }
            })
        }

        CallToolResult(
            content = listOf(TextContent(result.toString()))
        )
    }
}