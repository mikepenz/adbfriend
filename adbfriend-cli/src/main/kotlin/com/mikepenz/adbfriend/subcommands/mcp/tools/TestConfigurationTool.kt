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
 * Input schema for test configuration operations
 */
internal val TEST_CONFIGURATION_TOOL_INPUT = Tool.Input(
    properties = buildJsonObject {
        put("serial", SERIAL_INPUT)
        put("animations", buildJsonObject {
            put("type", JsonPrimitive("boolean"))
            put("description", JsonPrimitive("Whether to enable or disable animations on the device for tests. (Set to `false` to disable animations when to run tests)"))
            put("default", JsonPrimitive(true))
        })
        put("immersiveMode", buildJsonObject {
            put("type", JsonPrimitive("boolean"))
            put("description", JsonPrimitive("Whether to set the immersive_mode_confirmation on the device when configuring"))
            put("default", JsonPrimitive(false))
        })
        put("resetAutofill", buildJsonObject {
            put("type", JsonPrimitive("boolean"))
            put("description", JsonPrimitive("Whether to set the autofill_service to null on the device when configuring"))
            put("default", JsonPrimitive(false))
        })
        put("touches", buildJsonObject {
            put("type", JsonPrimitive("boolean"))
            put("description", JsonPrimitive("Whether to enable or disable touches when configuring the device for tests. (When we want to run tests set to `true`)"))
            put("default", JsonPrimitive(false))
        })
        put("unlock", buildJsonObject {
            put("type", JsonPrimitive("boolean"))
            put("description", JsonPrimitive("Whether to attempt to unlock the device. Attempt this when to run tests."))
            put("default", JsonPrimitive(false))
        })
        put("collapse", buildJsonObject {
            put("type", JsonPrimitive("boolean"))
            put("description", JsonPrimitive("Whether to attempt to collapse the statusbar. Attempt this when to run tests."))
        })
    },
    required = listOf("serial")
)

/**
 * Creates a tool for configuring Android devices for testing.
 * This tool can disable animations, set immersive mode, reset autofill service, enable touches,
 * unlock the device, and collapse the statusbar.
 */
fun createTestConfigurationTool(adb: AndroidDebugBridgeClient): RegisteredTool = createTool(
    name = "configure-test-device",
    description = """
        Configures an Android device for testing by setting various flags and options.
        Can disable animations, set immersive mode, reset autofill service, enable touches,
        unlock the device, and collapse the statusbar.
    """.trimIndent(),
    inputSchema = TEST_CONFIGURATION_TOOL_INPUT,
    outputSchema = Tool.Output(
        properties = buildJsonObject {
            applyDefaultOutputSchema(
                successDescription = "Whether the device configuration operations executed successfully",
                messageDescription = "An optional status message informing about errors during execution"
            )
            put("serial", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("The device serial number"))
            })
            put("completeSuccess", buildJsonObject {
                put("type", JsonPrimitive("boolean"))
                put("description", JsonPrimitive("Whether all operations were successful"))
            })
            put("operations", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("description", JsonPrimitive("List of operations performed on the device"))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("operation", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The name of the operation"))
                        })
                        put("success", buildJsonObject {
                            put("type", JsonPrimitive("boolean"))
                            put("description", JsonPrimitive("Whether the operation was successful"))
                        })
                        put("details", buildJsonObject {
                            put("type", JsonPrimitive("array"))
                            put("description", JsonPrimitive("Additional details about the operation (for animations)"))
                        })
                        put("error", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("An error message if the operation failed"))
                        })
                        put("value", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The current value (for immersiveMode)"))
                        })
                        put("previousValue", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("The previous value (for resetAutofillService)"))
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
    val animations = arguments["animations"]?.jsonPrimitive?.booleanOrNull ?: true
    val immersiveMode = arguments["immersiveMode"]?.jsonPrimitive?.booleanOrNull ?: false
    val resetAutofill = arguments["resetAutofill"]?.jsonPrimitive?.booleanOrNull ?: false
    val touches = arguments["touches"]?.jsonPrimitive?.booleanOrNull ?: false
    val unlock = arguments["unlock"]?.jsonPrimitive?.booleanOrNull ?: false
    val collapse = arguments["collapse"]?.jsonPrimitive?.booleanOrNull ?: false

    try {
        // Track success of operations
        var completeSuccess = true
        val results = mutableListOf<JsonObject>()

        // Apply animation flags
        val animationResults = mutableListOf<JsonObject>()
        var animationSuccess = true

        ANIMATION_FLAGS.forEach { flag ->
            val response = adb.execute(
                request = ShellCommandRequest("settings put global $flag ${if (animations) 0 else 1}"),
                serial = serial
            )

            val error = response.errorOutput.trim()
            if (error.isNotBlank()) {
                animationSuccess = false
                completeSuccess = false
                animationResults.add(buildJsonObject {
                    put("flag", JsonPrimitive(flag))
                    put("success", JsonPrimitive(false))
                    put("error", JsonPrimitive(error))
                })
            } else {
                animationResults.add(buildJsonObject {
                    put("flag", JsonPrimitive(flag))
                    put("success", JsonPrimitive(true))
                })
            }
        }

        results.add(buildJsonObject {
            put("operation", JsonPrimitive("animations"))
            put("success", JsonPrimitive(animationSuccess))
            put("details", JsonArray(animationResults))
        })

        // Apply touches setting if requested
        val response = adb.execute(
            request = ShellCommandRequest("settings put system $TOUCHES_FLAG ${if (touches) 1 else 0}"),
            serial = serial
        )

        val error = response.errorOutput.trim()
        val touchesSuccess = error.isBlank()
        if (!touchesSuccess) {
            completeSuccess = false
        }

        results.add(buildJsonObject {
            put("operation", JsonPrimitive("touches"))
            put("success", JsonPrimitive(touchesSuccess))
            if (!touchesSuccess) {
                put("error", JsonPrimitive(error))
            }
        })

        // Apply immersive mode if requested
        if (immersiveMode) {
            val response = adb.execute(
                request = ShellCommandRequest("settings put secure immersive_mode_confirmation confirmed"),
                serial = serial
            )

            val error = response.errorOutput.trim()
            val immersiveSuccess = error.isBlank()
            if (!immersiveSuccess) {
                completeSuccess = false
                results.add(buildJsonObject {
                    put("operation", JsonPrimitive("immersiveMode"))
                    put("success", JsonPrimitive(false))
                    put("error", JsonPrimitive(error))
                })
            } else {
                val validate = adb.execute(
                    request = ShellCommandRequest("settings get secure immersive_mode_confirmation"),
                    serial = serial
                ).output.trim()

                results.add(buildJsonObject {
                    put("operation", JsonPrimitive("immersiveMode"))
                    put("success", JsonPrimitive(true))
                    put("value", JsonPrimitive(validate))
                })
            }
        }

        // Reset autofill service if requested and confirmed
        if (resetAutofill) {
            // First get the current value
            val getResult = adb.execute(
                request = ShellCommandRequest("settings get secure autofill_service"),
                serial = serial
            )

            val getError = getResult.errorOutput.trim()
            if (getError.isNotBlank()) {
                completeSuccess = false
                results.add(buildJsonObject {
                    put("operation", JsonPrimitive("getAutofillService"))
                    put("success", JsonPrimitive(false))
                    put("error", JsonPrimitive(getError))
                })
            } else {
                val currentValue = getResult.output.trim()

                // Now set it to null
                val setResult = adb.execute(
                    request = ShellCommandRequest("settings put secure autofill_service null"),
                    serial = serial
                )

                val setError = setResult.errorOutput.trim()
                val autofillSuccess = setError.isBlank()
                if (!autofillSuccess) {
                    completeSuccess = false
                }

                results.add(buildJsonObject {
                    put("operation", JsonPrimitive("resetAutofillService"))
                    put("success", JsonPrimitive(autofillSuccess))
                    put("previousValue", JsonPrimitive(currentValue))
                    if (!autofillSuccess) {
                        put("error", JsonPrimitive(setError))
                    }
                })
            }
        }

        // Unlock device if requested
        if (unlock) {
            try {
                repeat(2) {
                    adb.execute(
                        request = ShellCommandRequest("input keyevent 82"),
                        serial = serial
                    )
                }

                results.add(buildJsonObject {
                    put("operation", JsonPrimitive("unlock"))
                    put("success", JsonPrimitive(true))
                })
            } catch (e: Exception) {
                completeSuccess = false
                results.add(buildJsonObject {
                    put("operation", JsonPrimitive("unlock"))
                    put("success", JsonPrimitive(false))
                    put("error", JsonPrimitive(e.message ?: "Unknown error"))
                })
            }
        }

        // Collapse statusbar if requested
        if (collapse) {
            try {
                adb.execute(
                    request = ShellCommandRequest("cmd statusbar collapse"),
                    serial = serial
                )

                results.add(buildJsonObject {
                    put("operation", JsonPrimitive("collapseStatusbar"))
                    put("success", JsonPrimitive(true))
                })
            } catch (e: Exception) {
                completeSuccess = false
                results.add(buildJsonObject {
                    put("operation", JsonPrimitive("collapseStatusbar"))
                    put("success", JsonPrimitive(false))
                    put("error", JsonPrimitive(e.message ?: "Unknown error"))
                })
            }
        }

        // Build the final result
        buildJsonObject {
            put("success", JsonPrimitive(true))
            put("serial", JsonPrimitive(serial))
            put("completeSuccess", JsonPrimitive(completeSuccess))
            put("operations", JsonArray(results))
        }.asStructuredResponse()
    } catch (e: Exception) {
        "Failed to configure device for testing: ${e.message}".asStructuredResponse()
    }
}

private val ANIMATION_FLAGS = arrayOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
private val TOUCHES_FLAG = "show_touches"
