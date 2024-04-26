package com.mikepenz.adbfriend

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*

class Config(val dryRun: Boolean, val progress: Boolean, val serials: List<String>?)

class AdbFriend : CliktCommand() {
    private val dryRun: Boolean by option().flag().help("Only compares the folders, does not push or remove files.")
    private val progress: Boolean by option().flag("--no-flag", default = true).help("Shows progress in the terminal. (Disable to systems which don't handle `\\r`)")
    private val serials: List<String>? by option().split(";").help("The serial(s) of devices to sync to. Delimited by `;`.")

    init {
        versionOption("0.0.3")
    }

    override fun run() {
        currentContext.obj = Config(dryRun, progress, serials)
    }
}

fun main(args: Array<String>) = AdbFriend().subcommands(Sync(), Test()).main(args)