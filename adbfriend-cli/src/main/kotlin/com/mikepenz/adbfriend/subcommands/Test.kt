package com.mikepenz.adbfriend.subcommands

import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.terminal.YesNoPrompt
import com.malinskiy.adam.request.device.Device
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import kotlin.system.exitProcess

class Test : AdbCommand() {
    private val configure: Boolean by option().flag("--reset", default = true).help("Configures the device for tests (disable animations)")
    private val immersiveMode: Boolean by option().flag().help("Also sets the `immersive_mode_confirmation` confirmation when configuring")
    private val resetAutofill: Boolean by option().flag().help("Also sets the `autofill_service` to null when configuring")
    private val touches: Boolean by option().flag().help("Also enables touches when configuring for tests (`--reset` will disable again)")
    private val unlock: Boolean by option().flag().help("Attempts to unlock the device by sending (`keyevent 82`)")
    private val collapse: Boolean by option().flag().help("Attempts to collapse the statusbar")
    private val force: Boolean by option().flag().help("Skips all warning prompts, and applies settings without confirmation.")

    override suspend fun runWithAdb(devices: List<Device>) {
        try {
            val targetScale: Int
            val targetTouchMode: Int
            if (configure) {
                echo("ℹ\uFE0F Preparing the devices for tests (disable animations)")
                echo()
                targetScale = 0
                targetTouchMode = 1
            } else {
                echo("ℹ\uFE0F Resetting test preparation for devices (re-enable animations)")
                echo()
                targetScale = 1
                targetTouchMode = 0
            }

            //
            var resetAutoFillConfirmed = false
            if (force && resetAutofill) {
                resetAutoFillConfirmed = resetAutofill
            } else if (configure && resetAutofill && YesNoPrompt("ℹ\uFE0F Are you sure you want to reset the autofill service?", terminal, default = false).ask() == true) {
                echo("⚠\uFE0F Will reset auto_fill service for devices!")
                resetAutoFillConfirmed = true
            }

            var completeSuccess = true
            devices.onEach { device ->
                echo("\uD83D\uDCF1 Execute sync for ${device.serial}")

                var success = true
                animationFlags.onEach { flag ->
                    adb.execute(
                        request = ShellCommandRequest("settings put global $flag $targetScale"), serial = device.serial
                    ).errorOutput.trim().takeIf { it.isNotBlank() }?.let {
                        success = false
                        completeSuccess = false
                        echo("  ⚠\uFE0F Failed to set the flag: $flag ($it)")
                    }
                }
                if (success) echo("  ✅ Animation flags applied.")

                if (touches) {
                    adb.execute(
                        request = ShellCommandRequest("settings put system $touchesFlag $targetTouchMode"), serial = device.serial
                    ).errorOutput.trim().takeIf { it.isNotBlank() }?.let {
                        completeSuccess = false
                        echo("  ⚠\uFE0F Failed to enable touches ($it)")
                    } ?: echo("  ✅ Touches enabled.")

                }

                if (configure && immersiveMode) {
                    adb.execute(
                        request = ShellCommandRequest("settings put secure immersive_mode_confirmation confirmed"), serial = device.serial
                    ).errorOutput.trim().takeIf { it.isNotBlank() }?.let {
                        completeSuccess = false
                        echo("  ⚠\uFE0F Failed to set immersive mode ($it)")
                    } ?: echo("  ✅ Immersive mode `confirmed`.")
                }

                if (configure && resetAutoFillConfirmed) {
                    val result = adb.execute(
                        request = ShellCommandRequest("settings get secure autofill_service"), serial = device.serial
                    )

                    if (result.errorOutput.trim().isNotBlank()) {
                        echo("  ⚠\uFE0F Failed to retrieve the current `autofill_service` value. (${result.errorOutput.trim()})")
                    } else {
                        val response = result.output.trim()
                        echo("  ℹ\uFE0F `autofill_service` was set to: `$response`.")
                        echo("      Re-enable manually: `adb shell settings put secure autofill_service $response`")

                        adb.execute(
                            request = ShellCommandRequest("settings put secure autofill_service null"), serial = device.serial
                        ).errorOutput.trim().takeIf { it.isNotBlank() }?.let {
                            completeSuccess = false
                            echo("  ⚠\uFE0F Failed to set `auto_fill` mode ($it)")
                        } ?: echo("  ✅ `auto_fill` service set to `null`.")
                    }
                }

                if (unlock) {
                    repeat(2) { adb.execute(request = ShellCommandRequest("input keyevent 82"), serial = device.serial) }
                    echo("  ℹ\uFE0F Device unlock attempted.")
                }

                if (collapse) {
                    adb.execute(request = ShellCommandRequest("cmd statusbar collapse"), serial = device.serial)
                    echo("  ℹ\uFE0F Statusbar collapse attempted.")
                }
            }

            echo()
            if (completeSuccess) {
                echo("✨ Completed Successfully")
            } else {
                echo("⚠\uFE0F Completed with a warning")
            }
            exitProcess(0)
        } catch (t: Throwable) {
            echo("⁉\uFE0F Could not complete requested test operations: ${t.message}")
            exitProcess(1)
        }
    }

    companion object {
        private val animationFlags = arrayOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        private val touchesFlag = "show_touches"
    }
}