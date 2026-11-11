package com.mounsokdara.zyphvert.shizuku

import android.os.ParcelFileDescriptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.system.exitProcess

class FsService : IFsService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun list(path: String): String {
        val files = File(path).listFiles() ?: return "null"
        val out = JSONArray()
        for (f in files) {
            out.put(
                JSONObject().put("n", f.name).put("d", f.isDirectory)
                    .put("s", if (f.isFile) f.length() else 0L).put("m", f.lastModified()),
            )
        }
        return out.toString()
    }

    override fun open(path: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
}
