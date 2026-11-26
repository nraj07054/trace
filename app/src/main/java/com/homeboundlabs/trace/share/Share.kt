package com.homeboundlabs.trace.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Hands a card image (and a message) to WhatsApp, or to the share sheet when WhatsApp isn't there. */
object Share {
    private val WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")

    /** Writes [bmp] as a PNG in the app's private cache (the only folder the file provider exposes). */
    suspend fun save(context: Context, bmp: Bitmap, name: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "cards").apply { mkdirs() }
        // Old cards are of no use once shared; keep the folder from growing.
        dir.listFiles()?.forEach { it.delete() }
        File(dir, "$name.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    fun send(context: Context, file: File, text: String, whatsApp: Boolean) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        fun intent(pkg: String?) = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            pkg?.let(::setPackage)
        }
        if (whatsApp) {
            for (pkg in WHATSAPP) {
                try {
                    context.startActivity(intent(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
                } catch (_: ActivityNotFoundException) {
                    // Not installed; try the next one, then the share sheet.
                }
            }
        }
        context.startActivity(Intent.createChooser(intent(null), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
