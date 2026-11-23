package com.shoptracker.pos.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.io.File

object UserProfile {
    var name by mutableStateOf("")
    var palette by mutableStateOf(0xFFE96A25.toInt())
    var photoVersion by mutableIntStateOf(0)
    fun photoFile(context: Context) = File(context.filesDir, "user_profile_photo")
    fun load(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        name = prefs.getString("user_name", "CHANOS") ?: "CHANOS"
        // Install the bundled CHANOS image only when there is no existing user photo.
        // Never overwrite a photo chosen by the user during a later app upgrade.
        val existingPhoto = photoFile(context)
        if (!existingPhoto.exists()) {
            context.assets.open("chanos_profile.jpg").use { source ->
                existingPhoto.outputStream().use { destination -> source.copyTo(destination) }
            }
            photoVersion++
        }
        val storedPalette = prefs.getInt("user_palette", 0)
        palette = if (storedPalette == 0 || (storedPalette == 0xFF00796B.toInt() &&
            prefs.getString("user_name", "CHANOS") == "CHANOS")) {
            // The legacy teal default is replaced for the bundled CHANOS profile.
            0xFFE96A25.toInt()
        } else storedPalette
        LocalThemePreference.dark = prefs.getBoolean("dark_mode", false)
    }
    /** Copies a selected photo into private app storage before updating profile state. */
    fun save(context: Context, newName: String, source: Uri?): Boolean {
        if (newName.isBlank()) return false
        if (source != null) {
            val destination = photoFile(context)
            val temporary = File(context.filesDir, "user_profile_photo.pending")
            try {
                val stream = context.contentResolver.openInputStream(source) ?: return false
                stream.use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
                // Reject invalid or unsupported images rather than replacing a working photo.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temporary.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
                palette = extractPalette(destination)
                photoVersion++
            } catch (_: Exception) {
                temporary.delete()
                return false
            }
        }
        name = newName.trim()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("user_name", name).putInt("user_palette", palette).apply()
        return true
    }
    private fun extractPalette(file: File): Int {
        val options = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return 0xFFE96A25.toInt()
        val bins = HashMap<Int, Int>()
        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
            val c = bitmap.getPixel(x, y)
            if (AndroidColor.alpha(c) < 180) continue
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(c, hsv)
            if (hsv[1] < 0.22f || hsv[2] < 0.18f || hsv[2] > 0.94f) continue
            val r = (AndroidColor.red(c) / 32) * 32 + 16
            val g = (AndroidColor.green(c) / 32) * 32 + 16
            val b = (AndroidColor.blue(c) / 32) * 32 + 16
            val key = AndroidColor.rgb(r.coerceAtMost(255), g.coerceAtMost(255), b.coerceAtMost(255))
            bins[key] = (bins[key] ?: 0) + 1
        }
        bitmap.recycle()
        return bins.maxByOrNull { it.value }?.key ?: 0xFFE96A25.toInt()
    }
}

@Composable
fun UserPhoto(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = UserProfile.photoVersion
    val image = remember(version) {
        val file = UserProfile.photoFile(context)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
    }
    if (image != null) Image(bitmap = image, contentDescription = "User photo", modifier = modifier, contentScale = ContentScale.Crop)
}

@Composable
fun CreateUserScreen(onCreated: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photo = it }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Create User", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { picker.launch("image/*") }) { Text(if (photo == null) "Choose User Photo" else "Change Photo") }
        photo?.let { uri ->
            val preview = remember(uri) { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }
            if (preview != null) Image(preview, "Selected photo", Modifier.size(110.dp), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("User Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Button(onClick = { UserProfile.save(context, name, photo); onCreated() }, enabled = name.isNotBlank() && photo != null, modifier = Modifier.fillMaxWidth()) {
            Text("Create User")
        }
    }
}

/** Editable profile with immediate preview and safe photo persistence. */
@Composable
fun CustomizeProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(UserProfile.name) }
    var selectedPhoto by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedPhoto = uri
            error = null
        }
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Customize Profile", style = MaterialTheme.typography.titleLarge)
        }
        val preview = remember(selectedPhoto) {
            selectedPhoto?.let { uri ->
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } catch (_: Exception) { null }
            }
        }
        if (preview != null) {
            Image(bitmap = preview, contentDescription = "Selected profile picture",
                modifier = Modifier.size(112.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            UserPhoto(Modifier.size(112.dp).clip(CircleShape))
        }
        Button(onClick = { picker.launch("image/*") }) { Text("Change Profile Picture") }
        if (selectedPhoto != null) Text("New picture selected — tap Save Profile to apply")
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("User Name") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            if (UserProfile.save(context, name, selectedPhoto)) onBack()
            else error = "Unable to save this photo. Choose another image and try again."
        }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save Profile") }
    }
}
