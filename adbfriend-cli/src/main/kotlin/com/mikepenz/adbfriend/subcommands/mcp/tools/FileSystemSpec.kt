package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.mikepenz.adbfriend.subcommands.mcp.exception.ToolException


// Define allowed paths for security
internal val DEFAULT_ALLOWED_PATHS = listOf(
    "/sdcard/Download/"
)

/**
 * Checks if a path is allowed based on the provided allowed paths list.
 * A path is allowed if it starts with any of the allowed paths.
 */
internal fun verifyPathAllowed(path: String, allowedPaths: List<String>) {
    if (!allowedPaths.any { allowedPath -> path.startsWith(allowedPath) || "$path/".startsWith(allowedPath) }) {
        throw ToolException("Access to path '$path' is not allowed for security reasons. Allowed paths: ${allowedPaths.joinToString()}")
    }
}

// Define default allowed paths for the host system
internal fun getDefaultHostAllowedPaths(): List<String> {
    val userHome = System.getProperty("user.home")
    return listOf("$userHome/adbfriend")
}
