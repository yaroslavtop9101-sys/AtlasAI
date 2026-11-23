package com.example.atlasai

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Содержимое, которым поделились из другого приложения («Поделиться → Атлас»). */
object Inbox {
    var text by mutableStateOf<String?>(null)
    val uris = mutableStateListOf<Uri>()
    var pending by mutableStateOf(false)
}

/** Состояние блокировки приложения. */
object AppLock {
    var locked by mutableStateOf(false)

    /** Когда пользователь ушёл из приложения (мс). */
    var leftAt = 0L
}
