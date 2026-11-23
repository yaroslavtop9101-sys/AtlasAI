package com.example.atlasai

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Вспомогательные функции для создания файлов. */
object Files {
    /** Безопасное имя файла: без папок и странных символов. */
    fun safeName(raw: String, fallback: String): String {
        var n = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        n = n.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80)
        if (n.isBlank() || n.startsWith(".")) n = fallback
        return n
    }

    /** Простой PDF из обычного текста (A4, перенос строк, несколько страниц). */
    fun pdf(text: String): ByteArray {
        val doc = PdfDocument()
        val w = 595
        val h = 842
        val margin = 40f
        val lineH = 18f
        val paint = Paint().apply {
            textSize = 12f
            isAntiAlias = true
            color = Color.BLACK
        }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        var canvas = page.canvas
        var y = margin + 12f

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            canvas = page.canvas
            y = margin + 12f
        }

        for (para in text.replace("\r", "").split("\n")) {
            var rest = para
            if (rest.isEmpty()) {
                y += lineH
                if (y > h - margin) newPage()
                continue
            }
            while (rest.isNotEmpty()) {
                val fit = paint.breakText(rest, true, w - 2 * margin, null)
                var cut = fit
                if (fit < rest.length) {
                    val sp = rest.lastIndexOf(' ', fit)
                    if (sp > 0) cut = sp
                }
                if (cut <= 0) cut = 1
                canvas.drawText(rest.substring(0, cut).trimEnd(), margin, y, paint)
                rest = rest.substring(cut).trimStart()
                y += lineH
                if (y > h - margin) newPage()
            }
        }
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    fun zip(files: List<Pair<String, ByteArray>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, bytes) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}

/** Создаёт файлы по просьбе модели и хранит их в папке приложения. */
class Creator(ctx: Context) {
    private val dir = File(ctx.filesDir, "generated").apply { mkdirs() }

    fun fileOf(name: String): File = File(dir, name)

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun uniqueName(name: String): String {
        if (!File(dir, name).exists()) return name
        return System.currentTimeMillis().toString(36) + "_" + name
    }

    /** Возвращает имена сохранённых файлов (пусто, если создавать нечего). */
    fun create(args: JSONObject): List<String> {
        val arr = args.optJSONArray("files") ?: return emptyList()
        val used = mutableSetOf<String>()
        val entries = mutableListOf<Pair<String, ByteArray>>()
        var total = 0
        for (i in 0 until minOf(arr.length(), 30)) {
            val o = arr.optJSONObject(i) ?: continue
            var name = Files.safeName(o.optString("name"), "file${i + 1}.txt")
            while (!used.add(name.lowercase())) name = "${i + 1}_$name"
            var content = o.optString("content")
            val bytes = when {
                name.endsWith(".pdf", ignoreCase = true) -> Files.pdf(content)
                else -> {
                    if (name.endsWith(".csv", ignoreCase = true) && !content.startsWith("\uFEFF")) {
                        content = "\uFEFF" + content   // чтобы Excel правильно читал кириллицу
                    }
                    content.toByteArray(Charsets.UTF_8)
                }
            }
            total += bytes.size
            if (total > 5_000_000) break
            entries.add(name to bytes)
        }
        if (entries.isEmpty()) return emptyList()

        val saved = mutableListOf<String>()
        if (args.optBoolean("zip", false) || entries.size > 1) {
            var zipName = Files.safeName(args.optString("zip_name", ""), "atlas_files.zip")
            if (!zipName.endsWith(".zip", ignoreCase = true)) zipName += ".zip"
            zipName = uniqueName(zipName)
            File(dir, zipName).writeBytes(Files.zip(entries))
            saved.add(zipName)
        } else {
            val (name, bytes) = entries[0]
            val unique = uniqueName(name)
            File(dir, unique).writeBytes(bytes)
            saved.add(unique)
        }
        return saved
    }
}
