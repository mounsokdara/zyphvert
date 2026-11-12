package com.mounsokdara.zyphvert.tools

import android.content.Context
import android.content.pm.PackageManager
import android.util.Xml
import androidx.core.content.pm.PackageInfoCompat
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private const val HASH_LIMIT = 256L * 1024 * 1024
private const val COPY_LIMIT = 256L * 1024 * 1024
private const val HEAD = 64 * 1024

internal class FileSource(val path: String, val temp: Boolean, private val closer: () -> Unit) : AutoCloseable {
    override fun close() = closer()
}

internal fun fileSize(ctx: Context, f: PickedFile): Long {
    f.path?.let { p -> File(p).takeIf { it.isFile }?.let { return it.length() } }
    return runCatching { ctx.contentResolver.openAssetFileDescriptor(f.uri, "r")?.use { it.length } }.getOrNull() ?: -1L
}

internal fun openSource(ctx: Context, f: PickedFile): FileSource? {
    f.path?.let { p -> if (File(p).isFile) return FileSource(p, false) {} }
    val pfd = runCatching { ctx.contentResolver.openFileDescriptor(f.uri, "r") }.getOrNull()
    if (pfd != null) {
        val p = "/proc/self/fd/${pfd.fd}"
        val ok = runCatching { RandomAccessFile(p, "r").use { it.length() >= 0 } }.getOrDefault(false)
        if (ok) return FileSource(p, false) { runCatching { pfd.close() } }
        runCatching { pfd.close() }
    }
    val size = fileSize(ctx, f)
    if (size in 0..COPY_LIMIT) {
        val tmp = File(ctx.cacheDir, "facts_${System.nanoTime()}_${baseName(f.name)}.${nameExt(f.name).ifEmpty { "bin" }}")
        val copied = runCatching {
            ctx.contentResolver.openInputStream(f.uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } } != null
        }.getOrDefault(false)
        if (copied) return FileSource(tmp.path, true) { tmp.delete() }
        tmp.delete()
    }
    return null
}

private class Sig(val label: String, val mime: String, val media: Boolean = false)

private fun sniff(h: ByteArray, n: Int, ext: String): Sig? {
    fun at(off: Int, vararg b: Int): Boolean {
        if (off + b.size > n) return false
        for (i in b.indices) if ((h[off + i].toInt() and 0xFF) != b[i]) return false
        return true
    }
    fun txt(off: Int, s: String) = at(off, *IntArray(s.length) { s[it].code })
    return when {
        at(0, 0x89, 0x50, 0x4E, 0x47) -> Sig("PNG image", "image/png")
        at(0, 0xFF, 0xD8, 0xFF) -> Sig("JPEG image", "image/jpeg")
        txt(0, "GIF8") -> Sig("GIF image", "image/gif")
        txt(0, "RIFF") && txt(8, "WEBP") -> Sig("WebP image", "image/webp")
        txt(0, "RIFF") && txt(8, "WAVE") -> Sig("WAV audio", "audio/wav", true)
        txt(0, "RIFF") && txt(8, "AVI ") -> Sig("AVI video", "video/x-msvideo", true)
        txt(0, "BM") && n > 14 -> Sig("BMP image", "image/bmp")
        at(0, 0x49, 0x49, 0x2A, 0x00) || at(0, 0x4D, 0x4D, 0x00, 0x2A) -> Sig("TIFF image", "image/tiff")
        txt(4, "ftyp") -> {
            val brand = if (n >= 12) String(h, 8, 4, Charsets.ISO_8859_1) else ""
            when {
                brand.startsWith("qt") -> Sig("QuickTime video (MOV)", "video/quicktime", true)
                brand.startsWith("M4A") -> Sig("M4A audio", "audio/mp4", true)
                brand in setOf("heic", "heix", "mif1", "msf1") -> Sig("HEIF image", "image/heic")
                brand == "avif" -> Sig("AVIF image", "image/avif")
                brand.startsWith("3gp") -> Sig("3GP video", "video/3gpp", true)
                else -> Sig("MP4 video", "video/mp4", true)
            }
        }
        at(0, 0x1A, 0x45, 0xDF, 0xA3) -> Sig("Matroska / WebM video", "video/x-matroska", true)
        txt(0, "OggS") -> Sig("Ogg media", "audio/ogg", true)
        txt(0, "fLaC") -> Sig("FLAC audio", "audio/flac", true)
        txt(0, "ID3") || at(0, 0xFF, 0xFB) || at(0, 0xFF, 0xF3) -> Sig("MP3 audio", "audio/mpeg", true)
        txt(0, "FLV") -> Sig("Flash video", "video/x-flv", true)
        at(0, 0x30, 0x26, 0xB2, 0x75) -> Sig("Windows Media (ASF)", "video/x-ms-asf", true)
        txt(0, "PK") && (at(2, 3, 4) || at(2, 5, 6)) -> Sig("ZIP archive", "application/zip")
        at(0, 0x1F, 0x8B) -> Sig("GZIP compressed file", "application/gzip")
        txt(0, "BZh") -> Sig("BZIP2 compressed file", "application/x-bzip2")
        at(0, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> Sig("XZ compressed file", "application/x-xz")
        at(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> Sig("7-Zip archive", "application/x-7z-compressed")
        txt(0, "Rar!") -> Sig("RAR archive", "application/vnd.rar")
        at(0, 0x28, 0xB5, 0x2F, 0xFD) -> Sig("Zstandard compressed file", "application/zstd")
        txt(0, "MSCF") -> Sig("Windows Cabinet (CAB)", "application/vnd.ms-cab-compressed")
        txt(257, "ustar") -> Sig("TAR archive", "application/x-tar")
        txt(0, "%PDF-") -> Sig("PDF document", "application/pdf")
        txt(0, "{\\rtf") -> Sig("Rich Text document", "application/rtf")
        at(0, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1) -> Sig("Microsoft Office (legacy) / OLE2 file", "application/x-ole-storage")
        at(0, 0x7F, 0x45, 0x4C, 0x46) -> Sig("ELF executable or library", "application/x-elf")
        txt(0, "MZ") -> Sig("Windows executable (PE)", "application/vnd.microsoft.portable-executable")
        at(0, 0xCF, 0xFA, 0xED, 0xFE) || at(0, 0xCE, 0xFA, 0xED, 0xFE) || at(0, 0xFE, 0xED, 0xFA, 0xCE) || at(0, 0xFE, 0xED, 0xFA, 0xCF) ->
            Sig("Mach-O executable", "application/x-mach-binary")
        at(0, 0xCA, 0xFE, 0xBA, 0xBE) -> Sig(if (ext == "class") "Java class file" else "Mach-O universal binary", "application/java-vm")
        txt(0, "dex\n") -> Sig("Android DEX code", "application/octet-stream")
        at(0, 0x00, 0x61, 0x73, 0x6D) -> Sig("WebAssembly module", "application/wasm")
        txt(0, "SQLite format 3") -> Sig("SQLite database", "application/vnd.sqlite3")
        at(0, 0x4C, 0x00, 0x00, 0x00, 0x01, 0x14, 0x02) -> Sig("Windows shortcut (LNK)", "application/x-ms-shortcut")
        at(0, 0x00, 0x01, 0x00, 0x00) && ext in setOf("ttf", "ttc") -> Sig("TrueType font", "font/ttf")
        txt(0, "OTTO") -> Sig("OpenType font", "font/otf")
        txt(0, "wOFF") -> Sig("WOFF font", "font/woff")
        txt(0, "wOF2") -> Sig("WOFF2 font", "font/woff2")
        txt(0, "#!") -> Sig("Script (shebang)", "text/x-script")
        else -> null
    }
}

internal fun looksLikeMedia(ctx: Context, f: PickedFile): Boolean {
    val src = openSource(ctx, f) ?: return false
    return src.use {
        val head = readHead(it.path, 512)
        sniff(head.first, head.second, nameExt(f.name))?.media == true
    }
}

private fun readHead(path: String, max: Int): Pair<ByteArray, Int> = runCatching {
    RandomAccessFile(path, "r").use { raf ->
        val buf = ByteArray(max)
        var got = 0
        while (got < max) { val r = raf.read(buf, got, max - got); if (r <= 0) break; got += r }
        buf to got
    }
}.getOrDefault(ByteArray(0) to 0)

internal fun fmtSize(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format(Locale.US, "%.2f MB", b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> String.format(Locale.US, "%.1f KB", b / 1024.0)
    else -> "$b bytes"
}

internal fun fmtDate(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date(ms))

internal fun hex(b: ByteArray, off: Int = 0, len: Int = b.size - off): String {
    val sb = StringBuilder()
    for (i in off until off + len) sb.append(String.format(Locale.US, "%02x", b[i].toInt() and 0xFF))
    return sb.toString()
}

private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
private fun le32(b: ByteArray, o: Int): Long = (le16(b, o).toLong()) or (le16(b, o + 2).toLong() shl 16)
private fun be32(b: ByteArray, o: Int): Long =
    ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

private fun hashRows(path: String, size: Long): List<MetaRow> {
    if (size > HASH_LIMIT) return listOf(MetaRow("Checksums", "Skipped (file is over ${fmtSize(HASH_LIMIT)})"))
    return runCatching {
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        FileInputStream(path).use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md5.update(buf, 0, n); sha1.update(buf, 0, n); sha256.update(buf, 0, n)
            }
        }
        listOf(
            MetaRow("MD5", hex(md5.digest())),
            MetaRow("SHA-1", hex(sha1.digest())),
            MetaRow("SHA-256", hex(sha256.digest())),
        )
    }.getOrDefault(emptyList())
}

private fun lastModified(ctx: Context, f: PickedFile): Long? {
    f.path?.let { p -> File(p).takeIf { it.isFile }?.let { return it.lastModified().takeIf { t -> t > 0 } } }
    return runCatching {
        ctx.contentResolver.query(f.uri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0).takeIf { t -> t > 0 } else null
        }
    }.getOrNull()
}

/** Everything we can say about any file, whatever its format. */
internal fun loadFileFacts(ctx: Context, f: PickedFile, summaryOnly: Boolean = false, editable: Boolean = false): List<MetaSection> {
    val sections = mutableListOf<MetaSection>()
    val size = fileSize(ctx, f)
    val ext = nameExt(f.name)
    val src = openSource(ctx, f)
    try {
        val head = if (src != null) readHead(src.path, HEAD) else (ByteArray(0) to 0)
        val sig = sniff(head.first, head.second, ext)
        val rows = mutableListOf<MetaRow>()
        val editable = f.path?.let { File(it).run { isFile && parentFile?.canWrite() == true } } == true
        rows += if (editable) MetaRow("Name", f.name, editTag = "file.name", editValue = f.name) else MetaRow("Name", f.name)
        if (size >= 0) rows += MetaRow("Size", if (size < 1024) "$size bytes" else "${fmtSize(size)} (${String.format(Locale.US, "%,d", size)} bytes)")
        val mimeByExt = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        sig?.let { rows += MetaRow("Detected type", it.label); rows += MetaRow("MIME type", it.mime) }
            ?: mimeByExt?.let { rows += MetaRow("MIME type", "$it (from the file name)") }
        if (ext.isNotEmpty()) rows += MetaRow("Extension", ".$ext")
        if (sig != null && mimeByExt != null && !sameFamily(sig.mime, mimeByExt) && ext !in zipLikeExts) {
            rows += MetaRow("Warning", "The name says $mimeByExt but the content looks like ${sig.label}")
        }
        lastModified(ctx, f)?.let {
            rows += if (editable) MetaRow("Last modified", fmtDate(it), editTag = "file.lastModified", editValue = isoUtc(it)) else MetaRow("Last modified", fmtDate(it))
        }
        f.path?.let { p ->
            rows += MetaRow("Location", p)
            val file = File(p)
            rows += MetaRow("Permissions", (if (file.canRead()) "read " else "") + (if (file.canWrite()) "write " else "") + (if (file.canExecute()) "execute" else ""))
            if (file.name.startsWith(".")) rows += MetaRow("Hidden", "Yes")
        }
        if (src != null) rows += hashRows(src.path, size)
        sections += MetaSection("File", rows)
        if (summaryOnly) return sections

        if (src == null) {
            sections += MetaSection("Notice", listOf(MetaRow("Content", "This file couldn't be opened for reading, so only its name and size are shown.")))
            return sections
        }
        val path = src.path
        val kind = sig?.label.orEmpty()
        runCatching {
            when {
                kind.startsWith("ZIP") -> sections += zipSections(ctx, f, path, ext)
                kind.startsWith("GZIP") -> sections += gzipSections(path)
                kind.startsWith("TAR") -> sections += tarSections(path)
                kind.startsWith("7-Zip") -> sections += MetaSection("7-Zip", listOf(MetaRow("Format version", "${head.first[6].toInt()}.${head.first[7].toInt()}")))
                kind.startsWith("RAR") -> sections += MetaSection("RAR", listOf(MetaRow("Format version", if (head.second > 7 && head.first[6].toInt() == 0) "RAR 4.x" else "RAR 5.0+")))
                kind.startsWith("PDF") -> sections += pdfSections(path, size)
                kind.startsWith("Windows executable") -> sections += peSections(path)
                kind.startsWith("ELF") -> sections += elfSections(path)
                kind.startsWith("SQLite") -> sections += sqliteSections(head.first, head.second)
                kind.startsWith("Mach-O") || kind.startsWith("Java") -> sections += machOrClassSections(head.first, head.second, kind)
                kind.startsWith("Microsoft Office") -> sections += oleSections(path, ext)
                sig == null -> sections += textSections(path, head.first, head.second, ext)
            }
            Unit
        }
        if (sections.none { it.title != "File" }) {
            sections += MetaSection("Header bytes", listOf(MetaRow("First 16 bytes", hex(head.first, 0, minOf(16, head.second)).chunked(2).joinToString(" "))))
        }
        return sections
    } finally {
        src?.close()
    }
}

private val zipLikeExts = setOf(
    "docx", "xlsx", "pptx", "odt", "ods", "odp", "epub", "apk", "aab", "jar", "war", "xpi", "apkm", "xapk", "kmz", "cbz", "xps", "vsdx", "3mf", "nupkg", "whl", "ipa", "mobileconfig",
)

private fun sameFamily(a: String, b: String): Boolean {
    val major = a.substringBefore('/')
    if (major != b.substringBefore('/')) return false
    if (major != "image" && major != "audio" && major != "video") return true
    return a == b || a.substringAfter('/').take(3) == b.substringAfter('/').take(3)
}

// ---- ZIP family -------------------------------------------------------------------------------------

private fun zipSections(ctx: Context, f: PickedFile, path: String, ext: String): List<MetaSection> {
    val out = mutableListOf<MetaSection>()
    ZipFile(path).use { z ->
        val entries = ArrayList<ZipEntry>()
        val en = z.entries()
        while (en.hasMoreElements() && entries.size < 200_000) entries += en.nextElement()
        var files = 0; var dirs = 0; var packed = 0L; var raw = 0L
        var newest = 0L; var oldest = Long.MAX_VALUE
        for (e in entries) {
            if (e.isDirectory) dirs++ else files++
            packed += maxOf(0L, e.compressedSize); raw += maxOf(0L, e.size)
            if (e.time > 0) { newest = maxOf(newest, e.time); oldest = minOf(oldest, e.time) }
        }
        val rows = mutableListOf(
            MetaRow("Entries", entries.size.toString()), MetaRow("Files", files.toString()), MetaRow("Folders", dirs.toString()),
            MetaRow("Packed size", fmtSize(packed)), MetaRow("Unpacked size", fmtSize(raw)),
        )
        if (raw > 0) rows += MetaRow("Compression", String.format(Locale.US, "%.1f%% smaller", (1.0 - packed.toDouble() / raw) * 100))
        if (newest > 0) rows += MetaRow("Newest entry", fmtDate(newest))
        if (oldest != Long.MAX_VALUE) rows += MetaRow("Oldest entry", fmtDate(oldest))
        z.comment?.takeIf { it.isNotBlank() }?.let { rows += MetaRow("Archive comment", it.take(500)) }
        if (entries.any { it.method == ZipEntry.STORED && !it.isDirectory } && entries.none { it.method == ZipEntry.DEFLATED }) rows += MetaRow("Method", "Stored (no compression)")
        out += MetaSection("Archive", rows)

        val names = entries.map { it.name }.toHashSet()
        fun read(name: String, cap: Int = 1 shl 20): ByteArray? =
            z.getEntry(name)?.let { e -> runCatching { z.getInputStream(e).use { it.readNBytesCompat(cap) } }.getOrNull() }

        if ("AndroidManifest.xml" in names && ("classes.dex" in names || "resources.arsc" in names)) out += apkSections(ctx, f, path, names)
        if ("word/document.xml" in names || "xl/workbook.xml" in names || "ppt/presentation.xml" in names || "docProps/core.xml" in names) {
            val kind = when {
                "word/document.xml" in names -> "Word document"
                "xl/workbook.xml" in names -> "Excel workbook"
                "ppt/presentation.xml" in names -> "PowerPoint presentation"
                else -> "Office Open XML package"
            }
            val rows2 = mutableListOf(MetaRow("Format", kind))
            read("docProps/core.xml")?.let { rows2 += xmlLeafRows(it.inputStream()) }
            read("docProps/app.xml")?.let { rows2 += xmlLeafRows(it.inputStream(), setOf("TitlesOfParts", "HeadingPairs", "vt:lpstr", "vt:i4", "vt:variant")) }
            read("docProps/custom.xml")?.let { c -> xmlLeafRows(c.inputStream()).takeIf { it.isNotEmpty() }?.let { rows2 += it.map { r -> MetaRow("Custom: ${r.key}", r.value) } } }
            out += MetaSection("Document properties", rows2)
            val media = names.count { it.startsWith("word/media/") || it.startsWith("xl/media/") || it.startsWith("ppt/media/") }
            val macros = "word/vbaProject.bin" in names || "xl/vbaProject.bin" in names || "ppt/vbaProject.bin" in names
            out += MetaSection("Document contents", listOf(MetaRow("Embedded media files", media.toString()), MetaRow("Contains macros", if (macros) "Yes" else "No")))
        }
        val mimetype = read("mimetype", 256)?.toString(Charsets.US_ASCII)?.trim()
        if (mimetype != null) {
            val rows2 = mutableListOf(MetaRow("MIME type inside", mimetype))
            if (mimetype.startsWith("application/vnd.oasis.opendocument")) {
                read("meta.xml")?.let { rows2 += xmlLeafRows(it.inputStream()) }
                out += MetaSection("Document properties", rows2)
            } else if (mimetype == "application/epub+zip") {
                val opf = names.firstOrNull { it.endsWith(".opf") }
                opf?.let { n -> read(n)?.let { rows2 += xmlLeafRows(it.inputStream(), setOf("item", "itemref", "meta")) } }
                out += MetaSection("Book", rows2)
            }
        }
        read("META-INF/MANIFEST.MF", 64 * 1024)?.let { b ->
            val main = String(b, Charsets.UTF_8).replace("\r\n ", "").replace("\n ", "").lineSequence()
                .takeWhile { it.isNotBlank() }.mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { MetaRow(l.substring(0, it), l.substring(it + 1).trim()) } }.toList()
            if (main.isNotEmpty()) out += MetaSection("Java manifest", main)
        }
        val shown = entries.take(150)
        out += MetaSection(
            "Contents (${entries.size})",
            shown.map { e -> MetaRow(e.name, if (e.isDirectory) "folder" else fmtSize(maxOf(0L, e.size))) } +
                (if (entries.size > shown.size) listOf(MetaRow("…", "${entries.size - shown.size} more entries")) else emptyList()),
        )
    }
    return out
}

private fun InputStream.readNBytesCompat(max: Int): ByteArray {
    val bos = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (total < max) {
        val n = read(buf, 0, minOf(buf.size, max - total))
        if (n < 0) break
        bos.write(buf, 0, n); total += n
    }
    return bos.toByteArray()
}

@Suppress("DEPRECATION")
private fun apkSections(ctx: Context, f: PickedFile, path: String, names: Set<String>): List<MetaSection> {
    var tmp: File? = null
    try {
        var apkPath = path
        if (path.startsWith("/proc/")) {
            val copy = File(ctx.cacheDir, "apk_${System.nanoTime()}.apk")
            tmp = copy
            ctx.contentResolver.openInputStream(f.uri)?.use { i -> copy.outputStream().use { o -> i.copyTo(o) } }
            apkPath = copy.path
        }
        val pm = ctx.packageManager
        val info = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_PERMISSIONS) ?: return emptyList()
        val app = info.applicationInfo
        val rows = mutableListOf<MetaRow>()
        if (app != null) {
            app.sourceDir = apkPath; app.publicSourceDir = apkPath
            runCatching { app.loadLabel(pm).toString() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { rows += MetaRow("App name", it) }
        }
        rows += MetaRow("Package", info.packageName)
        rows += MetaRow("Version name", info.versionName ?: "-")
        rows += MetaRow("Version code", PackageInfoCompat.getLongVersionCode(info).toString())
        if (app != null) {
            rows += MetaRow("Minimum Android (API)", app.minSdkVersion.toString())
            rows += MetaRow("Target Android (API)", app.targetSdkVersion.toString())
            rows += MetaRow("Debuggable", if (app.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) "Yes" else "No")
        }
        val abis = names.mapNotNull { Regex("^lib/([^/]+)/").find(it)?.groupValues?.get(1) }.toSortedSet()
        if (abis.isNotEmpty()) rows += MetaRow("Native code (ABIs)", abis.joinToString(", "))
        info.requestedPermissions?.let { p ->
            rows += MetaRow("Permissions (${p.size})", p.joinToString("\n") { it.removePrefix("android.permission.") })
        }
        return listOf(MetaSection("Android app", rows))
    } catch (_: Throwable) {
        return emptyList()
    } finally {
        tmp?.delete()
    }
}

private fun prettyXml(name: String): String {
    val n = name.substringAfter(':').replace('-', ' ').replace('_', ' ')
        .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").trim().lowercase(Locale.US)
    return n.replaceFirstChar { it.uppercase() }
}

private fun xmlLeafRows(ins: InputStream, skip: Set<String> = emptySet()): List<MetaRow> {
    val rows = mutableListOf<MetaRow>()
    runCatching {
        val p = Xml.newPullParser()
        p.setInput(ins, null)
        var ev = p.eventType
        var name: String? = null
        while (ev != XmlPullParser.END_DOCUMENT && rows.size < 80) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    name = p.name
                    if (p.name.endsWith("document-statistic")) {
                        for (i in 0 until p.attributeCount) rows += MetaRow(prettyXml(p.getAttributeName(i)), p.getAttributeValue(i))
                    }
                }
                XmlPullParser.TEXT -> {
                    val t = p.text?.trim().orEmpty()
                    val n = name
                    if (t.isNotEmpty() && n != null && n !in skip) rows += MetaRow(prettyXml(n), t.take(500))
                }
                XmlPullParser.END_TAG -> name = null
            }
            ev = p.next()
        }
    }
    return rows
}

// ---- gzip / tar ---------------------------------------------------------------------------------------

private fun gzipSections(path: String): List<MetaSection> = RandomAccessFile(path, "r").use { raf ->
    val h = ByteArray(10); raf.readFully(h)
    val flg = h[3].toInt() and 0xFF
    val rows = mutableListOf<MetaRow>()
    rows += MetaRow("Method", if (h[2].toInt() == 8) "Deflate" else "Unknown (${h[2]})")
    val mt = le32(h, 4)
    if (mt > 0) rows += MetaRow("Stored date", fmtDate(mt * 1000))
    rows += MetaRow("Made on", when (h[9].toInt() and 0xFF) { 0 -> "FAT (DOS/Windows)"; 3 -> "Unix"; 7 -> "Macintosh"; 11 -> "NTFS"; 255 -> "Unknown"; else -> "Other (${h[9]})" })
    var skipTo = 10L
    if (flg and 4 != 0) { val x = ByteArray(2); raf.seek(skipTo); raf.readFully(x); skipTo += 2 + le16(x, 0) }
    fun cstr(): String {
        raf.seek(skipTo)
        val bos = java.io.ByteArrayOutputStream()
        while (bos.size() < 1024) { val b = raf.read(); skipTo++; if (b <= 0) break; bos.write(b) }
        return bos.toString("ISO-8859-1")
    }
    if (flg and 8 != 0) rows += MetaRow("Original name", cstr())
    if (flg and 16 != 0) rows += MetaRow("Comment", cstr())
    if (raf.length() > 18) {
        val t = ByteArray(4); raf.seek(raf.length() - 4); raf.readFully(t)
        rows += MetaRow("Uncompressed size", fmtSize(le32(t, 0)) + " (stored modulo 4 GB)")
    }
    listOf(MetaSection("GZIP", rows))
}

private fun octal(b: ByteArray, off: Int, len: Int): Long {
    var v = 0L
    var started = false
    for (i in off until off + len) {
        val c = b[i].toInt()
        if (c in 48..55) { v = v * 8 + (c - 48); started = true } else if (started) break
    }
    return v
}

private fun tarSections(path: String): List<MetaSection> = RandomAccessFile(path, "r").use { raf ->
    val len = raf.length()
    var pos = 0L
    var count = 0; var total = 0L
    val names = mutableListOf<MetaRow>()
    var owner = ""
    val h = ByteArray(512)
    while (pos + 512 <= len && count < 100_000) {
        raf.seek(pos); raf.readFully(h)
        if (h.all { it.toInt() == 0 }) break
        val name = String(h, 0, 100, Charsets.UTF_8).substringBefore('\u0000')
        val sz = octal(h, 124, 12)
        if (count == 0) owner = String(h, 265, 32, Charsets.UTF_8).substringBefore('\u0000') + ":" + String(h, 297, 32, Charsets.UTF_8).substringBefore('\u0000')
        if (names.size < 150) names += MetaRow(name, fmtSize(sz))
        total += sz; count++
        pos += 512 + (sz + 511) / 512 * 512
    }
    val rows = mutableListOf(MetaRow("Entries", count.toString()), MetaRow("Unpacked size", fmtSize(total)))
    if (owner.length > 1) rows += MetaRow("First entry owner", owner)
    listOf(MetaSection("TAR", rows), MetaSection("Contents ($count)", names))
}

// ---- PDF --------------------------------------------------------------------------------------------

private fun pdfDate(s: String): String {
    val m = Regex("""D:(\d{4})(\d{2})?(\d{2})?(\d{2})?(\d{2})?(\d{2})?([Z+\-].*)?""").find(s) ?: return s
    val g = m.groupValues
    return "${g[1]}-${g[2].ifEmpty { "01" }}-${g[3].ifEmpty { "01" }} ${g[4].ifEmpty { "00" }}:${g[5].ifEmpty { "00" }}:${g[6].ifEmpty { "00" }}" +
        (g[7].takeIf { it.isNotEmpty() }?.let { " " + it.replace("'", ":").trimEnd(':') } ?: "")
}

private fun pdfString(raw: String, hexForm: Boolean): String {
    val bytes: ByteArray = if (hexForm) {
        val h = raw.filter { !it.isWhitespace() }.let { if (it.length % 2 == 1) it + "0" else it }
        ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    } else {
        val bos = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                i++
                when (val d = raw[i]) {
                    'n' -> bos.write('\n'.code); 'r' -> bos.write('\r'.code); 't' -> bos.write('\t'.code)
                    'b' -> bos.write(8); 'f' -> bos.write(12)
                    in '0'..'7' -> {
                        var v = d - '0'; var k = 0
                        while (k < 2 && i + 1 < raw.length && raw[i + 1] in '0'..'7') { i++; v = v * 8 + (raw[i] - '0'); k++ }
                        bos.write(v and 0xFF)
                    }
                    else -> bos.write(d.code and 0xFF)
                }
            } else bos.write(c.code and 0xFF)
            i++
        }
        bos.toByteArray()
    }
    return when {
        bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFE && (bytes[1].toInt() and 0xFF) == 0xFF -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        bytes.size >= 3 && (bytes[0].toInt() and 0xFF) == 0xEF && (bytes[1].toInt() and 0xFF) == 0xBB -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        else -> String(bytes, Charsets.ISO_8859_1)
    }.trim()
}

private fun pdfSections(path: String, size: Long): List<MetaSection> {
    val text = RandomAccessFile(path, "r").use { raf ->
        val len = raf.length()
        if (len <= 32L * 1024 * 1024) {
            ByteArray(len.toInt()).also { raf.readFully(it) }
        } else {
            val chunk = 4 * 1024 * 1024
            val a = ByteArray(chunk); raf.readFully(a)
            val b = ByteArray(chunk); raf.seek(len - chunk); raf.readFully(b)
            a + b
        }
    }.toString(Charsets.ISO_8859_1)
    val rows = mutableListOf<MetaRow>()
    Regex("""%PDF-(\d\.\d)""").find(text)?.let { rows += MetaRow("PDF version", it.groupValues[1]) }
    val keys = listOf("Title", "Author", "Subject", "Keywords", "Creator", "Producer", "CreationDate", "ModDate", "Trapped")
    var found = 0
    for (k in keys) {
        val m = Regex("/$k\\s*(?:\\(((?:\\\\.|[^\\\\)])*)\\)|<([0-9A-Fa-f\\s]*)>|/(\\w+))").findAll(text).lastOrNull() ?: continue
        val v = when {
            m.groups[1] != null -> pdfString(m.groupValues[1], false)
            m.groups[2] != null -> pdfString(m.groupValues[2], true)
            else -> m.groupValues[3]
        }
        if (v.isNotBlank()) {
            found++
            rows += MetaRow(if (k == "CreationDate") "Created" else if (k == "ModDate") "Modified" else k, if (k.endsWith("Date")) pdfDate(v) else v.take(500))
        }
    }
    val pages = Regex("""/Type\s*/Pages\b[^>]*?/Count\s+(\d+)""").findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()
        ?: Regex("""/Count\s+(\d+)[^>]*?/Type\s*/Pages\b""").findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()
    pages?.let { rows += MetaRow("Pages", it.toString()) }
    rows += MetaRow("Encrypted", if (text.contains("/Encrypt")) "Yes" else "No")
    if (text.contains("/Linearized")) rows += MetaRow("Fast web view", "Yes (linearized)")
    if (text.contains("/JavaScript") || text.contains("/JS ")) rows += MetaRow("Contains JavaScript", "Yes")
    val xmp = Regex("""<dc:title>.*?<rdf:li[^>]*>([^<]+)</rdf:li>""", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)
    if (found == 0 && xmp != null) rows += MetaRow("Title (XMP)", xmp.trim())
    if (found == 0) rows += MetaRow("Document info", "None found. It may be stored in a compressed part of the file.")
    return listOf(MetaSection("PDF", rows))
}

// ---- SQLite, text, OLE2, Mach-O ----------------------------------------------------------------------------

private fun sqliteSections(h: ByteArray, n: Int): List<MetaSection> {
    if (n < 100) return emptyList()
    val ps = ((h[16].toInt() and 0xFF) shl 8) or (h[17].toInt() and 0xFF)
    val enc = when (be32(h, 56).toInt()) { 1 -> "UTF-8"; 2 -> "UTF-16 little endian"; 3 -> "UTF-16 big endian"; else -> "Unknown" }
    val ver = be32(h, 96)
    val rows = listOf(
        MetaRow("Page size", "${if (ps == 1) 65536 else ps} bytes"),
        MetaRow("Pages", be32(h, 28).toString()),
        MetaRow("Text encoding", enc),
        MetaRow("User version", be32(h, 60).toString()),
        MetaRow("Application ID", "0x" + java.lang.Long.toHexString(be32(h, 68))),
        MetaRow("Written by SQLite", "${ver / 1_000_000}.${ver / 1000 % 1000}.${ver % 1000}"),
        MetaRow("Journal mode", if (h[18].toInt() == 2) "WAL" else "Rollback journal"),
    )
    return listOf(MetaSection("SQLite database", rows))
}

private fun machOrClassSections(h: ByteArray, n: Int, kind: String): List<MetaSection> {
    if (n < 8) return emptyList()
    if (kind.startsWith("Java")) {
        val major = ((h[6].toInt() and 0xFF) shl 8) or (h[7].toInt() and 0xFF)
        val minor = ((h[4].toInt() and 0xFF) shl 8) or (h[5].toInt() and 0xFF)
        val java = if (major >= 49) "Java ${major - 44}" else "Java 1.${major - 44}"
        return listOf(MetaSection("Java class", listOf(MetaRow("Class file version", "$major.$minor"), MetaRow("Compiled for", java))))
    }
    val magic = be32(h, 0)
    val is64 = magic == 0xFEEDFACFL || magic == 0xCFFAEDFEL
    val little = magic == 0xCEFAEDFEL || magic == 0xCFFAEDFEL
    fun u32(o: Int) = if (little) le32(h, o) else be32(h, o)
    val cpu = u32(4).toInt()
    val cpuName = when (cpu) { 7 -> "x86"; 0x01000007 -> "x86-64"; 12 -> "ARM"; 0x0100000C -> "ARM64"; 18 -> "PowerPC"; else -> "CPU type $cpu" }
    val type = when (u32(12).toInt()) { 1 -> "Object file"; 2 -> "Executable"; 6 -> "Dynamic library"; 8 -> "Bundle"; 10 -> "Debug symbols"; else -> "Other" }
    return listOf(MetaSection("Mach-O", listOf(MetaRow("Architecture", cpuName), MetaRow("Bits", if (is64) "64-bit" else "32-bit"), MetaRow("Type", type))))
}

private fun oleSections(path: String, ext: String): List<MetaSection> {
    val kind = when (ext) {
        "doc", "dot" -> "Word 97-2003 document"; "xls", "xlt" -> "Excel 97-2003 workbook"; "ppt", "pot" -> "PowerPoint 97-2003 presentation"
        "msi" -> "Windows Installer package"; "msg" -> "Outlook message"; else -> "OLE2 compound file"
    }
    val rows = mutableListOf(MetaRow("Format", kind))
    runCatching {
        RandomAccessFile(path, "r").use { raf ->
            val len = raf.length()
            val read = minOf(len, 8L * 1024 * 1024).toInt()
            val b = ByteArray(read); raf.readFully(b)
            val s = String(b, Charsets.ISO_8859_1)
            Regex("""Microsoft (Word|Excel|PowerPoint|Office)[ A-Za-z0-9.]{0,20}""").find(s)?.let { rows += MetaRow("Application hint", it.value.trim()) }
        }
    }
    rows += MetaRow("Note", "Detailed author and revision properties for this old format are not shown yet.")
    return listOf(MetaSection("Office document", rows))
}

private val textExts = setOf(
    "txt", "md", "csv", "tsv", "json", "xml", "html", "htm", "css", "js", "ts", "kt", "java", "py", "c", "h", "cpp", "yml", "yaml", "ini", "cfg", "conf",
    "log", "sh", "bat", "gradle", "properties", "toml", "sql", "svg", "srt", "vtt", "ass", "lrc", "m3u", "m3u8", "rtf", "tex", "gitignore",
)

private fun textSections(path: String, h: ByteArray, n: Int, ext: String): List<MetaSection> {
    val cap = 1024 * 1024
    val (buf, got) = readHead(path, cap)
    if (got == 0) return listOf(MetaSection("Content", listOf(MetaRow("Content", "Empty file"))))
    val sample = minOf(got, 8192)
    var nuls = 0
    for (i in 0 until sample) if (buf[i].toInt() == 0) nuls++
    val bomEnc = when {
        got >= 3 && (buf[0].toInt() and 0xFF) == 0xEF && (buf[1].toInt() and 0xFF) == 0xBB && (buf[2].toInt() and 0xFF) == 0xBF -> "UTF-8 with BOM"
        got >= 2 && (buf[0].toInt() and 0xFF) == 0xFF && (buf[1].toInt() and 0xFF) == 0xFE -> "UTF-16 little endian"
        got >= 2 && (buf[0].toInt() and 0xFF) == 0xFE && (buf[1].toInt() and 0xFF) == 0xFF -> "UTF-16 big endian"
        else -> null
    }
    val isText = bomEnc != null || (nuls == 0)
    if (!isText && ext !in textExts) {
        return listOf(MetaSection("Content", listOf(MetaRow("Type", "Binary data (format not recognised)"), MetaRow("First 16 bytes", hex(h, 0, minOf(16, n)).chunked(2).joinToString(" ")))))
    }
    val rows = mutableListOf<MetaRow>()
    val dec = Charsets.UTF_8.newDecoder()
    val utf8ok = bomEnc != null || runCatching { dec.decode(java.nio.ByteBuffer.wrap(buf, 0, if (got == cap) got - 3 else got)); true }.getOrDefault(false)
    rows += MetaRow("Encoding", bomEnc ?: if (utf8ok) (if ((0 until sample).all { buf[it] >= 0 }) "ASCII" else "UTF-8") else "Unknown 8-bit (probably Windows-1252 or Latin-1)")
    var crlf = 0; var lf = 0; var cr = 0
    var i = 0
    while (i < got) {
        val c = buf[i].toInt()
        if (c == 13) { if (i + 1 < got && buf[i + 1].toInt() == 10) { crlf++; i++ } else cr++ } else if (c == 10) lf++
        i++
    }
    rows += MetaRow("Line endings", listOfNotNull(if (crlf > 0) "Windows (CRLF)" else null, if (lf > 0) "Unix (LF)" else null, if (cr > 0) "Old Mac (CR)" else null).joinToString(" + ").ifEmpty { "Single line" })
    rows += MetaRow(if (got == cap) "Lines (first 1 MB)" else "Lines", (crlf + lf + cr + 1).toString())
    return listOf(MetaSection("Text file", rows))
}

internal fun isoUtc(millis: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(millis))

internal fun parseIsoUtc(text: String): Long? = runCatching {
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(text.trim().replace("Z", "+00:00"))!!.time
}.getOrNull()
