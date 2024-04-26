package com.mikepenz.adbfriend

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.AndroidDebugBridgeClientFactory
import com.malinskiy.adam.interactor.StartAdbInteractor
import com.malinskiy.adam.request.device.Device
import com.malinskiy.adam.request.device.ListDevicesRequest
import com.malinskiy.adam.request.misc.GetAdbServerVersionRequest
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Base command with the general adb setup
 */
abstract class AdbCommand : CliktCommand() {
    protected val config by requireObject<Config>()

    protected lateinit var adb: AndroidDebugBridgeClient

    override fun run() = runBlocking {
        StartAdbInteractor().execute() //Start the adb server
        adb = AndroidDebugBridgeClientFactory().build() // Create adb client

        try {
            // Get ADB Version
            val version: Int = adb.execute(request = GetAdbServerVersionRequest())
            echo("ℹ\uFE0F This machine uses ADB with version: ${version}.")

            // Get all devices
            val devices: List<Device> = adb.execute(request = ListDevicesRequest())
            if (devices.isEmpty()) {
                echo("⚠\uFE0F Didn't detect active devices connected via ADB.")
                exitProcess(1)
            }

            // filter to devices as defined by input
            val serialFilter = config.serials
            val filteredDevices = if (serialFilter?.isNotEmpty() == true) {
                devices.filter { serialFilter.contains(it.serial) }
            } else devices

            if (filteredDevices.isEmpty()) {
                echo("⚠\uFE0F No device matched the `--serials` input.")
                exitProcess(1)
            }

            runWithAdb(filteredDevices)
        } finally {
            adb.close()
        }
    }

    abstract suspend fun runWithAdb(devices: List<Device>)
}