package com.mikepenz.adbfriend.extensions

fun String.escapeForSync() = replace("$", "\$")
fun String.escapeForMD5() = replace("\$", "\\$")