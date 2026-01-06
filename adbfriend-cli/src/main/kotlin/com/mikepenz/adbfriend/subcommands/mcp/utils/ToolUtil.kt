package com.mikepenz.adbfriend.subcommands.mcp.utils

import com.mikepenz.adbfriend.subcommands.mcp.exception.ToolException
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*

fun createTool(
    /** The name of the tool. */
    name: String,
    /** A human-readable description of the tool. */
    description: String?,
    /** A JSON object defining the expected parameters for the tool. */
    inputSchema: ToolSchema,
    /** The title of the tool. */
    title: String = name,
    outputSchema: ToolSchema? = null,
    annotations: ToolAnnotations.() -> ToolAnnotations = { this },
    handler: suspend CallToolRequest.() -> CallToolResult,
): RegisteredTool = RegisteredTool(
    Tool(
        name = name,
        title = title,
        description = description,
        inputSchema = inputSchema,
        outputSchema = outputSchema,
        annotations = annotations(ToolAnnotations(title = name))
    ),
) {
    return@RegisteredTool try {
        handler(it)
    } catch (t: ToolException) {
        t.message.asStructuredResponse()
    } catch (t: Throwable) {
        "An unhandled exception occurred while using the tool: ${t.message}".asStructuredResponse()
    }
}

fun JsonObject.asStructuredResponse(): CallToolResult {
    return CallToolResult(
        structuredContent = this,
        content = listOf(TextContent(this.toString()))
    )
}

fun String.asStructuredResponse(successful: Boolean = false): CallToolResult {
    return buildJsonObject {
        put("success", JsonPrimitive(successful))
        put("message", JsonPrimitive(this@asStructuredResponse))
    }.asStructuredResponse()
}

fun JsonObjectBuilder.applyDefaultOutputSchema(
    successDescription: String = "Whether the operations was executed successfully",
    messageDescription: String = "In case of failure, the message will contain the error details.",
) {
    put("success", buildJsonObject {
        put("type", JsonPrimitive("boolean"))
        put("description", JsonPrimitive(successDescription))
    })
    put("message", buildJsonObject {
        put("type", JsonPrimitive("string"))
        put("description", JsonPrimitive(messageDescription))
    })
}

val CallToolRequest.inputSerial: String
    get() = arguments?.get("serial")?.jsonPrimitive?.content ?: throw ToolException("The 'serial' parameter is required.")

val CallToolRequest.inputPath: String
    get() = arguments?.get("path")?.jsonPrimitive?.content ?: throw ToolException("The 'path' parameter is required.")

val CallToolRequest.inputRecursive: Boolean
    get() = arguments?.get("recursive")?.jsonPrimitive?.booleanOrNull ?: false

val CallToolRequest.inputPaths: List<String>
    get() = arguments?.get("paths")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
        ?: throw ToolException("The 'paths' parameter is required and must be an array of strings.")

val CallToolRequest.inputContent: String
    get() = arguments?.get("content")?.jsonPrimitive?.content ?: throw ToolException("The 'content' parameter is required.")

val CallToolRequest.inputOutputPath: String
    get() = arguments?.get("output-path")?.jsonPrimitive?.content ?: throw ToolException("The 'output-path' parameter is required.")

val CallToolRequest.inputApkPath: String
    get() = arguments?.get("apk-path")?.jsonPrimitive?.content ?: throw ToolException("The 'apk-path' parameter is required.")

val CallToolRequest.inputOperations: JsonArray
    get() = arguments?.get("operations")?.jsonArray ?: throw ToolException("The 'operations' parameter is required and must be an array.")