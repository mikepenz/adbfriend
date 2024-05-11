package com.mikepenz.adbfriend.subcommands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class Tools : CliktCommand() {
    init {
        subcommands(AdbSpeed())
    }

    override fun run() {}
}