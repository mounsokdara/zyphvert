package com.mounsokdara.zyphvert.tools

import android.content.Context
import android.os.Environment
import java.io.File

/** Where converted files go: Zyphvert/<category> on shared storage, or the app's own folder if that isn't writable. */
object OutputPaths {
    private fun sharedRoot(): File = File(Environment.getExternalStorageDirectory(), "Zyphvert")

    fun root(ctx: Context): File {
        val shared = sharedRoot()
        val ok = runCatching { shared.mkdirs(); shared.isDirectory && shared.canWrite() }.getOrDefault(false)
        return if (ok) shared else File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Zyphvert")
    }

    fun dir(ctx: Context, category: String): File = File(root(ctx), category).apply { mkdirs() }

    fun ringtones(ctx: Context): File {
        val d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
        val ok = runCatching { d.mkdirs(); d.isDirectory && d.canWrite() }.getOrDefault(false)
        return if (ok) d else dir(ctx, "Ringtones")
    }

    /** `name.ext`, or `name_2.ext`, `name_3.ext`... if it exists. */
    fun unique(dir: File, base: String, ext: String): File {
        dir.mkdirs()
        var f = File(dir, "$base.$ext")
        var n = 2
        while (f.exists()) f = File(dir, "${base}_${n++}.$ext")
        return f
    }

    fun uniqueDir(parent: File, base: String): File {
        var f = File(parent, base)
        var n = 2
        while (f.exists()) f = File(parent, "${base}_${n++}")
        return f.apply { mkdirs() }
    }

    fun display(path: String): String {
        val root = Environment.getExternalStorageDirectory().path + "/"
        return path.removePrefix(root)
    }
}
