package com.mikepenz.adbfriend

import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.malinskiy.adam.request.device.Device
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import kotlin.system.exitProcess

class Uninstall : AdbCommand() {
    private val force: Boolean by option().flag().help("Skips all warning prompts, and applies settings without confirmation.")

    override suspend fun runWithAdb(devices: List<Device>) {
        try {
            devices.onEach { device ->
                echo("\uD83D\uDCF1 Execute uninstall for ${device.serial}")


                val response = adb.execute(request = ShellCommandRequest("dumpsys package packages"), serial = device.serial)

                val packages = packageParser(response.output)
                packages.onEach {
                    echo("  ${it.packageName} - ${it.versionName}")
                }

            }

            echo()
            echo("✨ Completed Successfully")
            exitProcess(0)
        } catch (t: Throwable) {
            echo("⁉\uFE0F Failed to uninstall: ${t.message}")
            exitProcess(1)
        }
    }


    private fun packageParser(packageString: String): List<Package> {
        val collectedPackages: MutableList<Package> = mutableListOf()
        var inPackages = true
        var currentPackage: String? = null
        val currentPackageDetails: MutableMap<String, String> = mutableMapOf()

        for (line in packageString.lineSequence()) {
            if (line.startsWith(PACKAGES, true)) {
                inPackages = true
                continue
            } else if (!line.startsWith(COMMON_START, true)) {
                inPackages = false
            }

            if (inPackages) {
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith(PACKAGE, true)) {
                    // new package, check if we had everything for the prior, reset
                    if (currentPackage != null && currentPackageDetails.isNotEmpty()) {
                        if (currentPackageDetails.size == PACKAGE_DETAILS.size) {
                            // got all details
                            collectedPackages.add(
                                Package(
                                    currentPackage,
                                    currentPackageDetails[PACKAGE_VERSION_NAME]!!,
                                    currentPackageDetails[PACKAGE_DATA_DIR]!!
                                )
                            )
                        } else {
                            echo("  ⚠\uFE0F Not all details found for $currentPackage")
                        }
                        currentPackageDetails.clear()
                    }

                    // identify the current package we are reading
                    val parts = trimmedLine.substring(PACKAGE.length).split("]")
                    if (parts.size == 2) {
                        currentPackage = parts[0]
                    } else {
                        // warning
                        echo("  ⚠\uFE0F Found non expected package start ($trimmedLine)")
                    }
                } else if (trimmedLine.isBlank()) {
                    currentPackage = null // no longer in a package
                    currentPackageDetails.clear()
                } else if (currentPackage != null) {
                    for (detail in PACKAGE_DETAILS) {
                        if (trimmedLine.startsWith(detail, true)) {
                            currentPackageDetails[detail] = trimmedLine.substring(detail.length)
                        }
                    }
                } else {
                    // we don't require this information at this time
                }
            } else {
                continue
            }
        }

        return collectedPackages
    }

    data class Package(
        val packageName: String,
        val versionName: String,
        val dataDir: String,
    )

    companion object {
        private const val PACKAGES = "Packages:"
        private const val COMMON_START = " "
        private const val PACKAGE = "Package ["
        private const val PACKAGE_VERSION_NAME = "versionName="
        private const val PACKAGE_DATA_DIR = "dataDir="
        private const val PACKAGE_LAST_UPDATE = "lastUpdateTime="
        private const val PACKAGE_FIRST_INSTALL = "firstInstallTime="
        private val PACKAGE_DETAILS = arrayOf(PACKAGE_VERSION_NAME, PACKAGE_DATA_DIR, PACKAGE_LAST_UPDATE, PACKAGE_FIRST_INSTALL)
    }
}