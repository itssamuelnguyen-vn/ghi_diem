package com.samuelcruz.ghidiem

import android.app.Application
import com.samuelcruz.ghidiem.data.AppDatabase
import com.samuelcruz.ghidiem.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the long-lived objects (like a Rails initializer). No DI library: there are only a few. */
class GhiDiemApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val repo: Repo by lazy { Repo(db.matches()) }
    /** For writes that must finish even if the screen that started them closes. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
