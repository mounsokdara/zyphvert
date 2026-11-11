package com.mounsokdara.zyphvert.util

import java.io.File

object FsFallback {
    fun children(dir: File): List<File>? {
        dir.listFiles()?.let { return it.toList() }
        val names = when (dir.path) {
            "/" -> listOf("storage", "sdcard", "mnt", "system", "vendor", "product", "data", "cache", "etc")
            "/storage" -> listOf("emulated", "self")
            "/storage/emulated" -> listOf("0")
            else -> return null
        }
        return names.map { File(dir, it) }.filter { it.exists() }
    }
}
