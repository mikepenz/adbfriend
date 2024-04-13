package com.mikepenz.adbfriend

fun String.escapeForSync() = replace("$", "\$")
fun String.escapeForMD5() = replace("\$", "\\$")