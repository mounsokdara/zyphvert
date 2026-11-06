package com.mounsokdara.zyphvert.util

import java.io.File

/**
 * Children of [dir]. Android hides some system folders from apps (listFiles returns null), so for the
 * top of the tree this falls back to the well-known entries, which lets you walk down from "/" like
 * in a root file manager.
 */
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
