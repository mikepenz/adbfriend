package com.mikepenz.adbfriend.subcommands.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

internal val SERIAL_INPUT = buildJsonObject {
    put("type", JsonPrimitive("string"))
    put("description", JsonPrimitive("The Android device serial string"))
}

internal val PACKAGE_FILTER_INPUT = buildJsonObject {
    put("type", JsonPrimitive("string"))
    put("description", JsonPrimitive("An optional package name glob to filter the output list"))
}

internal val PATH_INPUT = buildJsonObject {
    put("type", JsonPrimitive("string"))
    put("description", JsonPrimitive("The file path on the Android device"))
}

internal val DEVICE_FILTER_TOOL_INPUT = Tool.Input(
    properties = JsonObject(
        mapOf(
            "serial" to SERIAL_INPUT,
            "package-filter" to PACKAGE_FILTER_INPUT
        )
    ),
    required = listOf("serial")
)

// Input schema for file system operations
internal val FILE_SYSTEM_TOOL_INPUT = Tool.Input(
    properties = buildJsonObject {
        put("serial", SERIAL_INPUT)
        put("path", PATH_INPUT)
    },
    required = listOf("serial", "path")
)

// Input schema for file system operations with content
internal val FILE_SYSTEM_CONTENT_TOOL_INPUT = Tool.Input(
    properties = buildJsonObject {
        put("serial", SERIAL_INPUT)
        put("path", PATH_INPUT)
        put("content", buildJsonObject {
            put("type", JsonPrimitive("string"))
            put("description", JsonPrimitive("The content to write to the file"))
        })
    },
    required = listOf("serial", "path", "content")
)

// Input schema for file system operations with recursive option
internal val FILE_SYSTEM_RECURSIVE_TOOL_INPUT = Tool.Input(
    properties = buildJsonObject {
        put("serial", SERIAL_INPUT)
        put("path", PATH_INPUT)
        put("recursive", buildJsonObject {
            put("type", JsonPrimitive("boolean"))
            put("description", JsonPrimitive("Whether to perform the operation recursively"))
        })
    },
    required = listOf("serial", "path")
)

// Input schema for file search operations
internal val FILE_SYSTEM_SEARCH_TOOL_INPUT = Tool.Input(
    properties = buildJsonObject {
        put("serial", SERIAL_INPUT)
        put("path", PATH_INPUT)
        put("pattern", buildJsonObject {
            put("type", JsonPrimitive("string"))
            put("description", JsonPrimitive("The glob pattern to search for files (case insensitive)"))
        })
        put("recursive", buildJsonObject {
            put("type", JsonPrimitive("boolean"))
            put("description", JsonPrimitive("Whether to search recursively in subdirectories"))
        })
    },
    required = listOf("serial", "pattern")
)
