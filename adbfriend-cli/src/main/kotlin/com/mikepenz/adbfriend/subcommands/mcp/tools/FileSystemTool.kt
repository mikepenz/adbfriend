@file:OptIn(ExperimentalSerializationApi::class)

package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.Feature
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.malinskiy.adam.request.sync.v2.PushFileRequest
import com.mikepenz.adbfriend.extensions.escapeForSync
import com.mikepenz.adbfriend.subcommands.mcp.exception.ToolException
import com.mikepenz.adbfriend.subcommands.mcp.utils.*
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
internal val DEFAULT_ALLOWED_PATHS = listOf(
    "/sdcard/Download/"
)

/**
 * Checks if a path is allowed based on the provided allowed paths list.
 * A path is allowed if it starts with any of the allowed paths.
 */
private fun verifyPathAllowed(path: String, allowedPaths: List<String>) {
    if (!allowedPaths.any { allowedPath -> path.startsWith(allowedPath) || "$path/".startsWith(allowedPath) }) {
        throw ToolException("Access to path '$path' is not allowed for security reasons. Allowed paths: ${allowedPaths.joinToString()}")
    }
}


/**
 * Creates a tool for listing files and directories on an Android device.
 */
private fun createListFilesTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>
): RegisteredTool = createTool(
    name = "list-files",
    description = """
        Lists files and directories at the specified path on the Android device.
        Returns information about each file/directory including name, size, type, and last modified time.
        Use the 'recursive' parameter to list files in subdirectories recursively.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_RECURSIVE_TOOL_INPUT
) {
    val serial = inputSerial
    val path = inputPath
    val recursive = arguments["recursive"]?.jsonPrimitive?.booleanOrNull ?: false
    verifyPathAllowed(path, allowedPaths)

    try {
        verifyPathAllowed(path, allowedPaths)

        // Get all files using the common listFiles function
        val allFiles = adb.listFiles(serial, path, allowedPaths, recursive)

        // Build the result JSON
        val result = buildJsonObject {
            put("path", JsonPrimitive(path))
            put("recursive", JsonPrimitive(recursive))
            put("files", buildJsonArray {
                allFiles.forEach { (parentPath, file) ->
                    if (file.name != null && file.name != "." && file.name != "..") {
                        val filePath = if (parentPath.endsWith("/")) "$parentPath${file.name}" else "$parentPath/${file.name}"
                        add(buildJsonObject {
                            put("name", JsonPrimitive(file.name))
                            put("path", JsonPrimitive(filePath))
                            put("size", JsonPrimitive(file.size()))
                            put("isDirectory", JsonPrimitive(file.isDirectory()))
                            put("lastModified", JsonPrimitive(file.mtime.epochSecond))
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

/**
 * Creates a tool for reading file contents on an Android device.
 */
private fun createReadFileTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>
): RegisteredTool = createTool(
    name = "read-file",
    description = """
        Reads the contents of a file on the Android device.
        Returns the file content as text.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_TOOL_INPUT
) {
    val serial = inputSerial
    val path = inputPath
    verifyPathAllowed(path, allowedPaths)

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

/**
 * Creates a tool for writing to a file on an Android device.
 */
private fun createWriteFileTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>
): RegisteredTool = createTool(
    name = "write-file",
    description = """
        Writes content to a file on the Android device.
        Creates the file if it doesn't exist, or overwrites it if it does.
        Use with caution as this can overwrite important files on the device.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_CONTENT_TOOL_INPUT
) {
    val serial = inputSerial
    val path = inputPath
    val content = arguments["content"]?.jsonPrimitive?.content
        ?: throw ToolException("The 'content' parameter is required.")

    verifyPathAllowed(path, allowedPaths)

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

/**
 * Creates a tool for creating directories on an Android device.
 */
private fun createCreateDirectoryTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>
): RegisteredTool = createTool(
    name = "create-directory",
    description = """
        Creates a directory on the Android device.
        Creates parent directories if they don't exist.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_TOOL_INPUT
) {
    val serial = inputSerial
    val path = inputPath
    verifyPathAllowed(path, allowedPaths)

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

/**
 * Creates a tool for deleting files and directories on an Android device.
 */
private fun createDeleteTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>
): RegisteredTool = createTool(
    name = "delete",
    description = """
        Deletes a file or directory on the Android device.
        Use the 'recursive' parameter to delete directories recursively.
        Use with caution as this can delete important files on the device.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_RECURSIVE_TOOL_INPUT
) {
    val serial = inputSerial
    val path = inputPath
    val recursive = arguments["recursive"]?.jsonPrimitive?.booleanOrNull ?: false

    verifyPathAllowed(path, allowedPaths)

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

/**
 * Creates a tool for listing allowed directories on an Android device.
 */
private fun createListAllowedDirectoriesTool(
    allowedPaths: List<String>
): RegisteredTool = createTool(
    name = "list-allowed-directories",
    description = """
        Lists the directories that are allowed to be accessed by the file system tools.
        Use this to understand which directories are available before trying to access files.
    """.trimIndent(),
    inputSchema = Tool.Input()
) {
    try {
        val result = buildJsonObject {
            put("allowedDirectories", buildJsonArray {
                allowedPaths.forEach { path ->
                    add(JsonPrimitive(path))
                }
            })
        }

        CallToolResult(
            content = listOf(TextContent(result.toString()))
        )
    } catch (e: Exception) {
        CallToolResult(
            content = listOf(TextContent("Failed to list allowed directories: ${e.message}"))
        )
    }
}

/**
 * Creates a tool for moving files on an Android device.
 */
private fun createMoveFileTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>
): RegisteredTool = createTool(
    name = "move-file",
    description = """
        Moves a file from the source path to the destination path on the Android device.
        Both source and destination paths must be within the allowed paths.
        Use caution as this can overwrite important files on the device.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_MOVE_TOOL_INPUT
) {
    val serial = inputSerial
    val sourcePath = arguments["source"]?.jsonPrimitive?.content
        ?: throw ToolException("The 'source' parameter is required.")
    val destinationPath = arguments["destination"]?.jsonPrimitive?.content
        ?: throw ToolException("The 'destination' parameter is required.")

    // Verify both paths are allowed
    verifyPathAllowed(sourcePath, allowedPaths)
    verifyPathAllowed(destinationPath, allowedPaths)

    try {
        // Use mv command to move the file
        val response = adb.execute(
            request = ShellCommandRequest("mv \"${sourcePath.escapeForSync()}\" \"${destinationPath.escapeForSync()}\""),
            serial = serial
        )

        if (response.errorOutput.isNotBlank()) {
            CallToolResult(
                content = listOf(TextContent("Failed to move file: ${response.errorOutput}"))
            )
        } else {
            val result = buildJsonObject {
                put("source", JsonPrimitive(sourcePath))
                put("destination", JsonPrimitive(destinationPath))
                put("success", JsonPrimitive(true))
            }

            CallToolResult(
                content = listOf(TextContent(result.toString()))
            )
        }
    } catch (e: Exception) {
        CallToolResult(
            content = listOf(TextContent("Failed to move file: ${e.message}"))
        )
    }
}

/**
 * Creates a tool for reading multiple files at once on an Android device.
 */
private fun createReadMultipleFilesTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>
): RegisteredTool = createTool(
    name = "read-multiple-files",
    description = """
        Reads the contents of multiple files on the Android device at once.
        Returns the file contents as text for each file.
        This helps reduce the number of LLM calls needed when reading multiple files.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_MULTIPLE_FILES_TOOL_INPUT
) {
    val serial = inputSerial
    val paths = arguments["paths"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
        ?: throw ToolException("The 'paths' parameter is required and must be an array of strings.")

    if (paths.isEmpty()) {
        throw ToolException("At least one file path must be provided.")
    }

    // Verify all paths are allowed
    paths.forEach { path ->
        verifyPathAllowed(path, allowedPaths)
    }

    try {
        val results = buildJsonObject {
            put("files", buildJsonArray {
                paths.forEach { path ->
                    try {
                        // Use cat command to read file contents
                        val response = adb.execute(
                            request = ShellCommandRequest("cat \"${path.escapeForSync()}\""),
                            serial = serial
                        )

                        if (response.errorOutput.isNotBlank()) {
                            add(buildJsonObject {
                                put("path", JsonPrimitive(path))
                                put("error", JsonPrimitive(response.errorOutput))
                                put("success", JsonPrimitive(false))
                            })
                        } else {
                            add(buildJsonObject {
                                put("path", JsonPrimitive(path))
                                put("content", JsonPrimitive(response.output))
                                put("success", JsonPrimitive(true))
                            })
                        }
                    } catch (e: Exception) {
                        add(buildJsonObject {
                            put("path", JsonPrimitive(path))
                            put("error", JsonPrimitive(e.message ?: "Unknown error"))
                            put("success", JsonPrimitive(false))
                        })
                    }
                }
            })
        }

        CallToolResult(
            content = listOf(TextContent(results.toString()))
        )
    } catch (e: Exception) {
        CallToolResult(
            content = listOf(TextContent("Failed to read multiple files: ${e.message}"))
        )
    }
}

/**
 * Creates a tool for searching files with a case-insensitive glob pattern on an Android device.
 */
private fun createSearchFilesTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>
): RegisteredTool = createTool(
    name = "search-files",
    description = """
        Searches for files matching a case-insensitive glob pattern within the specified path or alternative within the allowed paths on the Android device.
        Returns information about each matching file including name, path, size, type.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_SEARCH_TOOL_INPUT
) {
    val serial = inputSerial
    val path = arguments["path"]?.jsonPrimitive?.content
    val pattern = arguments["pattern"]?.jsonPrimitive?.content
        ?: throw ToolException("The 'pattern' parameter is required.")
    val recursive = arguments["recursive"]?.jsonPrimitive?.booleanOrNull ?: false

    if (!path.isNullOrBlank()) {
        verifyPathAllowed(path, allowedPaths)
    }

    try {
        // Get files matching the pattern directly from the device
        val matchingFiles = if (path.isNullOrBlank()) {
            allowedPaths.flatMap { adb.findFilesOnDevice(serial, it, pattern, recursive) }
        } else {
            adb.findFilesOnDevice(serial, path, pattern, recursive)
        }.filter { it.isNotBlank() }

        // Get details for each matching file
        val fileDetails = matchingFiles.mapNotNull { filePath ->
            try {
                val fileResponse = adb.execute(
                    request = ShellCommandRequest("ls -la \"${filePath.escapeForSync()}\""),
                    serial = serial
                )

                val isDirectory = fileResponse.output.contains("d")
                val size = fileResponse.output.split("\\s+".toRegex()).getOrNull(4)?.toLongOrNull() ?: 0

                buildJsonObject {
                    put("name", JsonPrimitive(filePath.substringAfterLast('/')))
                    put("path", JsonPrimitive(filePath))
                    put("size", JsonPrimitive(size))
                    put("isDirectory", JsonPrimitive(isDirectory))
                }
            } catch (e: Exception) {
                null
            }
        }

        val result = buildJsonObject {
            put("basePath", JsonPrimitive(path))
            put("pattern", JsonPrimitive(pattern))
            put("matchingFiles", buildJsonArray {
                fileDetails.forEach { add(it) }
            })
        }

        CallToolResult(
            content = listOf(TextContent(result.toString()))
        )
    } catch (e: Exception) {
        CallToolResult(
            content = listOf(TextContent("Failed to search files: ${e.message}"))
        )
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
    add(createListAllowedDirectoriesTool(allowedPaths))
    add(createListFilesTool(adb, allowedPaths))
    add(createReadFileTool(adb, allowedPaths))
    add(createReadMultipleFilesTool(adb, allowedPaths))
    add(createWriteFileTool(adb, allowedPaths))
    add(createCreateDirectoryTool(adb, allowedPaths))
    add(createDeleteTool(adb, allowedPaths))
    add(createMoveFileTool(adb, allowedPaths))
    add(createSearchFilesTool(adb, allowedPaths))
}
