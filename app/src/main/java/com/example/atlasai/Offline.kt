package com.example.atlasai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Лёгкий «мозг» для разговора без интернета и при исчерпанных лимитах. */
object Offline {
    private val jokesEn = listOf(
        "I would tell you a joke about the cloud, but it is currently unreachable.",
        "Why do programmers prefer dark mode? Because light attracts bugs.",
        "I have been told I am witty. I suspect it is a software feature."
    )
    private val jokesRu = listOf(
        "Рассказал бы шутку про облако, но оно сейчас недоступно.",
        "Почему программисты любят тёмную тему? Потому что свет привлекает баги.",
        "Мне говорили, что я остроумен. Подозреваю, это функция прошивки."
    )

    private fun fmt(v: Double): String =
        if (v == Math.floor(v) && Math.abs(v) < 1e12) v.toLong().toString()
        else String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')

    /** Ответ по правилам или null, если подходящего правила нет. */
    fun match(q: String, name: String): String? {
        val ru = Tr.ru
        val t = q.lowercase().trim()
        val words = t.split(Regex("[\\s,!.?;:]+")).filter { it.isNotEmpty() }
        fun has(vararg w: String) = w.any { t.contains(it) }
        fun hasWord(vararg w: String) = w.any { it in words }
        val loc = if (ru) Locale("ru") else Locale.US
        val now = Date()

        val math = Regex("(-?\\d+(?:[.,]\\d+)?)\\s*([+\\-*/x×÷:])\\s*(-?\\d+(?:[.,]\\d+)?)").find(t)
        if (math != null) {
            val a = math.groupValues[1].replace(',', '.').toDouble()
            val b = math.groupValues[3].replace(',', '.').toDouble()
            val r = when (math.groupValues[2]) {
                "+" -> a + b
                "-" -> a - b
                "*", "x", "×" -> a * b
                else -> {
                    if (b == 0.0) {
                        return if (ru) "На ноль делить нельзя, $name." else "Division by zero is not permitted, $name."
                    }
                    a / b
                }
            }
            return (if (ru) "Получается " else "That comes to ") + fmt(r) + "."
        }
        return when {
            has("который час", "сколько времени", "сколько время", "сколько сейчас", "текущее время",
                "what time is it", "what's the time", "current time", "time now") ->
                (if (ru) "Сейчас " else "It is ") + SimpleDateFormat("HH:mm", loc).format(now) + "."
            has("какое число", "какая дата", "какой сегодня день", "дата сегодня",
                "what date", "what day is", "today's date") ->
                SimpleDateFormat("EEEE, d MMMM yyyy", loc).format(now)
            has("кто ты", "как тебя зовут", "who are you", "your name") ->
                if (ru) "Я Атлас, ваш личный ИИ-ассистент. К вашим услугам, $name."
                else "I am Atlas, your personal AI assistant. At your service, $name."
            has("как дела", "how are you") ->
                if (ru) "Все системы работают штатно. Благодарю, что спросили."
                else "All systems nominal. Thank you for asking."
            hasWord("шутка", "шутку", "анекдот", "joke") ->
                (if (ru) jokesRu else jokesEn).random()
            hasWord("спасибо", "благодарю", "thanks", "thank") ->
                if (ru) "Всегда пожалуйста, $name." else "Always a pleasure, $name."
            hasWord("привет", "здравствуй", "здравствуйте", "hello", "hi", "hey") ||
                has("добрый день", "добрый вечер", "доброе утро", "good morning", "good evening") ->
                if (ru) "Приветствую, $name. Чем могу помочь?" else "Greetings, $name. How may I help?"
            hasWord("пока", "bye", "goodbye") || has("до свидания") ->
                if (ru) "До встречи, $name." else "Until next time, $name."
            else -> null
        }
    }

    /** limited = true: интернет есть, но Google сейчас недоступен (лимит или сбой). */
    fun reply(q: String, name: String, limited: Boolean): String {
        match(q, name)?.let { return it }
        return if (Tr.ru) {
            (if (limited) "Google сейчас недоступен, и мои знания ограничены, $name. " else "Без интернета мои знания ограничены, $name. ") +
                "Я могу поболтать, назвать время и дату, посчитать, включить фонарик, поставить таймер, позвонить или написать."
        } else {
            (if (limited) "Google is unavailable right now and my knowledge is limited, $name. " else "Without a connection my knowledge is limited, $name. ") +
                "I can chat, tell the time and date, do math, use the flashlight, set timers, call and text."
        }
    }
}
