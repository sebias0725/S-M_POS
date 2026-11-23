package com.shoptracker.pos.ui

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Portable complete SQLite snapshot, preferences, and business profile photo. */
private object PortableBackup {
    private const val DB_NAME = "shop_tracker.db"
    fun export(context: Context, uri: Uri) {
        val temp = File(context.cacheDir, "backup_snapshot_${System.nanoTime()}.db")
        try {
            // VACUUM INTO produces a consistent snapshot even if SQLite is in WAL mode.
            val database = AppDatabase.get(context).openHelper.writableDatabase
            database.execSQL("VACUUM INTO '${temp.absolutePath.replace("'", "''")}'")
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE).all
            val json = JSONObject()
            prefs.forEach { (key, value) -> when (value) {
                is String, is Boolean, is Int, is Long, is Float -> json.put(key, value)
            } }
            context.contentResolver.openOutputStream(uri, "w")!!.use { output ->
                ZipOutputStream(output).use { zip ->
                    fun entry(name: String, bytes: ByteArray) {
                        zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                    }
                    entry(DB_NAME, temp.readBytes())
                    entry("settings.json", json.toString().toByteArray(Charsets.UTF_8))
                    val photo = UserProfile.photoFile(context)
                    if (photo.exists()) entry("profile.jpg", photo.readBytes())
                    entry("format.txt", "ShopSalesTracker portable backup v1".toByteArray())
                }
            }
        } finally { temp.delete() }
    }

    fun restore(context: Context, uri: Uri) {
        val folder = File(context.cacheDir, "restore_${System.nanoTime()}").apply { mkdirs() }
        try {
            val allowed = setOf(DB_NAME, "settings.json", "profile.jpg", "format.txt")
            context.contentResolver.openInputStream(uri)!!.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name !in allowed) throw IllegalArgumentException("Unexpected backup entry")
                        val output = File(folder, entry.name)
                        output.outputStream().use { zip.copyTo(it) }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            val candidate = File(folder, DB_NAME)
            val prefsFile = File(folder, "settings.json")
            require(candidate.exists() && prefsFile.exists() && File(folder, "format.txt").exists()) { "Incomplete backup" }
            val restoredPrefs = JSONObject(prefsFile.readText())
            val test = SQLiteDatabase.openDatabase(candidate.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val cursor = test.rawQuery("PRAGMA integrity_check", null)
                cursor.use { require(it.moveToFirst() && it.getString(0) == "ok") { "Corrupted database" } }
                val required = setOf("products", "business_days", "sales_transactions", "orders", "customers")
                val found = mutableSetOf<String>()
                test.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                    while (c.moveToNext()) found.add(c.getString(0))
                }
                require(found.containsAll(required)) { "Not a compatible v1.8.6 backup" }
                val version = test.version
                require(version == 11) { "Backup database version $version is incompatible" }
            } finally { test.close() }
            // The backup is fully validated before replacing anything.
            val db = AppDatabase.get(context)
            db.close()
            val current = context.getDatabasePath(DB_NAME)
            val rollback = File(folder, "original.db")
            if (current.exists()) current.copyTo(rollback, overwrite = true)
            try {
                candidate.copyTo(current, overwrite = true)
                File(current.path + "-wal").delete()
                File(current.path + "-shm").delete()
                val editor = context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear()
                restoredPrefs.keys().forEach { key -> when (val v = restoredPrefs.get(key)) {
                    is Boolean -> editor.putBoolean(key, v)
                    is Int -> editor.putInt(key, v)
                    is Long -> editor.putLong(key, v)
                    is Double -> editor.putFloat(key, v.toFloat())
                    is String -> editor.putString(key, v)
                } }
                check(editor.commit()) { "Could not restore settings" }
                File(folder, "profile.jpg").takeIf { it.exists() }?.copyTo(UserProfile.photoFile(context), overwrite = true)
            } catch (e: Exception) {
                if (rollback.exists()) rollback.copyTo(current, overwrite = true)
                throw e
            }
        } finally { folder.deleteRecursively() }
    }
}

@Composable
fun BackupControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = withContext(Dispatchers.IO) {
                try { PortableBackup.export(context, uri); "Backup saved. Keep a copy outside your phone." }
                catch (e: Exception) { "Backup failed: ${e.message}" }
            }
            busy = false
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Export your complete database, settings and profile photo to a ZIP. Store it in Drive or another safe location.")
        Button(onClick = { exportPicker.launch("ShopSalesTracker-backup.zip") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Export full backup")
        }
        OutlinedButton(onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Import / restore backup") }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    }
    pendingRestore?.let { uri ->
        AlertDialog(onDismissRequest = { pendingRestore = null },
            title = { Text("Replace all app data?") },
            text = { Text("Restoring permanently replaces your current products, inventory, customers, orders, history and settings. Export a backup first. The app will close after restoring; reopen it to see your data.") },
            confirmButton = { TextButton(onClick = {
                pendingRestore = null
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        try { PortableBackup.restore(context, uri); null }
                        catch (e: Exception) { e.message ?: "Restore failed" }
                    }
                    busy = false
                    if (result == null) {
                        Toast.makeText(context, "Backup restored. Reopen ShopSalesTracker.", Toast.LENGTH_LONG).show()
                        Process.killProcess(Process.myPid())
                    } else message = "Restore failed: $result"
                }
            }) { Text("Replace and restore") } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } })
    }
}
