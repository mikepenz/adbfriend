package com.mikepenz.adbfriend.subcommands

import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.malinskiy.adam.request.device.Device
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.mikepenz.adbfriend.model.Package
import com.mikepenz.adbfriend.utils.convertGlobToRegex
import com.mikepenz.adbfriend.utils.packageParser
import kotlin.system.exitProcess

abstract class AdbPackagesCommand(private val action: String) : AdbCommand() {
    protected val packagesGlob: String by option("--packages", metavar = "glob").required().help("Provide the filter to fetch the package or package glob of apps to uninstall.")
    protected var completeSuccess = true
    override suspend fun runWithAdb(devices: List<Device>) {
        try {
            for (device in devices) {
                echo("\uD83D\uDCF1 Execute uninstall for ${device.serial}")

                val response = adb.execute(request = ShellCommandRequest("dumpsys package packages"), serial = device.serial)

                val packages = packageParser(response.output) { log -> echo(log) }
                val regex = convertGlobToRegex(packagesGlob)
                val filtered = packages.filter { regex.matches(it.packageName) }

                if (filtered.isEmpty()) {
                    echo("⚠\uFE0F No applications matching provided packages filter.")
                    completeSuccess = false
                    continue
                }

                echo("  ℹ\uFE0F Found ${filtered.size} apps matching the filter:")
                filtered.onEach {
                    echo("     ${it.packageName} (${it.versionName})")
                }

                runForPackages(device, filtered)
            }

            echo()
            if (completeSuccess) {
                echo("✨ Completed Successfully")
            } else {
                echo("⚠\uFE0F Completed with a warning")
            }
            exitProcess(0)
        } catch (t: Throwable) {
            echo("⁉\uFE0F Failed to $action: ${t.message}")
            exitProcess(1)
        }
    }

    abstract suspend fun runForPackages(device: Device, packages: List<Package>)
}