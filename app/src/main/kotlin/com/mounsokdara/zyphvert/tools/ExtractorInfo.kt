package com.mounsokdara.zyphvert.tools

import android.content.Context
import com.drew.imaging.ImageMetadataReader

/**
 * Reads photo, RAW, HEIF, WebP, MP4/MOV, AVI, WAV and other tags with the open source metadata-extractor
 * library (EXIF, XMP, IPTC, ICC profiles, maker notes). Returns nothing when the format isn't recognised.
 */
internal fun extractorSections(ctx: Context, f: PickedFile): List<MetaSection> = runCatching {
    val md = ctx.contentResolver.openInputStream(f.uri)?.use { ImageMetadataReader.readMetadata(it.buffered(64 * 1024)) }
        ?: return@runCatching emptyList<MetaSection>()
    md.directories.mapNotNull { d ->
        val rows = d.tags.mapNotNull { t ->
            val v = t.description?.trim().orEmpty()
            val name = t.tagName.orEmpty()
            if (v.isEmpty() || name.startsWith("Unknown tag")) null else MetaRow(name, v.take(400))
        }.take(150)
        if (rows.isEmpty()) null else MetaSection(d.name.orEmpty().ifEmpty { "Metadata" }, rows)
    }
}.getOrDefault(emptyList())
