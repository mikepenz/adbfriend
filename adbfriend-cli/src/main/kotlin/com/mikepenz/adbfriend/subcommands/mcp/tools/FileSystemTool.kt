@file:OptIn(ExperimentalSerializationApi::class)

package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.Feature
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.malinskiy.adam.request.sync.v2.ListFileRequest
import com.malinskiy.adam.request.sync.v2.PushFileRequest
import com.mikepenz.adbfriend.extensions.escapeForSync
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import java.nio.file.Files

// Define allowed paths for security
private val DEFAULT_ALLOWED_PATHS = listOf(
    "/sdcard/",
    "/storage/emulated/0/",
    "/data/local/tmp/"
)

/**
 * Checks if a path is allowed based on the provided allowed paths list.
 * A path is allowed if it starts with any of the allowed paths.
 */
private fun isPathAllowed(path: String, allowedPaths: List<String>): Boolean {
    return allowedPaths.any { allowedPath -> path.startsWith(allowedPath) }
}

/**
 * Creates a tool for listing files and directories on an Android device.
 */
private fun createListFilesTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>
): RegisteredTool {
    return RegisteredTool(
        Tool(
            name = "list-files",
            description = """
                Lists files and directories at the specified path on the Android device.
                Returns information about each file/directory including name, size, type, and last modified time.
            """.trimIndent(),
            inputSchema = FILE_SYSTEM_TOOL_INPUT
        )
    ) { request ->
        val serial = request.arguments["serial"]?.jsonPrimitive?.content
        val path = request.arguments["path"]?.jsonPrimitive?.content

        if (serial == null) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("The 'serial' parameter is required."))
            )
        }
        if (path == null) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("The 'path' parameter is required."))
            )
        }

        // Check if path is allowed
        if (!isPathAllowed(path, allowedPaths)) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("Access to path '$path' is not allowed for security reasons. Allowed paths: ${allowedPaths.joinToString()}"))
            )
        }

        try {
            val files = adb.execute(
                ListFileRequest(path, listOf(Feature.LS_V2, Feature.STAT_V2)),
                serial = serial
            )

            val result = buildJsonObject {
                put("path", JsonPrimitive(path))
                put("files", buildJsonArray {
                    files.forEach { file ->
                        if (file.name != null) {
                            add(buildJsonObject {
                                put("name", JsonPrimitive(file.name))
                                put("size", JsonPrimitive(file.size))
                                put("isDirectory", JsonPrimitive(file.isDirectory()))
                            })
                        }
                    }
                })
            }

            CallToolResult(
                content = listOf(TextContent(result.toString()))
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Failed to list files: ${e.message}"))
            )
        }
    }
}

/**
 * Creates a tool for reading file contents on an Android device.
 */
private fun createReadFileTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>
): RegisteredTool {
    return RegisteredTool(
        Tool(
            name = "read-file",
            description = """
                Reads the contents of a file on the Android device.
                Returns the file content as text.
            """.trimIndent(),
            inputSchema = FILE_SYSTEM_TOOL_INPUT
        )
    ) { request ->
        val serial = request.arguments["serial"]?.jsonPrimitive?.content
        val path = request.arguments["path"]?.jsonPrimitive?.content

        if (serial == null) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("The 'serial' parameter is required."))
            )
        }
        if (path == null) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("The 'path' parameter is required."))
            )
        }

        // Check if path is allowed
        if (!isPathAllowed(path, allowedPaths)) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("Access to path '$path' is not allowed for security reasons. Allowed paths: ${allowedPaths.joinToString()}"))
            )
        }

        try {
            // Use cat command to read file contents
            val response = adb.execute(
                request = ShellCommandRequest("cat \"${path.escapeForSync()}\""),
                serial = serial
            )

            if (response.errorOutput.isNotBlank()) {
                CallToolResult(
                    content = listOf(TextContent("Failed to read file: ${response.errorOutput}"))
                )
            } else {
                val result = buildJsonObject {
                    put("path", JsonPrimitive(path))
                    put("content", JsonPrimitive(response.output))
                }

                CallToolResult(
                    content = listOf(TextContent(result.toString()))
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Failed to read file: ${e.message}"))
            )
        }
    }
}

/**
 * Creates a tool for writing to a file on an Android device.
 */
private fun createWriteFileTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>
): RegisteredTool {
    return RegisteredTool(
        Tool(
            name = "write-file",
            description = """
                Writes content to a file on the Android device.
                Creates the file if it doesn't exist, or overwrites it if it does.
                Use with caution as this can overwrite important files on the device.
            """.trimIndent(),
            inputSchema = FILE_SYSTEM_CONTENT_TOOL_INPUT
        )
    ) { request ->
        val serial = request.arguments["serial"]?.jsonPrimitive?.content
        val path = request.arguments["path"]?.jsonPrimitive?.content
        val content = request.arguments["content"]?.jsonPrimitive?.content

        if (serial == null) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("The 'serial' parameter is required."))
            )
        }
        if (path == null) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("The 'path' parameter is required."))
            )
        }
        if (content == null) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("The 'content' parameter is required."))
            )
        }

        // Check if path is allowed
        if (!isPathAllowed(path, allowedPaths)) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("Access to path '$path' is not allowed for security reasons. Allowed paths: ${allowedPaths.joinToString()}"))
            )
        }

        try {
            // Create a temporary file with the content
            val tempFile = withContext(Dispatchers.IO) {
                val tempFile = Files.createTempFile("adbfriend", ".tmp").toFile()
                tempFile.writeText(content)
                tempFile
            }

            // Push the file to the device
            withContext(Dispatchers.IO) {
                val channel = adb.execute(
                    PushFileRequest(
                        local = tempFile,
                        remotePath = path.escapeForSync(),
                        listOf(Feature.LS_V2, Feature.STAT_V2, Feature.SENDRECV_V2),
                        mode = "0644"
                    ),
                    scope = CoroutineScope(Dispatchers.IO),
                    serial = serial
                )

                // Wait for the transfer to complete
                for (percentageDouble in channel) {
                    // Progress updates
                }
            }

            // Delete the temporary file
            withContext(Dispatchers.IO) {
                tempFile.delete()
            }

            val result = buildJsonObject {
                put("path", JsonPrimitive(path))
                put("success", JsonPrimitive(true))
            }

            CallToolResult(
                content = listOf(TextContent(result.toString()))
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Failed to write file: ${e.message}"))
            )
        }
    }
}

/**
 * Creates a tool for creating directories on an Android device.
 */
private fun createCreateDirectoryTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>
): RegisteredTool {
    return RegisteredTool(
        Tool(
            name = "create-directory",
            description = """
                Creates a directory on the Android device.
                Creates parent directories if they don't exist.
            """.trimIndent(),
            inputSchema = FILE_SYSTEM_TOOL_INPUT
        )
    ) { request ->
        val serial = request.arguments["serial"]?.jsonPrimitive?.content
        val path = request.arguments["path"]?.jsonPrimitive?.content

        if (serial == null) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("The 'serial' parameter is required."))
            )
        }
        if (path == null) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("The 'path' parameter is required."))
            )
        }

        // Check if path is allowed
        if (!isPathAllowed(path, allowedPaths)) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("Access to path '$path' is not allowed for security reasons. Allowed paths: ${allowedPaths.joinToString()}"))
            )
        }

        try {
            // Use mkdir command to create directory
            val response = adb.execute(
                request = ShellCommandRequest("mkdir -p \"${path.escapeForSync()}\""),
                serial = serial
            )

            if (response.errorOutput.isNotBlank()) {
                CallToolResult(
                    content = listOf(TextContent("Failed to create directory: ${response.errorOutput}"))
                )
            } else {
                val result = buildJsonObject {
                    put("path", JsonPrimitive(path))
                    put("success", JsonPrimitive(true))
                }

                CallToolResult(
                    content = listOf(TextContent(result.toString()))
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Failed to create directory: ${e.message}"))
            )
        }
    }
}

/**
 * Creates a tool for deleting files and directories on an Android device.
 */
private fun createDeleteTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>
): RegisteredTool {
    return RegisteredTool(
        Tool(
            name = "delete",
            description = """
                Deletes a file or directory on the Android device.
                Use the 'recursive' parameter to delete directories recursively.
                Use with caution as this can delete important files on the device.
            """.trimIndent(),
            inputSchema = FILE_SYSTEM_RECURSIVE_TOOL_INPUT
        )
    ) { request ->
        val serial = request.arguments["serial"]?.jsonPrimitive?.content
        val path = request.arguments["path"]?.jsonPrimitive?.content
        val recursive = request.arguments["recursive"]?.jsonPrimitive?.booleanOrNull ?: false

        if (serial == null) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("The 'serial' parameter is required."))
            )
        }
        if (path == null) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("The 'path' parameter is required."))
            )
        }

        // Check if path is allowed
        if (!isPathAllowed(path, allowedPaths)) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent("Access to path '$path' is not allowed for security reasons. Allowed paths: ${allowedPaths.joinToString()}"))
            )
        }

        try {
            // Use rm command to delete file or directory
            val command = if (recursive) {
                "rm -rf \"${path.escapeForSync()}\""
            } else {
                "rm \"${path.escapeForSync()}\""
            }

            val response = adb.execute(
                request = ShellCommandRequest(command),
                serial = serial
            )

            if (response.errorOutput.isNotBlank()) {
                CallToolResult(
                    content = listOf(TextContent("Failed to delete: ${response.errorOutput}"))
                )
            } else {
                val result = buildJsonObject {
                    put("path", JsonPrimitive(path))
                    put("success", JsonPrimitive(true))
                }

                CallToolResult(
                    content = listOf(TextContent(result.toString()))
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Failed to delete: ${e.message}"))
            )
        }
    }
}

/**
 * Creates a set of file system tools for working with files on an Android device.
 *
 * @param adb The ADB client to use for communication with the device
 * @param allowedPaths List of paths that are allowed to be accessed (for security)
 * @return A list of registered tools for file system operations
 */
fun createFileSystemTools(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String> = DEFAULT_ALLOWED_PATHS
): List<RegisteredTool> = buildList {
    add(createListFilesTool(adb, allowedPaths))
    add(createReadFileTool(adb, allowedPaths))
    add(createWriteFileTool(adb, allowedPaths))
    add(createCreateDirectoryTool(adb, allowedPaths))
    add(createDeleteTool(adb, allowedPaths))
}
