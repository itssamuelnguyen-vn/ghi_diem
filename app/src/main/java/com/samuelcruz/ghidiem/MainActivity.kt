package com.samuelcruz.ghidiem

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableLongStateOf
import com.samuelcruz.ghidiem.ui.AppNav
import com.samuelcruz.ghidiem.ui.GhiDiemTheme

class MainActivity : ComponentActivity() {
    private val app by lazy { application as GhiDiemApp }
    /** Match to open, from the widget (0 = none). AppNav navigates to it and resets it. */
    private val openMatch = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openMatch.longValue = intent.getLongExtra(EXTRA_MATCH, 0)
        setContent { GhiDiemTheme { AppNav(openMatch) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openMatch.longValue = intent.getLongExtra(EXTRA_MATCH, 0)
    }

    override fun onStart() {
        super.onStart()
        app.syncSoon(1_000) // pick up changes made on another phone
    }

    /** Volume keys score points while a match screen is open (Settings → "Phím âm lượng"). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = app.volumeHandler
        val volume = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (handler != null && volume && app.prefs.volumeKeys) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) handler(event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_MATCH = "match"
    }
}
