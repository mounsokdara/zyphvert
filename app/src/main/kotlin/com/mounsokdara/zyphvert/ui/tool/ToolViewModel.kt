package com.mounsokdara.zyphvert.ui.tool

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
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
import com.mounsokdara.zyphvert.tools.readTags
import com.mounsokdara.zyphvert.tools.resolveLive
import com.mounsokdara.zyphvert.tools.tagKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Form state for one tool: picked files and option values survive rotation. */
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

    private var tagsFor: Uri? = null

    /** Metadata Editor: fill the tag fields with what the picked file already has. */
    fun loadTags(ctx: Context, spec: ToolSpec) {
        if (spec.id != "meta") return
        val f = picked[spec.inputs.first().id]?.firstOrNull() ?: return
        if (f.uri == tagsFor) return
        tagsFor = f.uri
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { readTags(ctx.applicationContext, f.uri) }
            tagKeys.forEach { (k, _) ->
                values["tag.$k"] = found[k].orEmpty()
                values["orig.tag.$k"] = found[k].orEmpty()
            }
        }
    }

    /** Builds the jobs and hands them to the background queue. */
    fun submit(ctx: Context, spec: ToolSpec, title: String, onQueued: () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val app = ctx.applicationContext
                val jobs = withContext(Dispatchers.IO) {
                    // Video-ish slots accept Live/Motion Photos: swap them for their video first.
                    val files = picked.toMap().mapValues { (slot, list) ->
                        if (spec.inputs.firstOrNull { it.id == slot }?.kind?.acceptsLive == true) list.map { resolveLive(app, it) } else list
                    }
                    spec.build(ToolRequest(app, manager.engine, title, files, values.toMap()))
                }
                if (jobs.isEmpty()) throw IllegalArgumentException("Nothing to do.")
                // Don't remember per-file or free-text fields.
                val keep = values.filterKeys { !it.startsWith("tag.") && !it.startsWith("orig.") && it != "name" && it != "range" && !it.startsWith("range.") }
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
