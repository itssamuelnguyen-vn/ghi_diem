package com.samuelcruz.ghidiem.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.samuelcruz.ghidiem.data.Backup
import com.samuelcruz.ghidiem.data.SnapshotFiles
import com.samuelcruz.ghidiem.sync.Drive
import com.samuelcruz.ghidiem.sync.GoogleAuth
import com.samuelcruz.ghidiem.sync.SnapshotSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = app()
    val prefs = app.prefs
    val context = LocalContext.current
    var voice by remember { mutableStateOf(prefs.voice) }
    var beeps by remember { mutableStateOf(prefs.beeps) }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    var volumeKeys by remember { mutableStateOf(prefs.volumeKeys) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        app.scope.launch {
            val r = runCatching { context.contentResolver.openOutputStream(uri)!!.use { Backup.write(app.db, it) } }
            withContext(Dispatchers.Main) { toast(if (r.isSuccess) "Đã sao lưu" else "Sao lưu thất bại: ${r.exceptionOrNull()?.message}") }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Cài đặt") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            FormCard("Khi ghi điểm") {
                Toggle("Đọc tỉ số", "Sau mỗi điểm đọc to tỉ số (bên giao trước), match point, đổi sân. Dùng giọng tiếng Việt của máy nếu có.", voice) {
                    voice = it; prefs.voice = it
                    if (it) app.speaker.speak("Mười một, chín")
                }
                Toggle("Âm báo", "Kêu và rung khi hết giờ chính thức của hiệp (bóng đá) và khi hết giờ hội ý.", beeps) {
                    beeps = it; prefs.beeps = it
                    if (it) app.sounds.whistle()
                }
                Toggle("Rung khi chạm", "Rung nhẹ mỗi lần ghi điểm.", haptics) { haptics = it; prefs.haptics = it }
                Toggle("Phím âm lượng để ghi điểm", "Khi đang mở trận: tăng âm lượng = điểm cho bên trái, giảm = bên phải. Ghi điểm không cần nhìn màn hình.", volumeKeys) {
                    volumeKeys = it; prefs.volumeKeys = it
                }
            }
            FormCard("Sao lưu") {
                Hint("Lưu toàn bộ trận, diễn biến và giải đấu ra một file (.json) để cất hoặc chuyển sang máy khác.")
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ export.launch(Backup.fileName()) }) { Text("Sao lưu ra file") }
                    OutlinedButton({ import.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }) { Text("Khôi phục") }
                }
            }
            FormCard("Google Drive") { SyncSection() }
            Hint("Ghi Điểm ${app.packageManager.getPackageInfo(app.packageName, 0).versionName}")
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(16.dp))
        }
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Khôi phục từ file?") },
            text = { Text("Toàn bộ dữ liệu hiện tại được thay bằng dữ liệu trong file. Không hoàn tác được.") },
            confirmButton = {
                TextButton({
                    restoreUri = null
                    app.scope.launch {
                        val r = runCatching { context.contentResolver.openInputStream(uri)!!.use { Backup.restore(app.db, it) } }
                        if (r.isSuccess) app.changed()
                        withContext(Dispatchers.Main) { toast(if (r.isSuccess) "Đã khôi phục" else "Không khôi phục được: ${r.exceptionOrNull()?.message}") }
                    }
                }) { Text("Khôi phục") }
            },
            dismissButton = { TextButton({ restoreUri = null }) { Text("Thôi") } },
        )
    }
}

@Composable
private fun Toggle(title: String, hint: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, onChange)
    }
}

private val whenFormat = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")

/** Connect, sync now, answer a conflict, disconnect, restore a safety copy (same as Sổ Nợ). */
@Composable
private fun SyncSection() {
    val app = app()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = app.prefs
    var email by remember { mutableStateOf(prefs.syncEmail) }
    var busy by remember { mutableStateOf(false) }
    var showCopies by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf<File?>(null) }
    val state = app.syncState

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    suspend fun finish(token: String) {
        val e = Drive(token).email() ?: error("Không đọc được tài khoản Google")
        prefs.syncEmail = e
        prefs.syncFileId = ""
        prefs.syncVersion = 0
        email = e
        SnapshotSync.schedule(context)
        app.syncSoon()
    }

    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        scope.launch {
            val r = runCatching { finish(GoogleAuth.tokenFromIntent(context, result.data)) }
            busy = false
            if (r.isFailure) toast("Không kết nối được: ${r.exceptionOrNull()?.message}")
        }
    }

    fun connect() {
        busy = true
        scope.launch {
            val r = runCatching {
                when (val a = GoogleAuth.authorize(context, null)) {
                    is GoogleAuth.Result.NeedsUi -> signIn.launch(IntentSenderRequest.Builder(a.intent.intentSender).build())
                    is GoogleAuth.Result.Token -> { finish(a.value); busy = false }
                }
            }
            if (r.isFailure) {
                busy = false
                toast("Không kết nối được Google Drive: ${r.exceptionOrNull()?.message}. Xem hướng dẫn trong README.")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (email.isEmpty()) {
            Hint("Lưu tất cả trận vào thư mục ẩn của app trên Google Drive và giữ nhiều máy giống nhau. Máy nào có thay đổi thì gửi lên; nếu hai máy cùng sửa, app hỏi giữ bản nào.")
            Button(onClick = ::connect, enabled = !busy) { Text(if (busy) "Đang kết nối…" else "Kết nối Google Drive") }
        } else {
            Text(email, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(
                    prefs.lastSyncAt.takeIf { it > 0 }?.let { "Đồng bộ lần cuối " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(whenFormat) },
                    "Có thay đổi chưa gửi".takeIf { prefs.changedAt > prefs.lastSyncAt },
                    prefs.syncError.ifEmpty { null },
                ).joinToString(" · ").ifEmpty { "Chưa đồng bộ" },
                style = MaterialTheme.typography.bodySmall,
                color = if (prefs.syncError.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { app.syncSoon() }) { Text("Đồng bộ ngay") }
                OutlinedButton(onClick = {
                    scope.launch {
                        GoogleAuth.revoke(context, prefs.syncEmail)
                        SnapshotSync.cancel(context)
                        prefs.syncEmail = ""; prefs.syncFileId = ""; prefs.syncVersion = 0; prefs.syncError = ""
                        email = ""
                    }
                }) { Text("Ngắt kết nối") }
            }
            if (state == SnapshotSync.Result.NeedsSignIn) TextButton(onClick = ::connect) { Text("Đăng nhập lại") }
        }
        val copies = remember(showCopies, state) { SnapshotFiles.list(context) }
        if (copies.isNotEmpty()) TextButton(onClick = { showCopies = true }) { Text("Bản lưu an toàn trước khi đồng bộ (${copies.size})") }
    }

    if (state == SnapshotSync.Result.Conflict) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Hai nơi cùng có thay đổi") },
            text = { Text("Máy này và Google Drive (từ máy khác) đều có thay đổi kể từ lần đồng bộ trước. Chọn bản muốn giữ; bản còn lại được cất vào \"Bản lưu an toàn\" trên máy này.") },
            confirmButton = { TextButton(onClick = { app.syncSoon(keep = SnapshotSync.Keep.THIS_PHONE) }) { Text("Giữ bản trên máy này") } },
            dismissButton = { TextButton(onClick = { app.syncSoon(keep = SnapshotSync.Keep.DRIVE) }) { Text("Lấy bản trên Drive") } },
        )
    }
    if (showCopies) {
        val copies = SnapshotFiles.list(context)
        AlertDialog(
            onDismissRequest = { showCopies = false },
            title = { Text("Bản lưu an toàn") },
            text = {
                Column {
                    copies.forEach { f -> TextButton(onClick = { restoring = f; showCopies = false }) { Text(f.nameWithoutExtension) } }
                }
            },
            confirmButton = { TextButton(onClick = { showCopies = false }) { Text("Đóng") } },
        )
    }
    restoring?.let { f ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text("Khôi phục bản này?") },
            text = { Text("Toàn bộ dữ liệu hiện tại được thay bằng \"${f.nameWithoutExtension}\", rồi gửi lên Drive.") },
            confirmButton = {
                TextButton({
                    restoring = null
                    app.scope.launch {
                        val r = runCatching { f.inputStream().use { Backup.restore(app.db, it) } }
                        if (r.isSuccess) app.changed()
                        withContext(Dispatchers.Main) { toast(if (r.isSuccess) "Đã khôi phục" else "Khôi phục thất bại") }
                    }
                }) { Text("Khôi phục") }
            },
            dismissButton = { TextButton({ restoring = null }) { Text("Thôi") } },
        )
    }
}
