package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.mikepenz.adbfriend.subcommands.mcp.utils.applyDefaultOutputSchema
import com.mikepenz.adbfriend.subcommands.mcp.utils.asStructuredResponse
import com.mikepenz.adbfriend.subcommands.mcp.utils.createTool
import com.mikepenz.adbfriend.subcommands.mcp.utils.inputSerial
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.serialization.json.*

/**
 * Input schema for proxy configuration operations
 */
internal val PROXY_TOOL_INPUT = Tool.Input(
    properties = buildJsonObject {
        put("serial", SERIAL_INPUT)
        put("enabled", buildJsonObject {
            put("type", JsonPrimitive("boolean"))
            put("description", JsonPrimitive("Whether to enable or disable the proxy. When false, proxy will be disabled."))
            put("default", JsonPrimitive(true))
        })
        put("host", buildJsonObject {
            put("type", JsonPrimitive("string"))
            put("description", JsonPrimitive("The proxy server host/IP address. Required when enabled is true."))
        })
        put("port", buildJsonObject {
            put("type", JsonPrimitive("integer"))
            put("description", JsonPrimitive("The proxy server port number. Required when enabled is true."))
            put("minimum", JsonPrimitive(1))
            put("maximum", JsonPrimitive(65535))
        })
    },
    required = listOf("serial", "enabled")
)

/**
 * Creates a tool for configuring HTTP proxy settings on Android devices.
 * This tool can set or disable the global HTTP proxy configuration.
 */
fun createProxyTool(adb: AndroidDebugBridgeClient): RegisteredTool = createTool(
    name = "configure-proxy",
    description = """
        Configures HTTP proxy settings on an Android device.
        Can set a proxy server with host and port, or disable the proxy entirely.
        Uses the global http_proxy setting which affects system-wide network connections.
    """.trimIndent(),
    inputSchema = PROXY_TOOL_INPUT,
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema(
                successDescription = "Whether the proxy configuration was successful",
                messageDescription = "An optional status message informing about errors during execution"
            )
            put("serial", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The device serial number"))
            })
            put("enabled", buildJsonObject {
                put("type", JsonPrimitive("boolean"))
                put("description", JsonPrimitive("Whether the proxy is enabled"))
            })
            put("host", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The proxy server host (if enabled and successful)"))
            })
            put("port", buildJsonObject {
                put("type", JsonPrimitive("integer"))
                put("description", JsonPrimitive("The proxy server port (if enabled and successful)"))
            })
            put("proxy", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The proxy configuration string (if enabled and successful)"))
            })
            put("currentValue", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The current proxy configuration value (if verification was successful)"))
            })
            put("error", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("An error message (if the operation failed)"))
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
    val enabled = arguments["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
    val host = arguments["host"]?.jsonPrimitive?.contentOrNull
    val port = arguments["port"]?.jsonPrimitive?.intOrNull

    try {
        val proxyValue = if (enabled) {
            // Validate required parameters when enabling proxy
            if (host.isNullOrBlank()) {
                return@createTool "Host parameter is required when enabling proxy".asStructuredResponse()
            }
            if (port == null || port < 1 || port > 65535) {
                return@createTool "Valid port parameter (1-65535) is required when enabling proxy".asStructuredResponse()
            }
            "$host:$port"
        } else {
            ":0"
        }

        // Execute the proxy configuration command
        val response = adb.execute(
            request = ShellCommandRequest("settings put global http_proxy $proxyValue"),
            serial = serial
        )

        val error = response.errorOutput.trim()
        val success = error.isBlank()

        // Verify the setting was applied by reading it back
        val verificationResult = if (success) {
            try {
                val verifyResponse = adb.execute(
                    request = ShellCommandRequest("settings get global http_proxy"),
                    serial = serial
                )
                verifyResponse.output.trim()
            } catch (e: Exception) {
                "Unable to verify: ${e.message}"
            }
        } else {
            null
        }

        // Build the result JSON
        val result = buildJsonObject {
            put("serial", JsonPrimitive(serial))
            put("success", JsonPrimitive(success))
            put("enabled", JsonPrimitive(enabled))
            if (enabled && success) {
                put("host", JsonPrimitive(host!!))
                put("port", JsonPrimitive(port!!))
                put("proxy", JsonPrimitive(proxyValue))
            }
            if (verificationResult != null) {
                put("currentValue", JsonPrimitive(verificationResult))
            }
            if (!success) {
                put("error", JsonPrimitive(error))
            }
        }
        result.asStructuredResponse()
    } catch (e: Exception) {
        "Failed to configure proxy: ${e.message}".asStructuredResponse()
    }
}