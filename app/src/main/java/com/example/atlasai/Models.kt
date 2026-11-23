package com.example.atlasai

class ModelInfo(val id: String, val name: String, val ru: String, val en: String)

/** Модели Gemini, доступные на бесплатном ключе (по вашей странице лимитов Google AI Studio). */
object Models {
    const val DEFAULT = "gemini-3.8-flash"

    val all = listOf(
        ModelInfo(
            "gemini-3.8-flash", "Gemini 3.8 Flash",
            "самая умная из быстрых: сложные вопросы, программирование, длинные задачи. До 5 запросов в минуту",
            "the smartest fast model: hard questions, coding, long tasks. Up to 5 requests per minute"
        ),
        ModelInfo(
            "gemini-3.6-flash", "Gemini 3.6 Flash",
            "прошлое поколение Flash: хороший баланс скорости и понимания фото и файлов, повседневные задачи. До 5 запросов в минуту",
            "previous Flash generation: good balance of speed and understanding photos and files, everyday tasks. Up to 5 requests per minute"
        ),
        ModelInfo(
            "gemini-3-flash-preview", "Gemini 3 Flash (предварительная)",
            "быстрая и сильная для своей цены, но предварительная версия, может измениться. До 5 запросов в минуту",
            "fast and strong for its price, but a preview version that may change. Up to 5 requests per minute"
        ),
        ModelInfo(
            "gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite",
            "самая быстрая и экономная 3.5: простые вопросы и частые запросы. До 15 запросов в минуту",
            "the fastest and cheapest 3.5: simple questions and frequent requests. Up to 15 requests per minute"
        ),
        ModelInfo(
            "gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite",
            "лёгкая и быстрая: короткие ответы, большие лимиты. До 15 запросов в минуту",
            "light and fast: short answers, higher limits. Up to 15 requests per minute"
        )
    )

    fun desc(m: ModelInfo): String = if (Tr.ru) m.ru else m.en

    /** Сначала выбранная модель, потом остальные: если у одной лимит, ответит другая. */
    fun fallbackOrder(selected: String): List<String> =
        listOf(selected) + all.map { it.id }.filter { it != selected }
}
