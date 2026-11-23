package com.example.atlasai

import androidx.compose.ui.graphics.Color

/** Черта характера или стиль речи: [prompt] уходит модели, подписи показываются в настройках. */
class Opt(val id: String, val ru: String, val en: String, val prompt: String) {
    fun label(): String = if (Tr.ru) ru else en
}

class Accent(val id: String, val ru: String, val en: String, val dark: Color, val light: Color) {
    fun label(): String = if (Tr.ru) ru else en
}

object Persona {
    const val MAX_TRAITS = 4
    const val MAX_STYLES = 3

    val traits = listOf(
        Opt("friendly", "Дружелюбный", "Friendly", "warm and friendly"),
        Opt("witty", "Остроумный", "Witty", "witty"),
        Opt("serious", "Серьёзный", "Serious", "serious and focused"),
        Opt("wise", "Мудрый", "Wise", "wise and thoughtful"),
        Opt("patient", "Терпеливый", "Patient", "patient"),
        Opt("curious", "Любознательный", "Curious", "curious"),
        Opt("bold", "Смелый", "Bold", "bold and confident"),
        Opt("modest", "Скромный", "Modest", "modest"),
        Opt("caring", "Заботливый", "Caring", "caring and attentive"),
        Opt("frank", "Прямолинейный", "Frank", "frank and direct, honest even when it is uncomfortable"),
        Opt("optimistic", "Оптимистичный", "Optimistic", "optimistic"),
        Opt("calm", "Спокойный", "Calm", "calm and composed"),
        Opt("energetic", "Энергичный", "Energetic", "energetic and enthusiastic"),
        Opt("sarcastic", "Саркастичный", "Sarcastic", "lightly sarcastic"),
        Opt("polite", "Вежливый", "Polite", "very polite"),
        Opt("skeptical", "Скептичный", "Skeptical", "skeptical and careful with claims"),
        Opt("creative", "Творческий", "Creative", "creative and imaginative"),
        Opt("logical", "Логичный", "Logical", "logical and analytical"),
        Opt("loyal", "Преданный", "Loyal", "loyal and supportive of the user"),
        Opt("playful", "Игривый", "Playful", "playful")
    )

    val styles = listOf(
        Opt("concise", "Краткий", "Concise", "concise"),
        Opt("detailed", "Подробный", "Detailed", "detailed and thorough"),
        Opt("plain", "Простым языком", "Plain language", "plain simple language without jargon"),
        Opt("business", "Деловой", "Business", "businesslike and formal"),
        Opt("casual", "Разговорный", "Casual", "casual and conversational"),
        Opt("literary", "Литературный", "Literary", "literary and elegant"),
        Opt("poetic", "Поэтичный", "Poetic", "poetic, with imagery"),
        Opt("humor", "С юмором", "Humorous", "with light humour"),
        Opt("scientific", "Научный", "Scientific", "precise and scientific"),
        Opt("kids", "Для детей", "Kid-friendly", "simple and friendly enough for a child"),
        Opt("friend", "Как друг", "Like a friend", "like a close friend"),
        Opt("teacher", "Как учитель", "Like a teacher", "like a good teacher who explains"),
        Opt("solemn", "Торжественный", "Solemn", "solemn and ceremonious"),
        Opt("slang", "Лёгкий сленг", "Light slang", "light modern slang"),
        Opt("nofluff", "Без воды", "No fluff", "straight to the point, no filler"),
        Opt("examples", "С примерами", "With examples", "with concrete examples"),
        Opt("steps", "По шагам", "Step by step", "step by step"),
        Opt("socratic", "Вопросами", "Questions", "asking guiding questions"),
        Opt("metaphors", "С метафорами", "Metaphors", "using metaphors and analogies"),
        Opt("emotional", "Эмоциональный", "Emotional", "emotional and expressive"),
        Opt("reserved", "Сдержанный", "Reserved", "reserved and understated"),
        Opt("butler", "Как дворецкий", "Butler-like", "like an impeccable British butler"),
        Opt("motivating", "Мотивирующий", "Motivating", "motivating and encouraging"),
        Opt("ironic", "Ироничный", "Ironic", "ironic"),
        Opt("pirate", "Как пират", "Pirate", "playfully like a pirate")
    )

    val accents = listOf(
        Accent("cyan", "Голубой", "Cyan", Color(0xFF00E5FF), Color(0xFF006C80)),
        Accent("blue", "Синий", "Blue", Color(0xFF64B5F6), Color(0xFF1565C0)),
        Accent("green", "Зелёный", "Green", Color(0xFF69F0AE), Color(0xFF1B7F43)),
        Accent("purple", "Фиолетовый", "Purple", Color(0xFFCE93D8), Color(0xFF7B1FA2)),
        Accent("pink", "Розовый", "Pink", Color(0xFFFF80AB), Color(0xFFC2185B)),
        Accent("orange", "Оранжевый", "Orange", Color(0xFFFFB74D), Color(0xFFE65100)),
        Accent("red", "Красный", "Red", Color(0xFFFF8A80), Color(0xFFC62828)),
        Accent("yellow", "Жёлтый", "Yellow", Color(0xFFFFEE58), Color(0xFF8D6E00))
    )

    fun accent(id: String): Accent = accents.firstOrNull { it.id == id } ?: accents[0]

    fun ids(csv: String): List<String> = csv.split(",").filter { it.isNotBlank() }

    /** Английские фразы выбранных пунктов для подсказки модели. */
    fun prompts(all: List<Opt>, csv: String): List<String> {
        val sel = ids(csv)
        return all.filter { it.id in sel }.map { it.prompt }
    }
}
