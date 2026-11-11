package com.mounsokdara.zyphvert.ui.tool

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mounsokdara.zyphvert.data.SettingsRepository
import com.mounsokdara.zyphvert.jobs.ConversionManager
import com.mounsokdara.zyphvert.tools.Opt
import com.mounsokdara.zyphvert.tools.PickedFile
import com.mounsokdara.zyphvert.tools.ToolRequest
import com.mounsokdara.zyphvert.tools.ToolSpec
import com.mounsokdara.zyphvert.tools.MetaInfo
import com.mounsokdara.zyphvert.tools.cleanMetaKey
import com.mounsokdara.zyphvert.tools.loadImageBitmap
import com.mounsokdara.zyphvert.tools.loadMetaInfo
import com.mounsokdara.zyphvert.tools.resolveLive
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

class MetaTag(val key: String, value: String, val orig: String?) {
    var value by mutableStateOf(value)
}

@HiltViewModel
class ToolViewModel @Inject constructor(
    val manager: ConversionManager,
    private val repo: SettingsRepository,
) : ViewModel() {
    val picked = mutableStateMapOf<String, List<PickedFile>>()
    val values = mutableStateMapOf<String, String>()
    var remember by mutableStateOf(false)
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    private var initFor: String? = null

    fun init(spec: ToolSpec, seedPath: String?) {
        if (initFor != spec.id) {
            initFor = spec.id
            spec.options.forEach { o ->
                values[o.id] = o.default
                if (o is Opt.Range) values["${o.id}.mode"] = "keep"
                if (o is Opt.Size) values["${o.id}.unit"] = "MB"
            }
            viewModelScope.launch {
                val saved = repo.toolSettings(spec.id)
                if (saved.isNotEmpty()) {
                    remember = true
                    saved.forEach { (k, v) -> if (k in values) values[k] = v }
                }
            }
        }
        if (seedPath != null) {
            val f = File(seedPath)
            val first = spec.inputs.firstOrNull() ?: return
            picked[first.id] = listOf(PickedFile(Uri.fromFile(f), f.name))
        }
    }

    fun applyPreset(vals: Map<String, String>) { vals.forEach { (k, v) -> values[k] = v } }

    fun setFiles(slot: String, files: List<PickedFile>) { picked[slot] = files; error = null }

    val metaTags = mutableStateListOf<MetaTag>()
    var meta by mutableStateOf<MetaInfo?>(null)
        private set
    var metaLoading by mutableStateOf(false)
        private set
    var coverPreview by mutableStateOf<Bitmap?>(null)
        private set
    private var metaFor: Uri? = null

    fun loadMeta(ctx: Context, spec: ToolSpec) {
        if (spec.id != "meta") return
        val f = picked[spec.inputs.first().id]?.firstOrNull()
        if (f?.uri == metaFor) return
        metaFor = f?.uri
        metaTags.clear()
        meta = null
        resetCover()
        values["meta.useFilename"] = "false"
        if (f == null) { metaLoading = false; return }
        metaLoading = true
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { runCatching { loadMetaInfo(ctx.applicationContext, manager.engine, f) }.getOrNull() }
            if (metaFor == f.uri) {
                meta = info
                info?.tags?.forEach { metaTags += MetaTag(it.key, it.value, it.value) }
                metaLoading = false
            }
        }
    }

    fun addTag(key: String) {
        val k = cleanMetaKey(key)
        if (k.isNotEmpty() && metaTags.none { it.key.equals(k, ignoreCase = true) }) metaTags += MetaTag(k, "", null)
    }

    fun removeTag(tag: MetaTag) { metaTags.remove(tag) }

    fun setCover(ctx: Context, f: PickedFile) {
        picked["cover"] = listOf(f)
        values["meta.coverRemove"] = "false"
        coverPreview = null
        viewModelScope.launch { coverPreview = withContext(Dispatchers.IO) { loadImageBitmap(ctx.applicationContext, f.uri, 640) } }
    }

    fun removeCover() {
        picked.remove("cover")
        coverPreview = null
        values["meta.coverRemove"] = "true"
    }

    fun resetCover() {
        picked.remove("cover")
        coverPreview = null
        values["meta.coverRemove"] = "false"
    }

    private fun metaOps(): String {
        val arr = JSONArray()
        val rows = metaTags.toList()
        for (t in rows) {
            val v = t.value.trim()
            val o = t.orig
            val changed = if (o == null) v.isNotEmpty() else v != o.trim()
            if (!changed) continue
            arr.put(JSONObject().put("k", t.key).also { if (v.isNotEmpty()) it.put("v", v) })
        }
        meta?.tags?.forEach { had -> if (rows.none { it.key == had.key }) arr.put(JSONObject().put("k", had.key)) }
        return arr.toString()
    }

    fun submit(ctx: Context, spec: ToolSpec, title: String, onQueued: () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                if (spec.id == "meta") values["meta.ops"] = metaOps()
                val app = ctx.applicationContext
                val jobs = withContext(Dispatchers.IO) {
                    val files = picked.toMap().mapValues { (slot, list) ->
                        if (spec.inputs.firstOrNull { it.id == slot }?.kind?.acceptsLive == true) list.map { resolveLive(app, it) } else list
                    }
                    spec.build(ToolRequest(app, manager.engine, title, files, values.toMap()))
                }
                if (jobs.isEmpty()) throw IllegalArgumentException("Nothing to do.")
                val keep = values.filterKeys { !it.startsWith("tag.") && !it.startsWith("meta.") && it != "name" && it != "range" && !it.startsWith("range.") }
                repo.saveToolSettings(spec.id, if (remember) keep else null)
                val ids = manager.submit(title, jobs)
                com.mounsokdara.zyphvert.data.BatchTracker.start(ids)
                onQueued()
            } catch (e: Throwable) {
                error = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }
}
