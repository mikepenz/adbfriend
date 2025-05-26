package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.serialization.json.*


fun createInstalledPackageTools(adb: AndroidDebugBridgeClient) = buildList {
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

    addInstalledPackageTool(
        adb = adb,
        name = "uninstall-package",
        extraProperties = mapOf(
            "keep-data" to buildJsonObject {
                put("type", JsonPrimitive("boolean"))
                put("description", JsonPrimitive("Flag to keep the data when uninstalling the package. By default will also remove data."))
            },
        ),
        description = "The uninstall package endpoint uninstalls the provided package names on the Android device for the provided serial, optionally keeping app data.",
    ) { adb, serial, packageNames ->
        val keepData = arguments["keep-data"]?.jsonPrimitive?.booleanOrNull ?: false

        packageNames.associateWith { p ->
            adb.execute(
                request = ShellCommandRequest(
                    StringBuilder().apply {
                        append("pm uninstall ")
                        if (keepData) {
                            append("-k ")
                        }
                        append(p)
                    }.toString()
                ), serial = serial
            ).errorOutput.trim().takeIf { it.isNotBlank() }?.let {
                false
            } ?: true
        }
    }
}


private fun MutableList<RegisteredTool>.addInstalledPackageTool(
    adb: AndroidDebugBridgeClient,
    name: String,
    description: String,
    extraProperties: Map<String, JsonObject> = emptyMap(),
    block: suspend CallToolRequest.(adb: AndroidDebugBridgeClient, serial: String, packageNames: Array<String>) -> Map<String, Boolean>
) {
    add(
        RegisteredTool(
            Tool(
                name = name, description = description, inputSchema = Tool.Input(
                    properties = buildJsonObject {
                        extraProperties.onEach { put(it.key, it.value) }
                        put("serial", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The Android device serial string to filter the output list"))
                        })
                        put("package-names", buildJsonObject {
                            put("type", JsonPrimitive("array"))
                            put("items", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                            })
                            put("description", JsonPrimitive("The array of package names to clear the data for."))
                        })
                    }, required = listOf(
                        "serial", "package-names"
                    )
                )
            )
        ) { request ->
            val serial = request.arguments["serial"]?.jsonPrimitive?.content?.trim()
            val packageNames = request.arguments["package-names"]?.jsonArray

            if (serial.isNullOrBlank()) {
                return@RegisteredTool CallToolResult(
                    content = listOf(TextContent("The 'serial' parameter is required."))
                )
            } else if (packageNames.isNullOrEmpty()) {
                return@RegisteredTool CallToolResult(
                    content = listOf(TextContent("The 'package-names' parameter is required."))
                )
            }

            val results = request.block(adb, serial, packageNames.map { it.jsonPrimitive.content }.toTypedArray())

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
    )
}