package com.mikepenz.adbfriend

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
            } else if (configure && resetAutofill && YesNoPrompt("ℹ\uFE0F Are you sure you want to reset the autofill service?", terminal).ask() == true) {
                echo("⚠\uFE0F Will reset auto_fill service for devices!")
                resetAutoFillConfirmed = true
            }

            devices.onEach { device ->
                echo("\uD83D\uDCF1 Execute sync for ${device.serial}")

                animationFlags.onEach { flag ->
                    adb.execute(
                        request = ShellCommandRequest("settings put global $flag $targetScale"), serial = device.serial
                    )
                }
                echo("  ✅ Animation flags applied.")

                if (touches) {
                    adb.execute(
                        request = ShellCommandRequest("settings put system $touchesFlag $targetTouchMode"), serial = device.serial
                    )
                    echo("  ✅ Touches enabled.")
                }

                if (configure && immersiveMode) {
                    adb.execute(
                        request = ShellCommandRequest("settings put secure immersive_mode_confirmation confirmed"), serial = device.serial
                    )
                    echo("  ✅ Immersive mode `confirmed`.")
                }

                if (configure && resetAutoFillConfirmed) {
                    val result = adb.execute(
                        request = ShellCommandRequest("settings get secure autofill_service "), serial = device.serial
                    ).output.lines().firstOrNull() ?: ""
                    echo("  ℹ\uFE0F `autofill_service` was set to: `$result`.")
                    echo("      Re-enable manually: `adb shell settings put secure autofill_service $result`")

                    adb.execute(
                        request = ShellCommandRequest("settings put secure autofill_service null"), serial = device.serial
                    )
                    echo("  ✅ `auto_fill` service set to `null`.")
                }

                if (unlock) {
                    repeat(2) { adb.execute(request = ShellCommandRequest("input keyevent 82"), serial = device.serial) }
                    echo("  ℹ\uFE0F Device unlock attempted.")
                }
            }

            echo()
            echo("✨ Completed Successfully")
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