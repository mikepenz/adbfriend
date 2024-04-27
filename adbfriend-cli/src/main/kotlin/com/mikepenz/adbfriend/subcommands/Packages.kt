package com.mikepenz.adbfriend.subcommands

import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.malinskiy.adam.request.device.Device
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest

class Packages : AdbPackagesCommand("packages") {
    private val immersiveFull: Boolean by option().flag().help("Sets the `immersive.full` for the identified package.")

    override suspend fun runForPackages(device: Device, packages: List<Package>) {

        if (immersiveFull) {
            var success = true
            packages.onEach { p ->
                adb.execute(
                    request = ShellCommandRequest("settings put global policy_control immersive.full=${p.packageName}"), serial = device.serial
                ).errorOutput.trim().takeIf { it.isNotBlank() }?.let {
                    success = false
                    completeSuccess = false
                    echo("  ⚠\uFE0F Failed to set `policy_control` for: ${p.packageName} ($it)")
                }
            }
            if (success) echo("  ✅ Applied `policy_control` successfully.")
        }
    }
}