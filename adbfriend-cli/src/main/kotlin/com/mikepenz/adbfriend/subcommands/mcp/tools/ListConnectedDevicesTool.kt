package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.device.Device
import com.mikepenz.adbfriend.extensions.fetchModel
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import com.mikepenz.adbfriend.subcommands.mcp.utils.createTool
import kotlinx.serialization.json.*

fun createConnectedDevicesTool(adb: AndroidDebugBridgeClient, devices: List<Device>): RegisteredTool = createTool(
    name = "get-connected-devices",
    description = """
        Retrieves the information of all android devices connected.
        For each device `serial`, `model` and `state` will be returned.
        The `serial` is the common identifier used to access any other device specific tool.
    """.trimIndent(),
    inputSchema = Tool.Input(
        properties = JsonObject(
            mapOf(
                "serial" to JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("The Android device serial string to filter the output list")
                    )
                ),
                "name" to JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("The Android device model name to filter the output list")
                    )
                ),
            )
        ),
        required = listOf()
    ),
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            put("devices", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("description", JsonPrimitive("List of connected Android devices"))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("serial", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The device serial number"))
                        })
                        put("model", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The device model name"))
                        })
                        put("state", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The device state (e.g., ONLINE, OFFLINE)"))
                        })
                    })
                })
            })
        }
    ),
    annotations = { 
        // Add additional metadata about the tool
        this
    }
) {
    val serial = arguments["serial"]?.jsonPrimitive?.content?.trim()
    val name = arguments["name"]?.jsonPrimitive?.content?.trim()

    val result = buildJsonObject {
        put("devices", buildJsonArray {
            devices.onEach { device ->
                if (serial.isNullOrBlank() || serial.equals(device.serial, true)) {
                    val model = adb.fetchModel(device) {}

                    if (name.isNullOrBlank() || model.equals(name, true)) {
                        add(buildJsonObject {
                            put("serial", JsonPrimitive(device.serial))
                            put("model", JsonPrimitive(model))
                            put("state", JsonPrimitive(device.state.name))
                        })
                    }
                }
            }
        })
    }

    CallToolResult(
        structuredContent = result,
        content = listOf()
    )
}
