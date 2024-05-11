package com.mikepenz.adbfriend.extensions

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.device.Device
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest

fun String.escapeForSync() = replace("$", "\$")
fun String.escapeForMD5() = replace("\$", "\\$")

/**
 * Fetches the model from the device
 */
suspend inline fun AndroidDebugBridgeClient.fetchModel(device: Device, crossinline echo: (String) -> Unit): String? {
    val result = execute(
        request = ShellCommandRequest("getprop ro.product.model"), serial = device.serial
    )
    result.errorOutput.trim().takeIf { it.isNotBlank() }?.let {
        echo("  ⚠\uFE0F Failed to fetch `ro.product.model` ($it)")
    }
    return result.output.trim()
}
