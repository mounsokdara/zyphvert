package com.mounsokdara.zyphvert.tools

import android.content.Context
import com.drew.imaging.ImageMetadataReader
import java.text.SimpleDateFormat
import java.util.Locale

internal fun extractorSections(ctx: Context, f: PickedFile): List<MetaSection> = runCatching {
    val md = ctx.contentResolver.openInputStream(f.uri)?.use { ImageMetadataReader.readMetadata(it.buffered(64 * 1024)) }
        ?: return@runCatching emptyList<MetaSection>()
    md.directories.mapNotNull { d ->
        val rows = d.tags.mapNotNull { t ->
            val v = t.description?.trim().orEmpty()
            val name = t.tagName.orEmpty()
            if (v.isEmpty() || name.startsWith("Unknown tag")) null
            else if (d.name == "QuickTime" && (name == "Creation Time" || name == "Modification Time")) timeRow(name, v)
            else MetaRow(name, v.take(400))
        }.take(150)
        if (rows.isEmpty()) null else MetaSection(d.name.orEmpty().ifEmpty { "Metadata" }, rows)
    }
}.getOrDefault(emptyList())

private fun timeRow(name: String, shown: String): MetaRow {
    val iso = runCatching { isoUtc(SimpleDateFormat("EEE MMM dd HH:mm:ss XXX yyyy", Locale.US).parse(shown)!!.time) }.getOrNull()
    return MetaRow(name, shown, editTag = iso?.let { "creation_time" }, editValue = iso)
}
