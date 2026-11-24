package com.example.atlasai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Лёгкий «мозг» для разговора без интернета. */
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

    /** Точные ответы без модели: простая математика, время и дата (только в коротких фразах). */
    fun quick(q: String): String? {
        val ru = Tr.ru
        val t = q.lowercase().trim()
        if (t.length > 60) return null
        val loc = if (ru) Locale("ru") else Locale.US
        val now = Date()
        if (!Regex("\\d+-\\d+-\\d+").containsMatchIn(t)) {
            val m = Regex("(-?\\d+(?:[.,]\\d+)?)\\s*([+\\-*/x×÷])\\s*(-?\\d+(?:[.,]\\d+)?)").find(t)
            if (m != null) {
                val a = m.groupValues[1].replace(',', '.').toDouble()
                val b = m.groupValues[3].replace(',', '.').toDouble()
                val r = when (m.groupValues[2]) {
                    "+" -> a + b
                    "-" -> a - b
                    "*", "x", "×" -> a * b
                    else -> if (b == 0.0) return if (ru) "На ноль делить нельзя." else "Division by zero is not permitted." else a / b
                }
                return (if (ru) "Получается " else "That comes to ") + fmt(r) + "."
            }
        }
        if (Regex("(который час|сколько времени|what time|current time)").containsMatchIn(t)) {
            return (if (ru) "Сейчас " else "It is ") + SimpleDateFormat("HH:mm", loc).format(now) + "."
        }
        if (Regex("(какое число|какая дата|какой сегодня день|what date|today's date|what day)").containsMatchIn(t)) {
            return SimpleDateFormat("EEEE, d MMMM yyyy", loc).format(now)
        }
        return null
    }

    fun reply(q: String, name: String): String {
        val ru = Tr.ru
        val t = q.lowercase().trim()
        fun has(vararg w: String) = w.any { t.contains(it) }
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
                else -> if (b == 0.0) return if (ru) "На ноль делить нельзя, ${name}." else "Division by zero is not permitted, ${name}." else a / b
            }
            return (if (ru) "Получается " else "That comes to ") + fmt(r) + "."
        }
        return when {
            has("который час", "сколько времени", "время", "what time", "the time", "current time") ->
                (if (ru) "Сейчас " else "It is ") + SimpleDateFormat("HH:mm", loc).format(now) + "."
            has("какое число", "какая дата", "какой день", "дата сегодня", "what date", "what day", "today's date") ->
                SimpleDateFormat("EEEE, d MMMM yyyy", loc).format(now)
            has("кто ты", "как тебя зовут", "who are you", "your name") ->
                if (ru) "Я Атлас, ваш личный ИИ-ассистент. К вашим услугам, $name."
                else "I am Atlas, your personal AI assistant. At your service, $name."
            has("как дела", "как ты", "how are you") ->
                if (ru) "Все системы работают штатно. Благодарю, что спросили."
                else "All systems nominal. Thank you for asking."
            has("шутк", "анекдот", "joke") ->
                (if (ru) jokesRu else jokesEn).random()
            has("спасибо", "благодар", "thank") ->
                if (ru) "Всегда пожалуйста, $name." else "Always a pleasure, $name."
            has("привет", "здравствуй", "добрый", "hello", "hey", "good morning", "good evening") || t == "hi" ->
                if (ru) "Приветствую, $name. Чем могу помочь?" else "Greetings, $name. How may I help?"
            has("пока", "до свидания", "goodbye", "bye") ->
                if (ru) "До встречи, $name." else "Until next time, $name."
            else ->
                if (ru) "Без интернета мои знания ограничены, $name. Я могу поболтать, назвать время и дату, посчитать, включить фонарик, поставить таймер, позвонить или написать. Для сложных вопросов подключитесь к сети."
                else "Without a connection my knowledge is limited, $name. I can chat, tell the time and date, do math, use the flashlight, set timers, call and text. For harder questions please connect to the internet."
        }
    }
}
