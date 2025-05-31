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

internal val OUTPUT_PATH_INPUT = buildJsonObject {
    put("type", JsonPrimitive("string"))
    put("description", JsonPrimitive("The path on the host system where the file should be saved. If not provided, a default path will be used."))
}


internal val DEVICE_FILTER_TOOL_INPUT = Tool.Input(
    properties = JsonObject(
        mapOf(
            "serial" to SERIAL_INPUT,
            "package-filter" to PACKAGE_FILTER_INPUT,
            "third-party-only" to buildJsonObject {
                put("type", JsonPrimitive("boolean"))
                put("description", JsonPrimitive("If true, only third-party applications will be included in the results"))
                put("default", JsonPrimitive(true))
            }
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

// Input schema for file move operations
internal val FILE_SYSTEM_MOVE_TOOL_INPUT = Tool.Input(
    properties = buildJsonObject {
        put("serial", SERIAL_INPUT)
        put("source", buildJsonObject {
            put("type", JsonPrimitive("string"))
            put("description", JsonPrimitive("The source file path on the Android device"))
        })
        put("destination", buildJsonObject {
            put("type", JsonPrimitive("string"))
            put("description", JsonPrimitive("The destination file path on the Android device"))
        })
    },
    required = listOf("serial", "source", "destination")
)

// Input schema for reading multiple files
internal val FILE_SYSTEM_MULTIPLE_FILES_TOOL_INPUT = Tool.Input(
    properties = buildJsonObject {
        put("serial", SERIAL_INPUT)
        put("paths", buildJsonObject {
            put("type", JsonPrimitive("array"))
            put("description", JsonPrimitive("The list of file paths on the Android device to read"))
            put("items", buildJsonObject {
                put("type", JsonPrimitive("string"))
            })
        })
    },
    required = listOf("serial", "paths")
)

// Input schema for copying a file from device to host
internal val FILE_SYSTEM_COPY_TO_HOST_TOOL_INPUT = Tool.Input(
    properties = buildJsonObject {
        put("serial", SERIAL_INPUT)
        put("path", PATH_INPUT)
        put("output-path", OUTPUT_PATH_INPUT)
    },
    required = listOf("serial", "path", "output-path")
)

internal val CAPTURE_SCREENSHOT_TOOL_INPUT = Tool.Input(
    properties = buildJsonObject {
        put("serial", SERIAL_INPUT)
        put("output-path", OUTPUT_PATH_INPUT)
    },
    required = listOf("serial", "output-path")
)
