package com.mikepenz.adbfriend.utils

import com.mikepenz.adbfriend.model.Package
import com.mikepenz.adbfriend.model.UsbInformation

private const val PACKAGES = "Packages:"
private const val COMMON_START = " "
private const val PACKAGE = "Package ["
private const val PACKAGE_VERSION_NAME = "versionName="
private const val PACKAGE_DATA_DIR = "dataDir="
private const val PACKAGE_LAST_UPDATE = "lastUpdateTime="
private const val PACKAGE_FIRST_INSTALL = "firstInstallTime="
private val PACKAGE_DETAILS = arrayOf(PACKAGE_VERSION_NAME, PACKAGE_DATA_DIR, PACKAGE_LAST_UPDATE, PACKAGE_FIRST_INSTALL)

fun packageParser(packageString: String, debug: Boolean = false, log: (String) -> Unit): List<Package> {
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
                        if (debug) log("  ⚠\uFE0F Not all details found for $currentPackage")
                    }
                    currentPackageDetails.clear()
                }

                // identify the current package we are reading
                val parts = trimmedLine.substring(PACKAGE.length).split("]")
                if (parts.size == 2) {
                    currentPackage = parts[0]
                } else {
                    log("  ⚠\uFE0F Found non expected package start ($trimmedLine)") // warning
                }
            } else if (trimmedLine.isBlank()) {
                currentPackage = null // no longer in a package
                currentPackageDetails.clear()
            } else if (currentPackage != null) {
                for (detail in PACKAGE_DETAILS) {
                    if (trimmedLine.startsWith(detail, true)) {
                        currentPackageDetails[detail] = trimmedLine.substring(detail.length)
                        break // if we had a match, no other string will match
                    }
                }
            } else {
                // we don't require this information at this time
            }
        } else {
            continue
        }
    }
    if (debug) collectedPackages.onEach { log("  ${it.packageName} - ${it.versionName}") }
    return collectedPackages
}

private const val SERIAL_NUMBER = "Serial Number: "
private const val SPEED = "Speed: "

fun usbProtocolParser(usbProtocolString: String = DEMO_STRING): List<UsbInformation> {
    val usbInformation: MutableList<UsbInformation> = mutableListOf()

    var serial: String? = null
    var speed: String? = null
    for (line in usbProtocolString.lineSequence()) {
        if (line.isNotBlank()) {
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith(SERIAL_NUMBER, true)) {
                serial = trimmedLine.substring(SERIAL_NUMBER.length)
            } else if (trimmedLine.startsWith(SPEED, true)) {
                speed = trimmedLine.substring(SPEED.length)
            }
            if (serial.isNullOrBlank().not() && speed.isNullOrBlank().not()) {
                usbInformation.add(UsbInformation(serial!!, speed!!))
            }
        } else {
            serial = null
            speed = null
        }
    }
    return usbInformation
}

const val DEMO_STRING = """
USB:

    USB 3.1 Bus:

      Host Controller Driver: AppleT8122USBXHCI

        Pixel 8 Pro:

          Product ID: 0x4ee7
          Vendor ID: 0x18d1  (Google Inc.)
          Version: 5.15
          Serial Number: 39211FDJG0010Z
          Speed: Up to 480 Mb/s
          Manufacturer: Google
          Location ID: 0x00100000 / 1
          Current Available (mA): 500
          Current Required (mA): 500
          Extra Operating Current (mA): 0

    USB 3.1 Bus:

      Host Controller Driver: AppleT8122USBXHCI
"""