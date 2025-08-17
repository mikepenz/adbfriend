package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.mikepenz.adbfriend.subcommands.mcp.utils.applyDefaultOutputSchema
import com.mikepenz.adbfriend.subcommands.mcp.utils.asStructuredResponse
import com.mikepenz.adbfriend.subcommands.mcp.utils.createTool
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.serialization.json.*


fun createInstalledPackageTools(adb: AndroidDebugBridgeClient) = buildList {
    addInstalledPackageTool(
        adb = adb,
        name = "clear-installed-package",
        description = """
            Clears the package data for the provided package names on the Android device for the provided serial.
            Use with caution as it will clear the applications data without warning.
        """.trimIndent(),
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
        description = """
            Sets the 'immersive-full' flag for the provided package names on the Android device for the provided serial.
            This is commonly done as preparation for executing tests on the android device, to prevent the immersive info dialog to be displayed.
        """.trimIndent(),
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
        description = """
            Forces the stop of the provided package names on the Android device for the provided serial.
            Use with caution as it will stop the application without warning.
        """.trimIndent(),
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
        description = """
            Uninstalls the provided package names on the Android device for the provided serial.
            Use with caution as it will uninstall the application without warning.
            If the 'keep-data' flag is set to true, associated data will not be removed, and only the application will be uninstalled.
        """.trimIndent(),
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
    block: suspend CallToolRequest.(adb: AndroidDebugBridgeClient, serial: String, packageNames: Array<String>) -> Map<String, Boolean>,
) {
    add(
        createTool(
            name = name,
            description = description,
            inputSchema = Tool.Input(
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
                },
                required = listOf(
                    "serial", "package-names"
                )
            ),
            outputSchema = Tool.Output(
                properties = buildJsonObject {
                    applyDefaultOutputSchema(
                        successDescription = "Whether the operation was executed successfully",
                        messageDescription = "An optional status message informing about errors during execution"
                    )
                    put("results", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("description", JsonPrimitive("Results of the operation for each package"))
                        put("items", buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put("properties", buildJsonObject {
                                put("packageName", buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put("description", JsonPrimitive("The android package name"))
                                })
                                put("successful", buildJsonObject {
                                    put("type", JsonPrimitive("boolean"))
                                    put("description", JsonPrimitive("Whether the operation was successful for this package"))
                                })
                            })
                        })
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
            val serial = arguments["serial"]?.jsonPrimitive?.content?.trim()
            val packageNames = arguments["package-names"]?.jsonArray

            if (serial.isNullOrBlank()) {
                "The 'serial' parameter is required.".asStructuredResponse()
            } else if (packageNames.isNullOrEmpty()) {
                "The 'package-names' parameter is required.".asStructuredResponse()
            } else {
                val results = block(adb, serial, packageNames.map { it.jsonPrimitive.content }.toTypedArray())

                val result = buildJsonObject {
                    put("success", JsonPrimitive(true))
                    put("results", buildJsonArray {
                        results.onEach { (packageName, result) ->
                            add(buildJsonObject {
                                put("packageName", JsonPrimitive(packageName))
                                put("successful", JsonPrimitive(result))
                            })
                        }
                    })
                }
                result.asStructuredResponse()
            }
        }
    )
}
