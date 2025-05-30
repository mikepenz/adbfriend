package com.mikepenz.adbfriend.subcommands.mcp.tools

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.device.Device

fun buildTools(adb: AndroidDebugBridgeClient, devices: List<Device>) = buildList {
    add(createCheckAdbSpeedTool())
    addAll(createInstalledPackageTools(adb))
    add(createConnectedDevicesTool(adb, devices))
    add(createGetInstalledPackagesTool(adb))
    addAll(createFileSystemTools(adb))
}
