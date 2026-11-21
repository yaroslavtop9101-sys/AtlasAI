package com.example.atlasai

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Учёт лимитов Google по моделям: запросы за последнюю минуту, блокировки после ошибки 429,
 * недоступные модели и закрытый поиск. Живёт только пока запущено приложение.
 */
object Limits {
    class Model(val id: String, val title: String, val rpm: Int)

    /** Порядок = порядок запасных вариантов в режиме «Авто». Лимиты в минуту взяты из Google AI Studio. */
    val MODELS = listOf(
        Model("gemini-3.8-flash", "Gemini 3.8 Flash", 5),
        Model("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", 15),
        Model("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite", 15),
        Model("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite", 10)
    )

    private val sent = HashMap<String, ArrayList<Long>>()
    private val blocked = HashMap<String, Long>()
    private val dead = HashSet<String>()
    private var searchBlockedUntil = 0L

    @Synchronized fun isDead(id: String) = id in dead

    @Synchronized fun kill(id: String) {
        dead.add(id)
    }

    @Synchronized fun record(id: String) {
        sent.getOrPut(id) { ArrayList() }.add(System.currentTimeMillis())
    }

    @Synchronized fun block(id: String, ms: Long) {
        blocked[id] = System.currentTimeMillis() + ms
    }

    @Synchronized fun blockSearch(ms: Long) {
        searchBlockedUntil = System.currentTimeMillis() + ms
    }

    @Synchronized fun searchBlocked() = System.currentTimeMillis() < searchBlockedUntil

    /** Сколько миллисекунд ждать до следующего запроса к модели (0 = можно сейчас). */
    @Synchronized fun waitMs(id: String): Long {
        val now = System.currentTimeMillis()
        var w = (blocked[id] ?: 0L) - now
        val rpm = MODELS.firstOrNull { it.id == id }?.rpm ?: 5
        val l = sent[id]
        if (l != null) {
            l.removeAll { it < now - 60_000 }
            if (l.size >= rpm) w = maxOf(w, l[0] + 60_000 - now)
        }
        return maxOf(w, 0L)
    }

    fun chain(selected: String): List<String> =
        if (MODELS.any { it.id == selected }) listOf(selected) else MODELS.map { it.id }

    /** Минимальное ожидание среди доступных моделей выбора; null, если все модели недоступны. */
    fun minWait(selected: String): Long? {
        val ids = chain(selected).filter { !isDead(it) }
        if (ids.isEmpty()) return null
        return ids.minOf { waitMs(it) }
    }

    /** Лимиты в день Google обновляет в полночь по тихоокеанскому времени. */
    fun untilPacificMidnightMs(): Long {
        val zone = ZoneId.of("America/Los_Angeles")
        val now = ZonedDateTime.now(zone)
        val next = now.toLocalDate().plusDays(1).atStartOfDay(zone)
        return next.toInstant().toEpochMilli() - System.currentTimeMillis()
    }

    fun fmt(ms: Long): String {
        val s = (ms + 999) / 1000
        if (s >= 3600) {
            return "${s / 3600} ${if (Tr.ru) "ч" else "h"} ${(s % 3600) / 60} ${if (Tr.ru) "мин" else "min"}"
        }
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }
}

/** Решает, нужен ли поиск Google и можно ли ответить локально. */
object Router {
    private val FRESH = listOf(
        "погод", "новост", "курс", "сегодня", "сейчас", "последн", "счёт", "счет", "матч", "цена", "цены", "стоит",
        "weather", "news", "today", "right now", "latest", "score", "price", "stock", "exchange rate", "currently"
    )

    fun needsSearch(t: String): Boolean {
        val l = t.lowercase()
        return FRESH.any { l.contains(it) }
    }

    /** Короткие фразы можно сначала попробовать ответить локально. */
    fun isShort(t: String): Boolean = t.trim().split(Regex("\\s+")).size <= 5
}
