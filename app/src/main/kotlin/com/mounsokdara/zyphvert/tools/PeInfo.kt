package com.mounsokdara.zyphvert.tools

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
private fun u32(b: ByteArray, o: Int): Long = u16(b, o).toLong() or (u16(b, o + 2).toLong() shl 16)
private fun u64(b: ByteArray, o: Int): Long = u32(b, o) or (u32(b, o + 4) shl 32)

private class PeSection(val name: String, val vsize: Long, val vaddr: Long, val rawSize: Long, val rawPtr: Long, val flags: Long)

private fun machineName(m: Int) = when (m) {
    0x14c -> "x86 (32-bit)"; 0x8664 -> "x64 (64-bit)"; 0x1c0, 0x1c4 -> "ARM"; 0xaa64 -> "ARM64"
    0x200 -> "Itanium"; 0x5032 -> "RISC-V 32"; 0x5064 -> "RISC-V 64"; 0x0 -> "Any"; else -> "0x" + Integer.toHexString(m)
}

private fun subsystemName(s: Int) = when (s) {
    1 -> "Native (drivers)"; 2 -> "Windows GUI"; 3 -> "Windows console"; 5 -> "OS/2 console"; 7 -> "POSIX console"
    9 -> "Windows CE"; 10 -> "EFI application"; 11 -> "EFI boot driver"; 12 -> "EFI runtime driver"; 14 -> "Xbox"; else -> "Unknown ($s)"
}

internal fun peSections(path: String): List<MetaSection> = RandomAccessFile(path, "r").use { raf ->
    val len = raf.length()
    if (len < 64) return@use emptyList()
    val dos = ByteArray(64); raf.readFully(dos)
    val peOff = u32(dos, 0x3C)
    if (peOff <= 0 || peOff + 24 > len) {
        return@use listOf(MetaSection("Executable", listOf(MetaRow("Format", "DOS (MZ) program"))))
    }
    raf.seek(peOff)
    val coff = ByteArray(24); raf.readFully(coff)
    if (coff[0].toInt() != 'P'.code || coff[1].toInt() != 'E'.code || coff[2].toInt() != 0 || coff[3].toInt() != 0) {
        val tag = String(coff, 0, 2, Charsets.ISO_8859_1)
        return@use listOf(MetaSection("Executable", listOf(MetaRow("Format", if (tag == "NE") "16-bit Windows (NE) program" else if (tag == "LE" || tag == "LX") "OS/2 or DOS extender (LE/LX) program" else "DOS (MZ) program"))))
    }
    val machine = u16(coff, 4)
    val nSections = u16(coff, 6).coerceAtMost(96)
    val stamp = u32(coff, 8)
    val optSize = u16(coff, 20)
    val chars = u16(coff, 22)
    val opt = ByteArray(optSize.coerceIn(0, 512)); raf.readFully(opt)
    val magic = if (opt.size >= 2) u16(opt, 0) else 0
    val plus = magic == 0x20b
    val rows = mutableListOf<MetaRow>()
    val isDll = chars and 0x2000 != 0
    val subsystem = if (opt.size > 70) u16(opt, 68) else 0
    val isDriver = subsystem == 1 && chars and 0x1000 != 0
    rows += MetaRow("Type", when { isDll -> "Dynamic library (DLL)"; isDriver -> "Driver"; chars and 0x2 != 0 -> "Executable program"; else -> "Object file" })
    rows += MetaRow("Architecture", machineName(machine) + if (opt.isNotEmpty()) (if (plus) ", PE32+" else ", PE32") else "")
    if (opt.size > 70) rows += MetaRow("Subsystem", subsystemName(subsystem))
    if (stamp > 0) rows += MetaRow("Compiled", fmtDate(stamp * 1000) + " (can be faked by some build tools)")
    if (opt.size >= 4) rows += MetaRow("Linker version", "${opt[2].toInt() and 0xFF}.${opt[3].toInt() and 0xFF}")
    if (opt.size > 46) rows += MetaRow("Minimum Windows", "${u16(opt, 40)}.${u16(opt, 42)}")
    if (opt.size > 20) rows += MetaRow("Entry point", "0x" + java.lang.Long.toHexString(u32(opt, 16)))
    if (opt.size > 36) rows += MetaRow("Image base", "0x" + java.lang.Long.toHexString(if (plus) u64(opt, 24) else u32(opt, 28)))
    if (opt.size > 60) rows += MetaRow("Size in memory", fmtSize(u32(opt, 56)))
    if (opt.size > 72) {
        val dc = u16(opt, 70)
        val prot = listOfNotNull(
            if (dc and 0x40 != 0) "ASLR" else null, if (dc and 0x20 != 0) "High-entropy ASLR" else null,
            if (dc and 0x100 != 0) "DEP (NX)" else null, if (dc and 0x4000 != 0) "Control Flow Guard" else null,
            if (dc and 0x400 != 0) "No SEH" else null,
        )
        rows += MetaRow("Security features", prot.joinToString(", ").ifEmpty { "None declared" })
    }
    val dirsOff = if (plus) 112 else 96
    val numDirs = if (opt.size > dirsOff - 4) u32(opt, dirsOff - 4).toInt().coerceIn(0, 16) else 0
    fun dirRva(i: Int) = if (i < numDirs && opt.size >= dirsOff + i * 8 + 8) u32(opt, dirsOff + i * 8) else 0L
    fun dirSize(i: Int) = if (i < numDirs && opt.size >= dirsOff + i * 8 + 8) u32(opt, dirsOff + i * 8 + 4) else 0L
    if (dirSize(14) > 0) rows += MetaRow(".NET assembly", "Yes (managed code)")
    if (dirSize(4) > 0) rows += MetaRow("Digital signature", "Present (the certificate is not verified here)")
    if (dirSize(1) > 0) rows += MetaRow("Imports other libraries", "Yes")
    if (dirSize(0) > 0) rows += MetaRow("Exports functions", "Yes")

    val secs = mutableListOf<PeSection>()
    raf.seek(peOff + 24 + optSize)
    val st = ByteArray(40)
    for (i in 0 until nSections) {
        if (raf.filePointer + 40 > len) break
        raf.readFully(st)
        secs += PeSection(String(st, 0, 8, Charsets.ISO_8859_1).substringBefore('\u0000'), u32(st, 8), u32(st, 12), u32(st, 16), u32(st, 20), u32(st, 36))
    }
    fun rvaToOff(rva: Long): Long? =
        secs.firstOrNull { rva >= it.vaddr && rva < it.vaddr + maxOf(it.vsize, it.rawSize) }?.let { it.rawPtr + (rva - it.vaddr) }

    val out = mutableListOf(MetaSection("Executable", rows))
    val version = runCatching { readVersionInfo(raf, len, dirRva(2), dirSize(2), ::rvaToOff) }.getOrNull().orEmpty()
    if (version.isNotEmpty()) out += MetaSection("Version info", version)
    if (secs.isNotEmpty()) {
        out += MetaSection(
            "Sections (${secs.size})",
            secs.map { s ->
                val f = s.flags
                val perm = (if (f and 0x40000000L != 0L) "R" else "-") + (if (f and 0x80000000L != 0L) "W" else "-") + (if (f and 0x20000000L != 0L) "X" else "-")
                MetaRow(s.name.ifEmpty { "(unnamed)" }, "${fmtSize(s.rawSize)} on disk · $perm")
            },
        )
    }
    out
}

private fun readVersionInfo(raf: RandomAccessFile, len: Long, rsrcRva: Long, rsrcSize: Long, toOff: (Long) -> Long?): List<MetaRow> {
    if (rsrcRva == 0L || rsrcSize == 0L) return emptyList()
    val base = toOff(rsrcRva) ?: return emptyList()
    fun readAt(off: Long, n: Int): ByteArray? {
        if (off < 0 || off + n > len) return null
        val b = ByteArray(n); raf.seek(off); raf.readFully(b); return b
    }
    fun entries(dirOff: Long): List<Pair<Long, Long>> {
        val h = readAt(dirOff, 16) ?: return emptyList()
        val count = (u16(h, 12) + u16(h, 14)).coerceAtMost(256)
        val body = readAt(dirOff + 16, count * 8) ?: return emptyList()
        return (0 until count).map { u32(body, it * 8) to u32(body, it * 8 + 4) }
    }
    val top = entries(base).firstOrNull { it.first == 16L } ?: return emptyList()
    if (top.second and 0x80000000L == 0L) return emptyList()
    val langDir = entries(base + (top.second and 0x7FFFFFFFL)).firstOrNull() ?: return emptyList()
    if (langDir.second and 0x80000000L == 0L) return emptyList()
    val leaf = entries(base + (langDir.second and 0x7FFFFFFFL)).firstOrNull() ?: return emptyList()
    val dataEntry = readAt(base + (leaf.second and 0x7FFFFFFFL), 16) ?: return emptyList()
    val dataOff = toOff(u32(dataEntry, 0)) ?: return emptyList()
    val dataLen = u32(dataEntry, 4).toInt().coerceIn(0, 128 * 1024)
    val b = readAt(dataOff, dataLen) ?: return emptyList()
    return parseVersionBlob(b)
}

private class VerBlock(val key: String, val valueOff: Int, val valueLen: Int, val isText: Boolean, val childStart: Int, val end: Int)

private fun readBlock(b: ByteArray, pos: Int, limit: Int): VerBlock? {
    if (pos < 0 || pos + 6 > limit) return null
    val len = u16(b, pos)
    val vlen = u16(b, pos + 2)
    val type = u16(b, pos + 4)
    if (len < 6) return null
    val end = minOf(pos + len, limit)
    var p = pos + 6
    val sb = StringBuilder()
    while (p + 2 <= end) {
        val c = u16(b, p); p += 2
        if (c == 0) break
        sb.append(c.toChar())
    }
    p = (p + 3) and 3.inv()
    val valueBytes = if (type == 1) vlen * 2 else vlen
    return VerBlock(sb.toString(), p, vlen, type == 1, (p + valueBytes + 3) and 3.inv(), end)
}

private fun parseVersionBlob(b: ByteArray): List<MetaRow> {
    val root = readBlock(b, 0, b.size) ?: return emptyList()
    val rows = mutableListOf<MetaRow>()
    if (root.valueLen >= 52 && root.valueOff + 20 <= b.size && u32(b, root.valueOff) == 0xFEEF04BDL) {
        val fvMs = u32(b, root.valueOff + 8); val fvLs = u32(b, root.valueOff + 12)
        val pvMs = u32(b, root.valueOff + 16); val pvLs = u32(b, root.valueOff + 20)
        rows += MetaRow("File version (numeric)", "${fvMs ushr 16}.${fvMs and 0xFFFF}.${fvLs ushr 16}.${fvLs and 0xFFFF}")
        rows += MetaRow("Product version (numeric)", "${pvMs ushr 16}.${pvMs and 0xFFFF}.${pvLs ushr 16}.${pvLs and 0xFFFF}")
    }
    var pos = root.childStart
    var guard = 0
    while (pos < root.end && guard++ < 8) {
        val blk = readBlock(b, pos, root.end) ?: break
        if (blk.key == "StringFileInfo") {
            var tp = blk.childStart
            var g2 = 0
            while (tp < blk.end && g2++ < 8) {
                val table = readBlock(b, tp, blk.end) ?: break
                var sp = table.childStart
                var g3 = 0
                while (sp < table.end && g3++ < 64) {
                    val str = readBlock(b, sp, table.end) ?: break
                    if (str.isText && str.valueLen > 0) {
                        val chars = minOf(str.valueLen, (b.size - str.valueOff) / 2)
                        val v = String(b, str.valueOff, chars * 2, Charsets.UTF_16LE).trimEnd('\u0000').trim()
                        if (v.isNotEmpty() && str.key.isNotEmpty()) rows += MetaRow(str.key, v.take(300))
                    }
                    sp = (str.end + 3) and 3.inv()
                }
                tp = (table.end + 3) and 3.inv()
            }
        }
        pos = (blk.end + 3) and 3.inv()
    }
    return rows
}

internal fun elfSections(path: String): List<MetaSection> = RandomAccessFile(path, "r").use { raf ->
    val h = ByteArray(64)
    val n = raf.read(h)
    if (n < 20) return@use emptyList()
    val is64 = h[4].toInt() == 2
    val little = h[5].toInt() != 2
    val bb = ByteBuffer.wrap(h).order(if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
    val type = bb.getShort(16).toInt() and 0xFFFF
    val machine = bb.getShort(18).toInt() and 0xFFFF
    val rows = mutableListOf<MetaRow>()
    rows += MetaRow("Bits", if (is64) "64-bit" else "32-bit")
    rows += MetaRow("Byte order", if (little) "Little endian" else "Big endian")
    rows += MetaRow("Type", when (type) { 1 -> "Relocatable object"; 2 -> "Executable"; 3 -> "Shared library or PIE executable"; 4 -> "Core dump"; else -> "Other ($type)" })
    rows += MetaRow("Architecture", when (machine) {
        3 -> "x86"; 8 -> "MIPS"; 20 -> "PowerPC"; 21 -> "PowerPC 64"; 40 -> "ARM"; 62 -> "x86-64"; 183 -> "ARM64 (AArch64)"; 243 -> "RISC-V"; else -> "Machine $machine"
    })
    rows += MetaRow("OS ABI", when (h[7].toInt() and 0xFF) { 0 -> "System V (Linux/Android)"; 3 -> "Linux"; 9 -> "FreeBSD"; 12 -> "OpenBSD"; else -> "Other (${h[7]})" })
    if (n >= 32) rows += MetaRow("Entry point", "0x" + java.lang.Long.toHexString(if (is64) bb.getLong(24) else bb.getInt(24).toLong() and 0xFFFFFFFFL))
    listOf(MetaSection("ELF", rows))
}
