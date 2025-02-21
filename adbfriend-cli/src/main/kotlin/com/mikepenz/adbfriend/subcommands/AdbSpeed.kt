package com.mikepenz.adbfriend.subcommands

import com.github.ajalt.clikt.core.Context
import com.malinskiy.adam.request.device.Device
import com.mikepenz.adbfriend.extensions.fetchModel
import com.mikepenz.adbfriend.utils.usbProtocolParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class AdbSpeed : AdbCommand() {

    override fun help(context: Context) = """
        Prints the USB speed of connected devices. Only supported on MacOS X.
    """.trimIndent()

    override suspend fun runWithAdb(devices: List<Device>) {
        try {
            val osName = System.getProperty("os.name")
            if (osName.contains("mac", true)) {
                echo("ℹ\uFE0F Fetching USB Speed information")

                val proc = withContext(Dispatchers.IO) {
                    ProcessBuilder("system_profiler", "SPUSBDataType")
                        .redirectOutput(ProcessBuilder.Redirect.PIPE)
                        .redirectError(ProcessBuilder.Redirect.PIPE)
                        .start().also {
                            it.waitFor(60, TimeUnit.MINUTES)
                        }
                }
                proc.errorReader().readText().trim().takeIf { it.isNotBlank() }?.let {
                    echo("⚠\uFE0F Failed to retrieve `system_profiler SPUSBDataType` ($it)")
                    exitProcess(1)
                }
                val usbInformation = usbProtocolParser(proc.inputReader().readText())

                devices.onEach { device ->
                    val deviceModel = adb.fetchModel(device) {
                        echo(it)
                    }
                    val match = usbInformation.firstOrNull { it.serial.equals(device.serial, true) }
                    val messagePrefix = if (deviceModel.isNullOrBlank()) device.serial else "$deviceModel (${device.serial})"
                    if (match != null) {
                        val speedMessage = if (match.speed.contains("Mb/s", true)) {
                            "\uD83D\uDE82 ${match.speed}"
                        } else {
                            "\uD83D\uDE85\uD83D\uDCA8 ${match.speed}"
                        }

                        echo("  \uD83D\uDCF1 $messagePrefix connected with $speedMessage")
                    } else {
                        echo("  ⚠\uFE0F failed to retrieve speed for $messagePrefix ")
                    }
                }
            } else {
                echo("⁉\uFE0F The `adbspeed` tool is only available for MacOS X")
                exitProcess(1)
            }

            echo()
            echo("✨ Completed Successfully")
            exitProcess(0)
        } catch (t: Throwable) {
            echo("⁉\uFE0F Could not complete requested test operations: ${t.message}")
            exitProcess(1)
        }
    }
}