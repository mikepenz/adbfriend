@file:OptIn(ExperimentalSerializationApi::class)

package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.Feature
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.malinskiy.adam.request.sync.v2.PullFileRequest
import com.malinskiy.adam.request.sync.v2.PushFileRequest
import com.mikepenz.adbfriend.extensions.escapeForSync
import com.mikepenz.adbfriend.subcommands.mcp.exception.ToolException
import com.mikepenz.adbfriend.subcommands.mcp.utils.*
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files

/**
 * Creates a tool for listing files and directories on an Android device.
 */
private fun createListFilesTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>,
): RegisteredTool = createTool(
    name = "list-files",
    description = """
        Lists files and directories at the specified path on the Android device.
        Returns information about each file/directory including name, size, type, and last modified time.
        Use the 'recursive' parameter to list files in subdirectories recursively.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_RECURSIVE_TOOL_INPUT,
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema()
            put("path", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The path that was listed"))
            })
            put("recursive", buildJsonObject {
                put("type", JsonPrimitive("boolean"))
                put("description", JsonPrimitive("Whether the listing was recursive"))
            })
            put("files", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("description", JsonPrimitive("List of files and directories at the specified path"))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("name", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The name of the file or directory"))
                        })
                        put("path", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The full path of the file or directory"))
                        })
                        put("size", buildJsonObject {
                            put("type", JsonPrimitive("integer"))
                            put("description", JsonPrimitive("The size of the file in bytes"))
                        })
                        put("isDirectory", buildJsonObject {
                            put("type", JsonPrimitive("boolean"))
                            put("description", JsonPrimitive("Whether the item is a directory"))
                        })
                        put("lastModified", buildJsonObject {
                            put("type", JsonPrimitive("integer"))
                            put("description", JsonPrimitive("The last modified time as a Unix timestamp"))
                        })
                    })
                })
            })
        }
    ),
    annotations = {
        copy(
            readOnlyHint = true,
            openWorldHint = false,
            destructiveHint = false,
        )
    }
) {
    val serial = inputSerial
    val path = inputPath
    val recursive = inputRecursive
    verifyPathAllowed(path, allowedPaths)

    try {
        // Get all files using the common listFiles function
        val allFiles = adb.listFiles(serial, path, allowedPaths, recursive)

        // Build the result JSON
        val result = buildJsonObject {
            put("success", JsonPrimitive(true))
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
        result.asStructuredResponse()
    } catch (e: Exception) {
        "Failed to list files: ${e.message}".asStructuredResponse()
    }
}

/**
 * Creates a tool for reading file contents on an Android device.
 */
private fun createReadFileTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>,
): RegisteredTool = createTool(
    name = "read-file",
    description = """
        Reads the contents of a (text) file on the Android device.
        Returns the file content as text.
        This API will not work for binary files.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_TOOL_INPUT,
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema(
                successDescription = "Whether the file was read successfully",
                messageDescription = "An optional status message informing about errors during execution"
            )
            put("path", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The path of the file that was read"))
            })
            put("content", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The textual content of the file"))
            })
        },
        required = listOf("success")
    ),
    annotations = {
        copy(
            readOnlyHint = true,
            openWorldHint = false,
            destructiveHint = false,
        )
    }
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
            "Failed to read file: ${response.errorOutput}".asStructuredResponse()
        } else {
            val result = buildJsonObject {
                put("success", JsonPrimitive(true))
                put("path", JsonPrimitive(path))
                put("content", JsonPrimitive(response.output))
            }
            result.asStructuredResponse()
        }
    } catch (e: Exception) {
        "Failed to read file: ${e.message}".asStructuredResponse()
    }
}

/**
 * Creates a tool for writing to a file on an Android device.
 */
private fun createWriteFileTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>,
): RegisteredTool = createTool(
    name = "write-file",
    description = """
        Writes content to a file on the Android device.
        Creates the file if it doesn't exist, or overwrites it if it does.
        Use with caution as this can overwrite important files on the device.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_CONTENT_TOOL_INPUT,
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema(
                successDescription = "Whether the file was written successfully",
                messageDescription = "An optional status message informing about errors during execution"
            )
            put("path", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The path of the file that was written"))
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
    val serial = inputSerial
    val path = inputPath
    val content = inputContent

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
            put("success", JsonPrimitive(true))
            put("path", JsonPrimitive(path))
        }
        result.asStructuredResponse()
    } catch (e: Exception) {
        "Failed to write file: ${e.message}".asStructuredResponse()
    }
}

/**
 * Creates a tool for creating directories on an Android device.
 */
private fun createCreateDirectoryTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>,
): RegisteredTool = createTool(
    name = "create-directory",
    description = """
        Creates a directory on the Android device.
        Creates parent directories if they don't exist.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_TOOL_INPUT,
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema(
                successDescription = "Whether the directory was created successfully",
                messageDescription = "An optional status message informing about errors during execution"
            )
            put("path", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The path of the directory that was created"))
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
            "Failed to create directory: ${response.errorOutput}".asStructuredResponse()
        } else {
            val result = buildJsonObject {
                put("success", JsonPrimitive(true))
                put("path", JsonPrimitive(path))
            }
            result.asStructuredResponse()
        }
    } catch (e: Exception) {
        "Failed to create directory: ${e.message}".asStructuredResponse()
    }
}

/**
 * Creates a tool for deleting files and directories on an Android device.
 */
private fun createDeleteTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>,
): RegisteredTool = createTool(
    name = "delete",
    description = """
        Deletes a file or directory on the Android device.
        Use the 'recursive' parameter to delete directories recursively.
        Use with extreme caution as this can delete important files on the device.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_RECURSIVE_TOOL_INPUT,
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema(
                successDescription = "Whether the delete operation was executed successfully",
                messageDescription = "An optional status message informing about errors during execution"
            )
            put("path", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The path that was deleted"))
            })
            put("recursive", buildJsonObject {
                put("type", JsonPrimitive("boolean"))
                put("description", JsonPrimitive("Whether the delete operation was recursive"))
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
    val serial = inputSerial
    val path = inputPath
    val recursive = inputRecursive

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
            "Failed to delete: ${response.errorOutput}".asStructuredResponse()
        } else {
            val result = buildJsonObject {
                put("success", JsonPrimitive(true))
                put("path", JsonPrimitive(path))
                put("recursive", JsonPrimitive(recursive))
            }
            result.asStructuredResponse()
        }
    } catch (e: Exception) {
        "Failed to delete: ${e.message}".asStructuredResponse()
    }
}

/**
 * Creates a tool for listing allowed directories on an Android device.
 */
private fun createListAllowedDirectoriesTool(
    allowedPaths: List<String>,
): RegisteredTool = createTool(
    name = "list-allowed-directories",
    description = """
        Lists the directories on the Android device that are allowed to be accessed by the file system tools.
        Use this to understand which directories are available on the Android device before trying to access files.
        This API does not return information about allowed paths on the host system.
    """.trimIndent(),
    inputSchema = Tool.Input(),
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema(
                successDescription = "Whether the list of allowed directories was retrieved successfully",
                messageDescription = "An optional status message informing about errors during execution"
            )
            put("allowedDirectories", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("description", JsonPrimitive("The list of directories allowed on the Android device"))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                })
            })
        },
        required = listOf("success", "allowedDirectories")
    ),
    annotations = {
        copy(
            readOnlyHint = true,
            openWorldHint = false,
            destructiveHint = false,
        )
    }
) {
    CallToolResult(
        structuredContent = buildJsonObject {
            put("success", JsonPrimitive(true))
            put("allowedDirectories", buildJsonArray {
                allowedPaths.forEach { path ->
                    add(JsonPrimitive(path))
                }
            })
        },
        content = listOf()
    )
}


/**
 * Creates a tool for listing allowed directories on an Android device.
 */
private fun createListAllowedHostDirectoriesTool(
    hostAllowedPaths: List<String>,
): RegisteredTool = createTool(
    name = "list-allowed-host-directories",
    description = """
        Lists the directories on the host system that are allowed to be accessed by the file system tools.
        Use this to understand which directories are available on the host system before trying to access files.
        This API does not return information about allowed paths on the android device.
    """.trimIndent(),
    inputSchema = Tool.Input(),
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema(
                successDescription = "Whether the list of allowed host directories was retrieved successfully",
                messageDescription = "An optional status message informing about errors during execution"
            )
            put("allowedDirectories", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("description", JsonPrimitive("The list of directories allowed on the host system"))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                })
            })
        },
        required = listOf("success", "allowedDirectories")
    ),
    annotations = {
        copy(
            readOnlyHint = true,
            openWorldHint = false,
            destructiveHint = false,
        )
    }
) {
    CallToolResult(
        structuredContent = buildJsonObject {
            put("success", JsonPrimitive(true))
            put("allowedDirectories", buildJsonArray {
                hostAllowedPaths.forEach { path ->
                    add(JsonPrimitive(path))
                }
            })
        },
        content = listOf()
    )
}

/**
 * Creates a tool for moving files on an Android device.
 */
private fun createMoveFilesTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>,
): RegisteredTool = createTool(
    name = "move-files",
    description = """
        Moves one or many files from the individual source path to the destination path on the Android device.
        This can also be used to rename files.
        Both source and destination paths for each provided item must be within the allowed paths.
        Use caution as this can overwrite important files on the device.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_MOVE_TOOL_INPUT,
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema(
                successDescription = "Whether all move operations were processed",
                messageDescription = "An optional status message informing about errors during execution"
            )
            put("operations", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("description", JsonPrimitive("The list of per-operation results"))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("source", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The source file path on the Android device"))
                        })
                        put("destination", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The destination file path on the Android device"))
                        })
                        put("success", buildJsonObject {
                            put("type", JsonPrimitive("boolean"))
                            put("description", JsonPrimitive("Whether the move operation succeeded"))
                        })
                        put("error", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Error message if the operation failed"))
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
    val serial = inputSerial
    val operations = inputOperations
    if (operations.isEmpty()) throw ToolException("At least one move operation must be provided.")

    try {
        val results = buildJsonObject {
            put("success", JsonPrimitive(true))
            put("operations", buildJsonArray {
                operations.forEach { operation ->
                    try {
                        val sourcePath = operation.jsonObject["source"]?.jsonPrimitive?.content
                            ?: throw ToolException("Each operation must have a 'source' parameter.")
                        val destinationPath = operation.jsonObject["destination"]?.jsonPrimitive?.content
                            ?: throw ToolException("Each operation must have a 'destination' parameter.")

                        // Verify both paths are allowed
                        verifyPathAllowed(sourcePath, allowedPaths)
                        verifyPathAllowed(destinationPath, allowedPaths)

                        // Use mv command to move the file
                        val response = adb.execute(
                            request = ShellCommandRequest("mv \"${sourcePath.escapeForSync()}\" \"${destinationPath.escapeForSync()}\""),
                            serial = serial
                        )

                        val error = response.errorOutput.isNotBlank()
                        add(buildJsonObject {
                            put("source", JsonPrimitive(sourcePath))
                            put("destination", JsonPrimitive(destinationPath))
                            if (error) put("error", JsonPrimitive(response.errorOutput))
                            put("success", JsonPrimitive(!error))
                        })
                    } catch (e: Exception) {
                        // If we can't extract source/destination, create a generic error entry
                        val sourcePath = operation.jsonObject["source"]?.jsonPrimitive?.contentOrNull ?: "No source path provided for operation"
                        val destinationPath = operation.jsonObject["destination"]?.jsonPrimitive?.contentOrNull ?: "No destination path provided for operation"
                        add(buildJsonObject {
                            put("source", JsonPrimitive(sourcePath))
                            put("destination", JsonPrimitive(destinationPath))
                            put("error", JsonPrimitive(e.message ?: "Unknown error"))
                            put("success", JsonPrimitive(false))
                        })
                    }
                }
            })
        }
        results.asStructuredResponse()
    } catch (e: Exception) {
        "Failed to process move operations: ${e.message}".asStructuredResponse()
    }
}

/**
 * Creates a tool for reading multiple files at once on an Android device.
 */
private fun createReadMultipleFilesTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>,
): RegisteredTool = createTool(
    name = "read-multiple-files",
    description = """
        Reads the contents of multiple files on the Android device at once.
        Returns the file contents as text for each file.
        This helps reduce the number of LLM calls needed when reading multiple files.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_MULTIPLE_FILES_TOOL_INPUT,
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema(
                successDescription = "Whether the files were processed successfully",
                messageDescription = "An optional status message informing about errors during execution"
            )
            put("files", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("description", JsonPrimitive("Per-file results including path, content (if successful), and errors (if any)"))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("path", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The path of the file that was read"))
                        })
                        put("content", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The textual content of the file if read successfully"))
                        })
                        put("success", buildJsonObject {
                            put("type", JsonPrimitive("boolean"))
                            put("description", JsonPrimitive("Whether the file was read successfully"))
                        })
                        put("error", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Error message if the file could not be read"))
                        })
                    })
                })
            })
        },
        required = listOf("success")
    ),
    annotations = {
        copy(
            readOnlyHint = true,
            openWorldHint = false,
            destructiveHint = false,
        )
    }
) {
    val serial = inputSerial
    val paths = inputPaths

    if (paths.isEmpty()) throw ToolException("At least one file path must be provided.")

    // Verify all paths are allowed
    paths.forEach { path ->
        verifyPathAllowed(path, allowedPaths)
    }

    try {
        val results = buildJsonObject {
            put("success", JsonPrimitive(true))
            put("files", buildJsonArray {
                paths.forEach { path ->
                    try {
                        // Use cat command to read file contents
                        val response = adb.execute(
                            request = ShellCommandRequest("cat \"${path.escapeForSync()}\""),
                            serial = serial
                        )

                        val error = response.errorOutput.isNotBlank()
                        add(buildJsonObject {
                            put("path", JsonPrimitive(path))
                            if (!error) {
                                put("content", JsonPrimitive(response.output))
                            } else {
                                put("error", JsonPrimitive(response.errorOutput))
                            }
                            put("success", JsonPrimitive(!error))
                        })
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
        results.asStructuredResponse()
    } catch (e: Exception) {
        "Failed to read multiple files: ${e.message}".asStructuredResponse()
    }
}

/**
 * Creates a tool for searching files with a case-insensitive glob pattern on an Android device.
 */
private fun createSearchFilesTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>,
): RegisteredTool = createTool(
    name = "search-files",
    description = """
        Searches for files on the Android device matching a case-insensitive glob pattern within the specified path or alternative within the allowed paths on the Android device.
        Returns information about each matching file including name, path, size, type.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_SEARCH_TOOL_INPUT,
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema(
                successDescription = "Whether the search operation was executed successfully",
                messageDescription = "An optional status message informing about errors during execution"
            )
            put("basePath", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The base path where the search was performed (if provided)"))
            })
            put("pattern", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The glob pattern used for the search"))
            })
            put("recursive", buildJsonObject {
                put("type", JsonPrimitive("boolean"))
                put("description", JsonPrimitive("Whether the search was performed recursively"))
            })
            put("matchingFiles", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("description", JsonPrimitive("List of files matching the search criteria"))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("name", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The name of the file"))
                        })
                        put("path", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The full path of the matching file"))
                        })
                        put("size", buildJsonObject {
                            put("type", JsonPrimitive("integer"))
                            put("description", JsonPrimitive("The size of the file in bytes"))
                        })
                        put("isDirectory", buildJsonObject {
                            put("type", JsonPrimitive("boolean"))
                            put("description", JsonPrimitive("Whether the item is a directory"))
                        })
                    })
                })
            })
        },
        required = listOf("success")
    ),
    annotations = {
        copy(
            readOnlyHint = true,
            openWorldHint = false,
            destructiveHint = false,
        )
    }
) {
    val serial = inputSerial
    val path = arguments["path"]?.jsonPrimitive?.content
    val pattern = arguments["pattern"]?.jsonPrimitive?.content
        ?: throw ToolException("The 'pattern' parameter is required.")
    val recursive = inputRecursive

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
            put("success", JsonPrimitive(true))
            if (path != null) put("basePath", JsonPrimitive(path))
            put("pattern", JsonPrimitive(pattern))
            put("recursive", JsonPrimitive(recursive))
            put("matchingFiles", buildJsonArray {
                fileDetails.forEach { add(it) }
            })
        }
        result.asStructuredResponse()
    } catch (e: Exception) {
        "Failed to search files: ${e.message}".asStructuredResponse()
    }
}

/**
 * Creates a tool for copying one or many files from an Android device to the host system.
 */
private fun createCopyFileToHostTool(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String>,
    hostAllowedPaths: List<String>,
): RegisteredTool = createTool(
    name = "copy-file-to-host",
    description = """
        Copies one or many files from the Android device to the host system. Specify the file paths on the Android device and the output paths on the host system.
        The android-path must be within the allowed android paths as defined by `list-allowed-directories`.
        The host-path must be within the allowed host paths as defined by `list-allowed-host-directories`. 
        This tool works with both text and binary files. This tool does not work for directories.
        Use with caution as this can overwrite existing files on the host system.
    """.trimIndent(),
    inputSchema = FILE_SYSTEM_COPY_TO_HOST_TOOL_INPUT,
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema(
                successDescription = "Whether all copy operations were processed",
                messageDescription = "An optional status message informing about errors during execution"
            )
            put("operations", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("description", JsonPrimitive("The list of per-operation results"))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("android-path", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The source file path on the Android device"))
                        })
                        put("host-path", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The destination file path on the host system"))
                        })
                        put("success", buildJsonObject {
                            put("type", JsonPrimitive("boolean"))
                            put("description", JsonPrimitive("Whether the copy operation succeeded"))
                        })
                        put("error", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("Error message if the operation failed"))
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
    val serial = inputSerial
    val operations = inputOperations
    if (operations.isEmpty()) throw ToolException("At least one copy operation must be provided.")
    try {
        val results = buildJsonObject {
            put("success", JsonPrimitive(true))
            put("operations", buildJsonArray {
                operations.forEach { operation ->
                    try {
                        val androidPath = operation.jsonObject["android-path"]?.jsonPrimitive?.content
                            ?: throw ToolException("Each operation must have a 'android-path' parameter.")
                        val hostPath = operation.jsonObject["host-path"]?.jsonPrimitive?.content
                            ?: throw ToolException("Each operation must have an 'host-path' parameter.")

                        // Verify device path is allowed
                        verifyPathAllowed(androidPath, allowedPaths)
                        // Use provided host allowed paths or default if not provided
                        verifyHostPathAllowed(hostPath, hostAllowedPaths)

                        // Ensure the output directory exists
                        val directory = File(hostPath).parentFile
                        if (directory != null && !directory.exists()) {
                            directory.mkdirs()
                        }

                        // Pull the file from the device to the host
                        val outputFile = File(hostPath)
                        withContext(Dispatchers.IO) {
                            val pullChannel = adb.execute(
                                request = PullFileRequest(
                                    remotePath = androidPath.escapeForSync(),
                                    local = outputFile,
                                    supportedFeatures = listOf(Feature.LS_V2, Feature.STAT_V2, Feature.SENDRECV_V2)
                                ),
                                scope = CoroutineScope(Dispatchers.IO),
                                serial = serial
                            )

                            @Suppress("ControlFlowWithEmptyBody")
                            for (percentageDouble in pullChannel) {
                                // wait until the file is fully pulled
                            }
                        }

                        add(buildJsonObject {
                            put("android-path", JsonPrimitive(androidPath))
                            put("host-path", JsonPrimitive(hostPath))
                            put("success", JsonPrimitive(true))
                        })
                    } catch (e: Exception) {
                        // If we can't extract path/output-path, create a generic error entry
                        val path = operation.jsonObject["android-path"]?.jsonPrimitive?.contentOrNull ?: "No `android-path` provided for operation"
                        val outputPath = operation.jsonObject["host-path"]?.jsonPrimitive?.contentOrNull ?: "No output `host-path` provided for operation"

                        add(buildJsonObject {
                            put("android-path", JsonPrimitive(path))
                            put("host-path", JsonPrimitive(outputPath))
                            put("error", JsonPrimitive(e.message ?: "Unknown error"))
                            put("success", JsonPrimitive(false))
                        })
                    }
                }
            })
        }

        results.asStructuredResponse()
    } catch (e: Exception) {
        "Failed to process copy operations: ${e.message}".asStructuredResponse()
    }
}

/**
 * Creates a set of file system tools for working with files on an Android device.
 *
 * @param adb The ADB client to use for communication with the device
 * @param allowedPaths List of paths that are allowed to be accessed (for security)
 * @param hostAllowedPaths List of paths on the host system that are allowed to be accessed (for security)
 * @return A list of registered tools for file system operations
 */
fun createFileSystemTools(
    adb: AndroidDebugBridgeClient,
    allowedPaths: List<String> = DEFAULT_ALLOWED_PATHS,
    hostAllowedPaths: List<String> = getDefaultHostAllowedPaths(),
): List<RegisteredTool> = buildList {
    add(createListAllowedDirectoriesTool(allowedPaths))
    add(createListAllowedHostDirectoriesTool(hostAllowedPaths))
    add(createListFilesTool(adb, allowedPaths))
    add(createReadFileTool(adb, allowedPaths))
    add(createReadMultipleFilesTool(adb, allowedPaths))
    add(createWriteFileTool(adb, allowedPaths))
    add(createCreateDirectoryTool(adb, allowedPaths))
    add(createDeleteTool(adb, allowedPaths))
    add(createMoveFilesTool(adb, allowedPaths))
    add(createSearchFilesTool(adb, allowedPaths))
    add(createCopyFileToHostTool(adb, allowedPaths, hostAllowedPaths))
}
