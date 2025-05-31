package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.device.Device

fun buildTools(
    adb: AndroidDebugBridgeClient,
    devices: List<Device>,
    allowedPaths: List<String> = DEFAULT_ALLOWED_PATHS,
    hostAllowedPaths: List<String>? = null
) = buildList {
    add(createCheckAdbSpeedTool())
    addAll(createInstalledPackageTools(adb))
    add(createConnectedDevicesTool(adb, devices))
    add(createGetInstalledPackagesTool(adb))
    add(createTestConfigurationTool(adb))
    addAll(createFileSystemTools(adb, allowedPaths))
    add(createCaptureScreenshotTool(adb, hostAllowedPaths))
}
