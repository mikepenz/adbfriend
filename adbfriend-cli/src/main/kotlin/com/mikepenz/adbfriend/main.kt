package com.mikepenz.adbfriend

import adbfriend_root.adbfriend_cli.BuildConfig
import adbfriend_root.adbfriend_cli.generated.resources.Res
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.adbfriend.subcommands.Packages
import com.mikepenz.adbfriend.subcommands.Sync
import com.mikepenz.adbfriend.subcommands.Test
import com.mikepenz.adbfriend.subcommands.Uninstall
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.util.logging.Level
import java.util.logging.Logger

class Config(val dryRun: Boolean, val progress: Boolean, val serials: List<String>?)

@OptIn(ExperimentalResourceApi::class)
class AdbFriend : CliktCommand(name = "adbfriend") {
    private val dryRun: Boolean by option().flag().help("Only compares the folders, does not push or remove files.")
    private val progress: Boolean by option().flag("--no-progress", default = true).help("Shows progress in the terminal. (Disable to systems which don't handle `\\r`)")
    private val serials: List<String>? by option().split(";").help("The serial(s) of devices to sync to. Delimited by `;`.")

    init {
        Logger.getLogger("io.netty").setLevel(Level.OFF)
        versionOption(BuildConfig.APP_VERSION)

        eagerOption("--about", help = "Outputs the used libraries to the terminal.") {
            val libraries = runBlocking {
                Libs.Builder().withJson(Res.readBytes("files/aboutlibraries.json").decodeToString()).build()
            }

            echo()
            echo("\uD83D\uDCDA Used libraries:")
            echo()
            libraries.libraries.onEach {
                echo("  ${it.name} [${it.artifactVersion}] - ${it.licenses.joinToString { lic -> lic.name }}")
            }

            throw PrintMessage("")
        }
    }

    override fun run() {
        currentContext.obj = Config(dryRun, progress, serials)
    }
}

fun main(args: Array<String>) = AdbFriend().subcommands(Sync(), Test(), Uninstall(), Packages()).main(args)