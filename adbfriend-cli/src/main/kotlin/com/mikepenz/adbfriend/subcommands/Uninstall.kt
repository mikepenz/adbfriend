package com.mikepenz.adbfriend.subcommands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.terminal.YesNoPrompt
import com.malinskiy.adam.request.device.Device
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.mikepenz.adbfriend.model.Package
import kotlin.system.exitProcess

class Uninstall : AdbPackagesCommand("uninstall") {
    private val keepData: Boolean by option().flag().help("Will keep the data when uninstalling. By default will also remove data.")
    private val force: Boolean by option().flag().help("Skips all warning prompts, and applies settings without confirmation.")

    override fun help(context: Context) = """
        This tool offers a flexible API to uninstall packages matching the pattern.
    """.trimIndent()

    override suspend fun runForPackages(device: Device, packages: List<Package>) {
        // ensure the person wants to install all
        if (!force && YesNoPrompt("ℹ\uFE0F Are you sure you want to uninstall the listed applications?", terminal, default = false).ask() != true) {
            echo("No app uninstalled.")
            exitProcess(1)
        }

        var success = true
        packages.onEach { p ->
            val cmd = StringBuilder().apply {
                append("pm uninstall ")
                if (keepData) {
                    append("-k ")
                }
                append(p.packageName)
            }.toString()

            adb.execute(
                request = ShellCommandRequest(cmd), serial = device.serial
            ).errorOutput.trim().takeIf { it.isNotBlank() }?.let {
                success = false
                completeSuccess = false
                echo("  ⚠\uFE0F Failed to uninstall: ${p.packageName} ($it)")
            }
        }
        if (success) echo("  ✅ Uninstalled successfully for ${device.serial}")
    }
}