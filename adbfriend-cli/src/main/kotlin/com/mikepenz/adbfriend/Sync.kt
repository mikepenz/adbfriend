package com.mikepenz.adbfriend

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.AndroidDebugBridgeClientFactory
import com.malinskiy.adam.interactor.StartAdbInteractor
import com.malinskiy.adam.request.Feature
import com.malinskiy.adam.request.device.Device
import com.malinskiy.adam.request.device.ListDevicesRequest
import com.malinskiy.adam.request.misc.GetAdbServerVersionRequest
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.malinskiy.adam.request.shell.v2.ShellCommandResult
import com.malinskiy.adam.request.sync.v2.ListFileRequest
import com.malinskiy.adam.request.sync.v2.PushFileRequest
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.roundToInt
import kotlin.system.exitProcess

class Sync : CliktCommand() {
    private val remotePath: String by option().required().help("The Remote Path of where to sync the data")
    private val localPath: String by option().required().help("The Local path of data to sync to the phone")
    private val excludes: List<String> by option().split(";").default(listOf(".DS_Store", ".git", ".lfs")).help("Excluded files or folders, delimited by `;`")
    private val deleteMissing: Boolean by option().flag().help("Delete file on the remote not existing locally")
    private val md5Compare: Boolean by option().flag().help("Compare MD5 to check equality")
    private val pushTimeout: Long by option().long().default(5_000L).help("Defines the timeout after how many ms a push should fail without progress")
    private val retryCount: Int by option().int().default(3).help("Defines the amount of retries")
    private val failOnError: Boolean by option().flag().help("Defines if the action shall fatally fail if a single file fails to push")
    private val config by requireObject<Config>()

    private lateinit var adb: AndroidDebugBridgeClient

    override fun run() = runBlocking {
        try {
            val localFile = File(localPath)
            require(localFile.exists()) { "The local file does not exist." }

            StartAdbInteractor().execute() //Start the adb server
            adb = AndroidDebugBridgeClientFactory().build() // Create adb client

            // Get ADB Version
            val version: Int = adb.execute(request = GetAdbServerVersionRequest())
            echo("ℹ\uFE0F This machine uses ADB with version: $version")

            // Get all devices
            val devices: List<Device> = adb.execute(request = ListDevicesRequest())
            if (devices.isEmpty()) {
                echo("⚠\uFE0F Didn't detect active devices connected via ADB")
                exitProcess(1)
            }

            // filter to devices as defined by input
            val serialFilter = config.serials
            val filteredDevices = if (serialFilter?.isNotEmpty() == true) {
                devices.filter { serialFilter.contains(it.serial) }
            } else devices

            compare(filteredDevices, remotePath, localFile)

            echo("✨ Done                        ")

            exitProcess(0)
        } catch (t: Throwable) {
            echo("⁉\uFE0F Pushing the files failed with error: ${t.message}")
            exitProcess(1)
        }
    }


    private suspend fun compare(devices: List<Device>, remote: String, local: File) {
        if (excludes.contains(local.name)) {
            echo("⁉\uFE0F Exclude rule, excludes the root folder")
            return
        }

        val localFiles = local.mapped()
        devices.forEach { device ->
            device.compare(local.name, remote, localFiles, 1)
        }
    }

    private suspend fun Device.compare(name: String, remote: String, localFiles: Map<String, File>, level: Int, prefix: String = "") {
        val device = this
        val remoteFiles = adb.execute(ListFileRequest(remote, listOf(Feature.LS_V2, Feature.STAT_V2)), device.serial)
            .filter { it.name != null }
            .associateBy { it.name!! }

        echo("$prefix$name")
        val levelInset = "  "

        localFiles.onEach { (localName, localFile) ->
            val fullRemotePath = remote + File.separatorChar + localName.replace("$", "\$")
            if (localFile.isDirectory) {
                device.compare(localFile.name, fullRemotePath, localFile.mapped(), level + 1, levelInset)
            } else if (remoteFiles.containsKey(localName)) {
                val localSize = localFile.length()
                val remoteFile = remoteFiles[localName]!!
                val remoteSize = remoteFile.size.toLong()

                // is there remote, identify if it needs an update
                if (localSize != remoteSize) {
                    echo("$prefix$levelInset⬆\uFE0F $localName :: File was modified. Pushing it")
                    device.pushFile(localFile, fullRemotePath)
                } else {
                    if (md5Compare && !device.compareMd5(localFile, fullRemotePath)) {
                        echo("$prefix$levelInset⬆\uFE0F $localName :: File was modified. Pushing it")
                        device.pushFile(localFile, fullRemotePath)
                    } else {
                        echo("$prefix$levelInset✅ $localName :: File is up to date.")
                    }
                }
            } else {
                echo("$prefix$levelInset\uD83C\uDD95 $localName :: File was missing. Pushing update.")
                device.pushFile(localFile, fullRemotePath)
            }
        }
        remoteFiles.onEach { (name, _) ->
            val fullRemotePath = remote + File.separatorChar + name
            if (!excludes.contains(name) && !localFiles.containsKey(name)) {
                // file remote, but not local, remove...
                if (deleteMissing) {
                    echo("$prefix$levelInset\uD83D\uDDD1\uFE0F $name :: exists only remote. Deleting.")
                    device.deleteFile(fullRemotePath)
                } else {
                    echo("$prefix$levelInset$name :: exists only remote")
                }
            }
        }

    }

    private fun Device.pushFile(localFile: File, remote: String): Unit = runBlocking {
        if (config.dryRun) return@runBlocking
        val device = this@pushFile
        var attempt = 0
        try {
            retry(retryCount, 1_000L, preRetry = {
                adb.close() // if we failed try to redo adb
                adb = AndroidDebugBridgeClientFactory().build()
            }) {
                attempt++
                val result = withResettableTimeoutOrNull(pushTimeout) {
                    val channel = adb.execute(
                        PushFileRequest(
                            local = localFile,
                            remotePath = remote.escapeForSync(),
                            listOf(Feature.LS_V2, Feature.STAT_V2, Feature.SENDRECV_V2),
                            mode = "0644"
                        ),
                        scope = this,
                        serial = device.serial
                    )

                    timeoutCallback = {
                        channel.cancel()
                    }

                    var percentage: Int
                    for (percentageDouble in channel) {
                        percentage = (percentageDouble * 100).roundToInt()
                        terminal.rawPrint("\rPushing: $percentage%")
                        reset()
                    }
                    terminal.rawPrint("\r")
                    true
                }
                if (result == null) throw Exception("Failed while pushing in attempt $attempt")
            }
        } catch (t: Throwable) {
            terminal.rawPrint("\r-- ❌ Timed out $attempt times while pushing $remote (Reason: ${t.message}\n")
            if (failOnError) {
                throw t
            }
        }
    }

    private fun Device.deleteFile(remote: String): Unit = runBlocking {
        if (config.dryRun) return@runBlocking
        val device = this@deleteFile
        adb.execute(request = ShellCommandRequest("rm \"${remote.escapeForSync()}\""), serial = device.serial)
    }

    private fun Device.compareMd5(localFile: File, remote: String): Boolean = runBlocking {
        // get md5 of file (only if file size was equal)
        val device = this@compareMd5
        val response: ShellCommandResult = adb.execute(request = ShellCommandRequest("md5sum \"${remote.escapeForMD5()}\""), serial = device.serial)
        val parts = response.output.split("  ")
        return@runBlocking if (parts.size == 2) {
            localFile.md5() == parts[0]
        } else {
            echo("⁉\uFE0F Could not md5 compare file.")
            false
        }
    }

    private fun File.mapped() = (listFiles() ?: emptyArray()).filterNot { excludes.contains(it.name) }.associateBy { it.name }
}