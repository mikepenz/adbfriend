package com.mikepenz.adbfriend.subcommands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.mikepenz.adbfriend.subcommands.mcp.Server

class Mcp : CliktCommand() {
    init {
        subcommands(Server())
    }

    override fun help(context: Context) = """
        Provides MCP (Model Context Protocol) related commands.
    """.trimIndent()

    override fun run() {}
}