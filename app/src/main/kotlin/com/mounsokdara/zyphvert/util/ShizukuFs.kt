package com.mounsokdara.zyphvert.util

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.mounsokdara.zyphvert.shizuku.FsService
import com.mounsokdara.zyphvert.shizuku.IFsService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import rikka.shizuku.Shizuku
import java.io.File

object ShizukuFs {
    private var service: IFsService? = null
    private val lock = Mutex()

    fun running(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun granted(): Boolean =
        running() && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    fun requestPermission(code: Int) {
        runCatching { Shizuku.requestPermission(code) }
    }

    fun listenForPermission(onResult: (Boolean) -> Unit): () -> Unit {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            onResult(grantResult == PackageManager.PERMISSION_GRANTED)
        }
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
        return { runCatching { Shizuku.removeRequestPermissionResultListener(listener) } }
    }

    private suspend fun connect(ctx: Context): IFsService? = lock.withLock {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        if (!granted()) return null
        val ready = CompletableDeferred<IFsService?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val s = binder?.takeIf { it.pingBinder() }?.let { IFsService.Stub.asInterface(it) }
                service = s
                ready.complete(s)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        val args = Shizuku.UserServiceArgs(ComponentName(ctx.packageName, FsService::class.java.name))
            .daemon(false).processNameSuffix("fs").debuggable(false).version(1).tag("zyphvert-fs")
        if (runCatching { Shizuku.bindUserService(args, connection) }.isFailure) return null
        withTimeoutOrNull(8_000) { ready.await() }
    }

    suspend fun list(ctx: Context, path: String): List<ProtectedEntry>? = withContext(Dispatchers.IO) {
        val s = connect(ctx) ?: return@withContext null
        runCatching {
            val text = s.list(path)
            if (text == "null") return@runCatching null
            val arr = JSONArray(text)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ProtectedEntry(o.getString("n"), o.getBoolean("d"), o.getLong("s"), o.getLong("m"))
            }
        }.getOrNull()
    }

    suspend fun copyToCache(ctx: Context, path: String): File? = withContext(Dispatchers.IO) {
        val s = connect(ctx) ?: return@withContext null
        runCatching {
            val source = File(path)
            val dir = File(ctx.cacheDir, "protected/${path.hashCode().toUInt()}").apply { mkdirs() }
            val target = File(dir, source.name)
            s.open(path).use { pfd ->
                java.io.FileInputStream(pfd.fileDescriptor).use { input -> target.outputStream().use { input.copyTo(it) } }
            }
            target
        }.getOrNull()
    }
}
