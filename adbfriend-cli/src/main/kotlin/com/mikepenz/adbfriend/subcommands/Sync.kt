package com.mikepenz.adbfriend.subcommands

import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.malinskiy.adam.AndroidDebugBridgeClientFactory
import com.malinskiy.adam.request.Feature
import com.malinskiy.adam.request.device.Device
import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import com.malinskiy.adam.request.shell.v2.ShellCommandResult
import com.malinskiy.adam.request.sync.v2.ListFileRequest
import com.malinskiy.adam.request.sync.v2.PushFileRequest
import com.mikepenz.adbfriend.*
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.roundToInt
import kotlin.system.exitProcess

class Sync : AdbCommand() {
    private val remotePath: String by option().required().help("The Remote Path of where to sync the data")
    private val localPath: String by option().required().help("The Local path of data to sync to the phone")
    private val excludes: List<String> by option().split(";").default(listOf(".DS_Store", ".git", ".lfs")).help("Excluded files or folders, delimited by `;`")
    private val deleteMissing: Boolean by option().flag().help("Delete file on the remote not existing locally")
    private val md5Compare: Boolean by option().flag().help("Compare MD5 to check equality")
    private val pushTimeout: Long by option().long().default(5_000L).help("Defines the timeout after how many ms a push should fail without progress")
    private val retryCount: Int by option().int().default(3).help("Defines the amount of retries")
    private val retryDelay: Long by option().long().default(1_000L).help("Defines the delay after a upload failure occurred")
    private val failOnError: Boolean by option().flag().help("Defines if the action shall fatally fail if a single file fails to push")
    private val hideUp2date: Boolean by option().flag().help("When enabled, will hide any entries which are already up2date.")

    override suspend fun runWithAdb(devices: List<Device>) {
        try {
            val cleanedRemotePath = remotePath.removeSuffix(File.separator)
            val cleanedLocalPath = localPath.removeSuffix(File.separator)

            val localFile = File(cleanedLocalPath)
            require(localFile.exists()) { "The local file does not exist." }

            echo()
            echo("ℹ\uFE0F Copying from $cleanedLocalPath > $cleanedRemotePath")
            echo()

            val success = compare(devices, cleanedRemotePath, localFile)

            echo("                                            ")

            if (success) {
                echo("✨ Completed Successfully")
            } else {
                echo("⚠\uFE0F Completed with a warning")
            }

            exitProcess(0)
        } catch (t: Throwable) {
            echo("⁉\uFE0F Pushing the files failed with error: ${t.message}")
            exitProcess(1)
        }
    }


    private suspend fun compare(devices: List<Device>, remote: String, local: File): Boolean {
        if (excludes.contains(local.name)) {
            echo("⁉\uFE0F Exclude rule, excludes the root folder")
            return false
        }


        var allSuccessful = true
        val localFiles = local.mapped()
        devices.forEach { device ->
            echo("\uD83D\uDCF1 Execute sync for ${device.serial}")

            val result = device.compare(local.name, remote, localFiles, 1, "  ")
            allSuccessful = allSuccessful && result
        }
        return allSuccessful
    }

    private suspend fun Device.compare(name: String, remote: String, localFiles: Map<String, File>, level: Int, prefix: String = ""): Boolean {
        var allSuccessful = true
        val device = this
        val remoteFiles = adb.execute(ListFileRequest(remote, listOf(Feature.LS_V2, Feature.STAT_V2)), device.serial)
            .filter { it.name != null }
            .associateBy { it.name!! }

        echo("$prefix\uD83D\uDDC4\uFE0F $name")

        // val directChild = prefix + PARENT + DIRECT
        val indirectChild = prefix.replace(DIRECT, INDIRECT) + PARENT + DIRECT

        localFiles.onEach { (localName, localFile) ->
            val fullRemotePath = remote join localName.replace("$", "\$")
            val result = if (localFile.isDirectory) {
                device.compare(localFile.name, fullRemotePath, localFile.mapped(), level + 1, indirectChild)
            } else if (remoteFiles.containsKey(localName)) {
                val localSize = localFile.length()
                val remoteFile = remoteFiles[localName]!!
                val remoteSize = remoteFile.size.toLong()

                // is there remote, identify if it needs an update
                if (localSize != remoteSize) {
                    echo("$indirectChild\uD83D\uDCC1⬆\uFE0F $localName :: File was modified. Pushing it")
                    device.pushFile(localFile, fullRemotePath, indirectChild)
                } else {
                    if (md5Compare && !device.compareMd5(localFile, fullRemotePath)) {
                        echo("$indirectChild\uD83D\uDCC1⬆\uFE0F $localName :: File was modified. Pushing it")
                        device.pushFile(localFile, fullRemotePath, indirectChild)
                    } else {
                        if (!hideUp2date) echo("$indirectChild\uD83D\uDCC1✅ $localName :: File is up to date.")
                        true
                    }
                }
            } else {
                echo("$indirectChild\uD83D\uDCC1\uD83C\uDD95 $localName :: File was missing. Pushing update.")
                device.pushFile(localFile, fullRemotePath, indirectChild)
            }
            allSuccessful = allSuccessful && result
        }
        remoteFiles.onEach { (name, _) ->
            val fullRemotePath = remote join name
            if (!excludes.contains(name) && !localFiles.containsKey(name)) {
                // file remote, but not local, remove...
                if (deleteMissing) {
                    echo("$indirectChild\uD83D\uDCC1\uD83D\uDDD1\uFE0F $name :: exists only remote. Deleting.")
                    device.deleteFile(fullRemotePath)
                } else {
                    echo("$indirectChild\uD83D\uDCC1$name :: exists only remote")
                }
            }
        }
        return allSuccessful
    }

    private fun Device.pushFile(localFile: File, remote: String, prepandLog: String): Boolean = runBlocking {
        if (config.dryRun) return@runBlocking true
        val device = this@pushFile
        var attempt = 0
        try {
            retry(retryCount, retryDelay, preRetry = {
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
                        if (config.progress) terminal.rawPrint("\r${prepandLog}Pushing: $percentage%")
                        reset()
                    }
                    if (config.progress) terminal.rawPrint("\r")
                }
                if (result == null) throw Exception("Failed while pushing in attempt $attempt")
            }
            return@runBlocking true
        } catch (t: Throwable) {
            terminal.rawPrint("${if (config.progress) "\r" else ""}${prepandLog}${INDIRECT}❌ Timed out $attempt times while pushing $remote (Reason: ${t.message}\n")
            if (failOnError) {
                throw t
            } else {
                return@runBlocking false
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

    private infix fun String.join(second: String) = this + File.separatorChar + second

    companion object {
        private const val DIRECT = "--"
        private const val INDIRECT = "  "
        private const val PARENT = '|'
    }
}