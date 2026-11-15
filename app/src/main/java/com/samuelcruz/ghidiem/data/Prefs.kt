package com.samuelcruz.ghidiem.data

import android.content.Context

/** Settings (like a tiny key-value table). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun bool(key: String, default: Boolean) = sp.getBoolean(key, default)
    private fun setBool(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()
    private fun str(key: String) = sp.getString(key, "") ?: ""
    private fun setStr(key: String, value: String) = sp.edit().putString(key, value).apply()
    private fun long(key: String) = sp.getLong(key, 0)
    private fun setLong(key: String, value: Long) = sp.edit().putLong(key, value).apply()

    /** Read the score aloud after each point (text-to-speech). */
    var voice: Boolean
        get() = bool("voice", false)
        set(v) = setBool("voice", v)

    /** Beep when a period's time is up and when a time-out ends. */
    var beeps: Boolean
        get() = bool("beeps", true)
        set(v) = setBool("beeps", v)

    /** Short buzz on each point. */
    var haptics: Boolean
        get() = bool("haptics", true)
        set(v) = setBool("haptics", v)

    /** Volume up / down give the point to the left / right side while a match is open. */
    var volumeKeys: Boolean
        get() = bool("volume_keys", false)
        set(v) = setBool("volume_keys", v)

    // ---- Google Drive sync (see sync/SnapshotSync)
    var syncEmail: String
        get() = str("sync_email")
        set(v) = setStr("sync_email", v)
    var syncFileId: String
        get() = str("sync_file_id")
        set(v) = setStr("sync_file_id", v)
    var syncVersion: Long
        get() = long("sync_version")
        set(v) = setLong("sync_version", v)
    var lastSyncAt: Long
        get() = long("last_sync_at")
        set(v) = setLong("last_sync_at", v)
    /** Last local change (every repo write). */
    var changedAt: Long
        get() = long("changed_at")
        set(v) = setLong("changed_at", v)
    var syncError: String
        get() = str("sync_error")
        set(v) = setStr("sync_error", v)
}
