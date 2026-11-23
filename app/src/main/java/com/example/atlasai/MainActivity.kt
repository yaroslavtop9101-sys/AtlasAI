package com.example.atlasai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    private lateinit var voice: Voice
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        voice = Voice(this, prefs)
        if (savedInstanceState == null && prefs.appLock) AppLock.locked = true
        handleShare(intent)
        setContent {
            AtlasTheme(prefs) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AtlasApp(prefs, voice)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    override fun onStop() {
        AppLock.leftAt = System.currentTimeMillis()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        // вернулись после долгого отсутствия: блокируем снова
        if (prefs.appLock && AppLock.leftAt > 0 && System.currentTimeMillis() - AppLock.leftAt > 60_000) {
            AppLock.locked = true
        }
    }

    /** «Поделиться → Атлас»: текст и файлы попадают в чат как черновик. */
    @Suppress("DEPRECATION")
    private fun handleShare(i: Intent?) {
        if (i == null) return
        when (i.action) {
            Intent.ACTION_SEND -> {
                val t = i.getStringExtra(Intent.EXTRA_TEXT)
                val u = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (!t.isNullOrBlank()) Inbox.text = t
                if (u != null) Inbox.uris.add(u)
                Inbox.pending = true
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val us = i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (us != null) Inbox.uris.addAll(us)
                Inbox.pending = true
            }
        }
        i.action = null
    }

    override fun onDestroy() {
        voice.shutdown()
        super.onDestroy()
    }
}
