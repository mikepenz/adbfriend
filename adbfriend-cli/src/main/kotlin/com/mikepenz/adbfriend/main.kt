package com.mikepenz.adbfriend

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import com.mikepenz.adbfriend.subcommands.Packages
import com.mikepenz.adbfriend.subcommands.Sync
import com.mikepenz.adbfriend.subcommands.Test
import com.mikepenz.adbfriend.subcommands.Uninstall
import java.util.logging.Level
import java.util.logging.Logger

class Config(val dryRun: Boolean, val progress: Boolean, val serials: List<String>?)

class AdbFriend : CliktCommand() {
    private val dryRun: Boolean by option().flag().help("Only compares the folders, does not push or remove files.")
    private val progress: Boolean by option().flag("--no-progress", default = true).help("Shows progress in the terminal. (Disable to systems which don't handle `\\r`)")
    private val serials: List<String>? by option().split(";").help("The serial(s) of devices to sync to. Delimited by `;`.")

    init {
        Logger.getLogger("io.netty").setLevel(Level.OFF)
        versionOption("0.1.1")
    }

    override fun run() {
        currentContext.obj = Config(dryRun, progress, serials)
    }
}

fun main(args: Array<String>) = AdbFriend().subcommands(Sync(), Test(), Uninstall(), Packages()).main(args)