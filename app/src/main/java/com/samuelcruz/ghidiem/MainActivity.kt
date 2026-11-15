package com.samuelcruz.ghidiem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.samuelcruz.ghidiem.ui.AppNav
import com.samuelcruz.ghidiem.ui.GhiDiemTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GhiDiemTheme { AppNav() } }
    }
}
