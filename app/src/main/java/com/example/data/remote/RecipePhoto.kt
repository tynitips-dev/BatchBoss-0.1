package com.example.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.BatchBossApplication
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Match the website's bounded JPEG data URL; content/file URIs cannot travel between devices. */
object RecipePhoto {
    suspend fun portable(value: String): String = withContext(Dispatchers.IO) {
        if (value.isBlank() || value.startsWith("https://") || value.startsWith("data:image/")) return@withContext value
        val uri = Uri.parse(value)
        require(uri.scheme == "content" || uri.scheme == "file") { "Unsupported recipe photo" }
        val resolver = BatchBossApplication.instance.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Cannot read recipe photo" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1800) inSampleSize *= 2
        }
        val source = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Cannot open recipe photo")
        val scale = minOf(1.0, 900.0 / maxOf(source.width, source.height))
        val resized = Bitmap.createScaledBitmap(source, maxOf(1, (source.width * scale).toInt()), maxOf(1, (source.height * scale).toInt()), true)
        try {
            for (quality in listOf(75, 45, 25)) {
                val bytes = ByteArrayOutputStream().use { output ->
                    resized.compress(Bitmap.CompressFormat.JPEG, quality, output)
                    output.toByteArray()
                }
                val data = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                if (data.length <= 450000) return@withContext data
            }
            error("Recipe photo is too large; choose a smaller image")
        } finally {
            if (resized !== source) resized.recycle()
            source.recycle()
        }
    }

    /** Coil accepts bytes directly, including photos saved by the website. */
    fun displayModel(value: String): Any =
        if (value.startsWith("data:image/") && value.contains(";base64,")) {
            runCatching { Base64.decode(value.substringAfter(";base64,"), Base64.DEFAULT) }.getOrDefault(byteArrayOf())
        } else value
}
