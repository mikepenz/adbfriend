package com.mikepenz.adbfriend.subcommands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands

class Tools : CliktCommand() {
    init {
        subcommands(AdbSpeed())
    }

    override fun help(context: Context) = """
        Groups various sub-tools together. This is no-op.
    """.trimIndent()

    override fun run() {}
}