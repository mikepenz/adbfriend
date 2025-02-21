package com.mikepenz.adbfriend.subcommands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.malinskiy.adam.request.device.Device
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.mikepenz.adbfriend.model.Package
import kotlin.system.exitProcess

class Packages : AdbPackagesCommand("packages") {
    private val immersiveFull: Boolean by option().flag().help("Sets the `immersive.full` for the identified package.")
    private val forceStop: Boolean by option().flag().help("Force stops the identified apps.")
    private val clear: Boolean by option().flag().help("Clears the app data and app cache.")

    override fun help(context: Context) = """
        This tool offers to manage packages on the connected devices. Specifically force stop or clear the app data for packages matching the pattern. 
        It can also apply the `immersive.full` flag to the identified packages.
    """.trimIndent()

    override fun run() {
        if (!immersiveFull && !forceStop && !clear) {
            echo("⁉\uFE0F At least one option has to be provided!")
            echoFormattedHelp()
            exitProcess(1)
        }
        super.run()
    }

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

        if (clear) {
            var success = true
            packages.onEach { p ->
                adb.execute(
                    request = ShellCommandRequest("pm clear ${p.packageName}"), serial = device.serial
                ).errorOutput.trim().takeIf { it.isNotBlank() }?.let {
                    success = false
                    completeSuccess = false
                    echo("  ⚠\uFE0F Failed to clear app data: ${p.packageName} ($it)")
                }
            }
            if (success) echo("  ✅ Clearing app data for all successfully.")
        }

        if (forceStop) {
            var success = true
            packages.onEach { p ->
                adb.execute(
                    request = ShellCommandRequest("am force-stop ${p.packageName}"), serial = device.serial
                ).errorOutput.trim().takeIf { it.isNotBlank() }?.let {
                    success = false
                    completeSuccess = false
                    echo("  ⚠\uFE0F Failed to force stop: ${p.packageName} ($it)")
                }
            }
            if (success) echo("  ✅ Force stopped all successfully.")
        }
    }
}