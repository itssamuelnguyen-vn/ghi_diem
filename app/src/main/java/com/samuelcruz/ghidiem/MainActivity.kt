package com.samuelcruz.ghidiem

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.samuelcruz.ghidiem.ui.AppNav
import com.samuelcruz.ghidiem.ui.GhiDiemTheme

class MainActivity : ComponentActivity() {
    private val app by lazy { application as GhiDiemApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GhiDiemTheme { AppNav() } }
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
}
