package com.mikepenz.adbfriend.subcommands.mcp.utils

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.mikepenz.adbfriend.extensions.escapeForSync
import com.mikepenz.adbfriend.subcommands.mcp.exception.ToolException


/**
 * Searches for files and directories on an Android device using the `find` command.
 *
 * @param serial The serial number of the Android device to search on
 * @param path The path on the device to start searching from
 * @param recursive Whether to search recursively through subdirectories (default: false)
 * @return List of found files and directories
 * @throws ToolException If the search operation fails
 */
suspend fun AndroidDebugBridgeClient.findFilesOnDevice(
    serial: String,
    path: String,
    recursive: Boolean = false,
): List<String> {
    val adb = this
    // Build the find command
    val findCommand = StringBuilder("find \"${path.escapeForSync()}\"")
    if (!recursive) {
        findCommand.append(" -maxdepth 1")
    }
    findCommand.append(" -type f -o -type d")

    // Execute the find command
    val response = adb.execute(
        request = ShellCommandRequest(findCommand.toString()),
        serial = serial
    )

    if (response.errorOutput.isNotBlank()) {
        throw ToolException("Failed to search files: ${response.errorOutput}")
    }

    return response.output.lines()
}