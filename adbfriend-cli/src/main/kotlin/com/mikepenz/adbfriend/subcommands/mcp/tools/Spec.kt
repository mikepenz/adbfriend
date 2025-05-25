package com.mikepenz.adbfriend.subcommands.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal val DEVICE_FILTER_TOOL_INPUT = Tool.Input(
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