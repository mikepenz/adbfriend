package com.mikepenz.adbfriend.subcommands.mcp.utils

import com.mikepenz.adbfriend.subcommands.mcp.exception.ToolException
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.Tool.Input
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.serialization.json.jsonPrimitive

fun createTool(
    /** The name of the tool. */
    name: String,
    /** A human-readable description of the tool. */
    description: String?,
    /** A JSON object defining the expected parameters for the tool. */
    inputSchema: Input,
    handler: suspend CallToolRequest.() -> CallToolResult
): RegisteredTool = RegisteredTool(
    Tool(
        name = name,
        description = description,
        inputSchema = inputSchema
    ),
) {
    return@RegisteredTool try {
        handler(it)
    } catch (t: ToolException) {
        CallToolResult(content = listOf(TextContent(t.message)))
    } catch (t: Throwable) {
        CallToolResult(content = listOf(TextContent("An unhandled exception occurred while using the tool: ${t.message}")))
    }
}

val CallToolRequest.inputSerial: String
    get() = arguments["serial"]?.jsonPrimitive?.content ?: throw ToolException("The 'serial' parameter is required.")

val CallToolRequest.inputPath: String
    get() = arguments["path"]?.jsonPrimitive?.content ?: throw ToolException("The 'path' parameter is required.")

