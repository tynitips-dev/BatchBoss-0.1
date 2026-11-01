package com.example

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.RecipePhoto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = BatchBossApplication::class)
class RecipePhotoCompatibilityTest {
    @Test fun `web photo data is decoded for Coil and portable photos survive unchanged`() = runBlocking {
        val data = "data:image/jpeg;base64,AQID"
        assertArrayEquals(byteArrayOf(1, 2, 3), RecipePhoto.displayModel(data) as ByteArray)
        assertEquals(data, RecipePhoto.portable(data))
        assertEquals("", RecipePhoto.portable(""))
        assertEquals("https://example.test/cake.jpg", RecipePhoto.portable("https://example.test/cake.jpg"))
    }
    @Test fun `phone-local photo becomes bounded portable JPEG`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<BatchBossApplication>()
        val file = File(app.cacheDir, "recipe-test.png")
        val bitmap = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val data = RecipePhoto.portable(file.toURI().toString())
            assertTrue(data.startsWith("data:image/jpeg;base64,"))
            assertTrue(data.length <= 450000)
            val bytes = RecipePhoto.displayModel(data) as ByteArray
            val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertNotNull(decoded)
            assertTrue(maxOf(decoded.width, decoded.height) <= 900)
            decoded.recycle()
        } finally { bitmap.recycle(); file.delete() }
    }
}
