package com.samuelcruz.ghidiem

import android.app.Application
import com.samuelcruz.ghidiem.data.AppDatabase
import com.samuelcruz.ghidiem.data.Prefs
import com.samuelcruz.ghidiem.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the long-lived objects (like a Rails initializer). No DI library: there are only a few. */
class GhiDiemApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val prefs: Prefs by lazy { Prefs(this) }
    val repo: Repo by lazy { Repo(db.matches(), ::changed) }
    val speaker: Speaker by lazy { Speaker(this) }
    val sounds: Sounds by lazy { Sounds(this) }
    /** For writes that must finish even if the screen that started them closes. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Set by the open match screen: gets volume-key presses (true = volume up) when the setting is on.
     * MainActivity routes the keys here instead of changing the volume.
     */
    @Volatile var volumeHandler: ((Boolean) -> Unit)? = null

    /** Something was written: remember it for sync (hooks for the widget and Drive come later). */
    fun changed() {
        prefs.changedAt = System.currentTimeMillis()
    }
}
