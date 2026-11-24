package com.example.atlasai

import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** Поиск в интернете через DuckDuckGo (без ключа). Вызывать только не из главного потока. */
object WebSearch {
    class Hit(val title: String, val url: String, val snippet: String)

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Tablet) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val smallTalk = Regex(
        "^(привет|здравствуй|добрый|как дела|как ты|спасибо|благодар|пока|до свидания|кто ты|как тебя зовут|" +
            "hi|hello|hey|thanks|thank you|bye|how are you|who are you)(\\s|[,.!?]|\\z)"
    )
    private val trigger = Regex(
        "(новост|погод|курс |цена|цены|стоит|сегодня|сейчас|вчера|завтра|недавн|последн|кто такой|кто такая|" +
            "что такое|когда |где |почему |сколько |найди|поищи|загугли|в интернете|who is|what is|when |where |" +
            "why |how many|how much|news|weather|price|latest|today|current|search|look up)"
    )
    private val linkTag = Regex("<a\\s[^>]*result__a[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
    private val snipTag = Regex("<a\\s[^>]*result__snippet[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
    private val hrefAttr = Regex("href=\"([^\"]+)\"")

    /** Нужен ли поиск в сети для этой фразы (режим «авто»). */
    fun needsSearch(text: String): Boolean {
        val t = text.lowercase().trim()
        if (t.length < 4 || smallTalk.containsMatchIn(t)) return false
        return t.contains('?') || trigger.containsMatchIn(t)
    }

    /** Возвращает до [max] результатов. Пустой список, если ничего не нашлось или нет связи. */
    fun search(query: String, max: Int = 3): List<Hit> {
        val q = query.trim()
            .replace(Regex("^(найди|поищи|загугли|search for|look up|google)\\s+", RegexOption.IGNORE_CASE), "")
            .take(200)
        if (q.isBlank()) return emptyList()
        val fromHtml: List<Hit> = try {
            html(q, max)
        } catch (e: Exception) {
            emptyList()
        }
        if (fromHtml.isNotEmpty()) return fromHtml
        return try {
            instant(q, max)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun html(q: String, max: Int): List<Hit> {
        val c = URL("https://html.duckduckgo.com/html/").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 12000
            c.readTimeout = 15000
            c.doOutput = true
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.setRequestProperty("Accept-Language", if (Tr.ru) "ru-RU,ru;q=0.9,en;q=0.5" else "en-US,en;q=0.9")
            val kl = if (Tr.ru) "ru-ru" else "us-en"
            val form = "q=" + URLEncoder.encode(q, "UTF-8") + "&kl=" + kl
            c.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            if (c.responseCode !in 200..299) return emptyList()
            val page = c.inputStream.bufferedReader().use { it.readText() }
            return parse(page, max)
        } finally {
            c.disconnect()
        }
    }

    private fun parse(page: String, max: Int): List<Hit> {
        val out = ArrayList<Hit>()
        val blocks = page.split("<div class=\"result ")
        for (i in 1 until blocks.size) {
            val b = blocks[i]
            if (b.contains("result--ad")) continue
            val m = linkTag.find(b) ?: continue
            val href = hrefAttr.find(m.value)?.groupValues?.get(1) ?: continue
            val url = realUrl(href) ?: continue
            val title = clean(m.groupValues[1])
            if (title.isBlank()) continue
            val snippet = snipTag.find(b)?.let { clean(it.groupValues[1]) } ?: ""
            out.add(Hit(title.take(80), url, snippet.take(200)))
            if (out.size >= max) break
        }
        return out
    }

    private fun realUrl(href: String): String? {
        val h = href.replace("&amp;", "&")
        val m = Regex("uddg=([^&]+)").find(h)
        if (m != null) {
            return try {
                URLDecoder.decode(m.groupValues[1], "UTF-8")
            } catch (e: Exception) {
                null
            }
        }
        return when {
            h.startsWith("http") -> h
            h.startsWith("//") -> "https:" + h
            else -> null
        }
    }

    private fun clean(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()

    /** Запасной путь: официальный краткий ответ DuckDuckGo (Instant Answer). */
    private fun instant(q: String, max: Int): List<Hit> {
        val u = "https://api.duckduckgo.com/?q=" + URLEncoder.encode(q, "UTF-8") +
            "&format=json&no_html=1&skip_disambig=1&no_redirect=1"
        val c = URL(u).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000
            c.readTimeout = 12000
            c.setRequestProperty("User-Agent", UA)
            if (c.responseCode !in 200..299) return emptyList()
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val out = ArrayList<Hit>()
            val abstractText = j.optString("AbstractText", "")
            if (abstractText.isNotBlank()) {
                out.add(Hit(j.optString("Heading", q).ifBlank { q }, j.optString("AbstractURL", ""), abstractText.take(300)))
            }
            val rel = j.optJSONArray("RelatedTopics")
            if (rel != null) {
                var i = 0
                while (i < rel.length() && out.size < max) {
                    val o = rel.optJSONObject(i)
                    val txt = o?.optString("Text", "") ?: ""
                    if (txt.isNotBlank()) {
                        out.add(Hit(txt.take(80), o?.optString("FirstURL", "") ?: "", txt.take(200)))
                    }
                    i++
                }
            }
            return out
        } finally {
            c.disconnect()
        }
    }
}
