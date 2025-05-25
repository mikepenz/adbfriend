package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.device.Device
import com.mikepenz.adbfriend.extensions.fetchModel
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

fun Server.addConnectedDevicesTool(adb: AndroidDebugBridgeClient, devices: List<Device>) {
    addTool(
        name = "get-connected-devices",
        description = "The connected devices endpoint returns the list of connected android devices",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "serial" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The Android device serial string")
                        )
                    ),
                    "name" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The Android device model name")
                        )
                    ),
                )
            ),
            required = listOf()
        )
    ) {
        val result = buildJsonObject {
            put("devices", buildJsonArray {
                devices.onEach { device ->
                    add(buildJsonObject {
                        put("serial", JsonPrimitive(device.serial))
                        put("model", JsonPrimitive(adb.fetchModel(device) {}))
                        put("state", JsonPrimitive(device.state.name))
                    })
                }
            })
        }

        CallToolResult(
            content = listOf(TextContent(result.toString()))
        )
    }
}