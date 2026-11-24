package com.example.atlasai

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.io.IOException

/**
 * Локальная модель Gemma (формат .task) через MediaPipe LLM Inference.
 * Файл модели пользователь выбирает один раз (/local), он копируется в память приложения.
 */
object LocalLlm {
    private const val FILE = "atlas_model.task"

    /** Максимальная длина всего запроса к модели в символах (чтобы влезть в контекст маленькой модели). */
    private const val PROMPT_CHARS = 2400

    @Volatile
    private var engine: LlmInference? = null

    @Volatile
    private var enginePath = ""

    fun file(ctx: Context): File = File(ctx.filesDir, FILE)

    fun ready(ctx: Context): Boolean {
        val f = file(ctx)
        return f.isFile && f.length() > 1_000_000
    }

    fun sizeMb(ctx: Context): Long = file(ctx).length() / (1024 * 1024)

    /** Копирует выбранный пользователем файл в память приложения. Вызывать не из главного потока. */
    fun importModel(ctx: Context, uri: Uri) {
        unload()
        val tmp = File(ctx.filesDir, "$FILE.part")
        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("cannot open file")
            input.use { src -> tmp.outputStream().use { dst -> src.copyTo(dst, 1 shl 20) } }
            if (tmp.length() < 50L * 1024 * 1024) {
                throw IOException(if (Tr.ru) "файл слишком маленький для модели" else "file is too small for a model")
            }
            val dst = file(ctx)
            if (dst.exists()) dst.delete()
            if (!tmp.renameTo(dst)) throw IOException("cannot save model")
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
    }

    fun delete(ctx: Context) {
        unload()
        file(ctx).delete()
    }

    @Synchronized
    fun unload() {
        try {
            engine?.close()
        } catch (_: Throwable) {
        }
        engine = null
        enginePath = ""
    }

    @Synchronized
    private fun engine(ctx: Context): LlmInference {
        val path = file(ctx).absolutePath
        val cur = engine
        if (cur != null && enginePath == path) return cur
        var last: Throwable? = null
        for (tokens in intArrayOf(1536, 1024)) {
            try {
                val opts = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(path)
                    .setMaxTokens(tokens)
                    .build()
                val e = LlmInference.createFromOptions(ctx.applicationContext, opts)
                engine = e
                enginePath = path
                return e
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: IllegalStateException("model load failed")
    }

    /** Блокирующий вызов: первая загрузка модели занимает несколько секунд. Вызывать не из главного потока. */
    @Synchronized
    fun generate(ctx: Context, prompt: String): String {
        val out = engine(ctx).generateResponse(prompt) ?: ""
        return out.replace("<end_of_turn>", "").trim()
    }

    /** Собирает запрос в формате Gemma (чередование user / model). */
    fun buildPrompt(
        system: String,
        history: List<Msg>,
        text: String,
        hits: List<WebSearch.Hit>,
        searched: Boolean
    ): String {
        val turns: List<Pair<String, String>> = history
            .filter { !it.silent && it.text.isNotBlank() }
            .takeLast(4)
            .map { (if (it.fromUser) "user" else "model") to it.text.trim().take(240) }
            .dropWhile { it.first != "user" }

        var q = text.trim().ifBlank { "." }.take(400)
        if (hits.isNotEmpty()) {
            val sb = StringBuilder("Search results from the internet:\n")
            hits.forEachIndexed { i, h ->
                sb.append(i + 1).append(". ").append(h.title).append(": ").append(h.snippet).append('\n')
            }
            sb.append("\nUse these results to answer the question. If they do not contain the answer, say so.\n\nQuestion: ")
            sb.append(q)
            q = sb.toString()
        } else if (searched) {
            q = "The internet search found nothing. Answer from general knowledge and say that you could not check the internet.\n\nQuestion: " + q
        }

        var use = turns
        while (true) {
            val p = assemble(system, use, q)
            if (p.length <= PROMPT_CHARS || use.isEmpty()) return p
            use = use.drop(2)
        }
    }

    private fun assemble(system: String, turns: List<Pair<String, String>>, q: String): String {
        val all = turns + ("user" to q)
        val sb = StringBuilder()
        all.forEachIndexed { i, t ->
            sb.append("<start_of_turn>").append(t.first).append('\n')
            if (i == 0) sb.append(system).append("\n\n")
            sb.append(t.second).append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }
}
