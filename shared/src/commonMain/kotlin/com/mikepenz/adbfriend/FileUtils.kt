package com.mikepenz.adbfriend

import java.io.File
import java.math.BigInteger
import java.security.MessageDigest

fun File.md5(): String {
    return BigInteger(1, MessageDigest.getInstance("MD5").digest(readBytes())).toString(16).padStart(32, '0')
}