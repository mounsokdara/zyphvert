package com.mounsokdara.zyphvert.tools

import android.content.Context
import android.net.Uri
import com.mounsokdara.zyphvert.engine.MediaEngine
import java.io.File

enum class InKind(val mimes: Array<String>, val exts: Set<String>?) {
    Video(arrayOf("video/*"), VIDEO_EXTS),
    Media(arrayOf("video/*", "audio/*"), VIDEO_EXTS + AUDIO_EXTS),
    Image(arrayOf("image/*"), IMAGE_EXTS),
    Visual(arrayOf("video/*", "image/*"), VIDEO_EXTS + IMAGE_EXTS),
    Any(arrayOf("*/*"), null),
    ;

    val acceptsLive get() = this == Video || this == Media
}

data class InputSlot(
    val id: String,
    val label: String,
    val kind: InKind,
    val multiple: Boolean = false,
    val optional: Boolean = false,
)

data class Preset(val key: String, val title: String, val desc: String, val values: Map<String, String>)

data class Item(val key: String, val label: String, val desc: String? = null)

sealed class Opt(
    val id: String,
    val label: String,
    val showIf: ((Map<String, String>) -> Boolean)? = null,
    val advanced: Boolean = false,
) {
    abstract val default: String

    class Choice(
        id: String, label: String, val options: List<Item>,
        showIf: ((Map<String, String>) -> Boolean)? = null, advanced: Boolean = false,
    ) : Opt(id, label, showIf, advanced) {
        override val default = options.first().key
    }

    class Slider(
        id: String, label: String, val min: Int, val max: Int, val initial: Int, val unit: String = "",
        showIf: ((Map<String, String>) -> Boolean)? = null, advanced: Boolean = false,
    ) : Opt(id, label, showIf, advanced) { override val default = initial.toString() }

    class Text(
        id: String, label: String, val hint: String = "", val initial: String = "",
        showIf: ((Map<String, String>) -> Boolean)? = null, advanced: Boolean = false,
    ) : Opt(id, label, showIf, advanced) { override val default = initial }

    class Toggle(
        id: String, label: String, val initial: Boolean, val desc: String? = null,
        showIf: ((Map<String, String>) -> Boolean)? = null, advanced: Boolean = false,
    ) : Opt(id, label, showIf, advanced) { override val default = initial.toString() }

    class Presets(id: String, label: String, val presets: List<Preset>) : Opt(id, label) { override val default = "" }

    class Name(id: String = "name", label: String = "Save as") : Opt(id, label) { override val default = "" }

    class Range(id: String, label: String, val slot: String = "media", val modes: Boolean = false) : Opt(id, label) {
        override val default = ""
    }

    class Tags(id: String = "tags", label: String = "Edit tags", showIf: ((Map<String, String>) -> Boolean)? = null) : Opt(id, label, showIf) { override val default = "" }

    class Metadata(id: String = "metadata", label: String = "Metadata") : Opt(id, label) { override val default = "" }

    class Section(label: String, advanced: Boolean = false) : Opt("section:$label", label, null, advanced) { override val default = "" }
}

data class PickedFile(
    val uri: Uri,
    val name: String,
    val companion: String? = null,
    val live: Boolean = false,
) {
    val path: String? get() = if (uri.scheme == "file") uri.path else null
}

class ToolJob(
    val title: String,
    val input: String,
    val output: String?,
    val command: String? = null,
    val probe: Boolean = false,
    val native: (suspend () -> Unit)? = null,
    val after: (suspend () -> Unit)? = null,
)

class ToolRequest(
    val ctx: Context,
    val engine: MediaEngine,
    val toolTitle: String,
    val files: Map<String, List<PickedFile>>,
    val values: Map<String, String>,
) {
    fun str(id: String) = values[id].orEmpty()
    fun int(id: String) = values[id]?.toIntOrNull() ?: 0
    fun bool(id: String) = values[id] == "true"
    fun files(id: String): List<PickedFile> = files[id].orEmpty()
    fun file(id: String): PickedFile? = files[id]?.firstOrNull()

    fun saf(f: PickedFile): String =
        f.path?.let { q(it) } ?: engine.safRead(ctx, f.uri)
            ?: throw IllegalStateException("The FFmpeg engine isn't part of this build.")

    fun out(category: String, f: PickedFile, ext: String, tag: String = "", dir: File? = null): File {
        val custom = str("name").trim().replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.').take(80)
        val useCustom = custom.isNotEmpty() && files("media").size <= 1
        val base = if (useCustom) custom else baseName(f.name) + tag + "_zv"
        return OutputPaths.unique(dir ?: OutputPaths.dir(ctx, category), base, ext)
    }

    fun job(f: PickedFile, out: File, command: String) =
        ToolJob(title = toolTitle, input = f.name, output = out.path, command = command)
}

class ToolSpec(
    val id: String,
    val inputs: List<InputSlot>,
    val options: List<Opt>,
    val actionLabel: String = "Convert",
    val intro: String,
    val hint: String? = null,
    val build: suspend (ToolRequest) -> List<ToolJob>,
)
