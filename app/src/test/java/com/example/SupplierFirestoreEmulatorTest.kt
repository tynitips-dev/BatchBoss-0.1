package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SupplierEntity
import com.example.data.remote.BakerySupplierCloud
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Source
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs the production Android write transaction, never against a hosted project. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SupplierFirestoreEmulatorTest {
    private fun <T> await(task: Task<T>): T = Tasks.await(task, 30, TimeUnit.SECONDS)

    @Test fun `stale phone writes preserve intervening website edits and deletions`() {
        val host = System.getenv("FIRESTORE_EMULATOR_HOST")
        require(host == "127.0.0.1:8080") { "This test requires the isolated local Firestore emulator." }
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit(Callable {
                val app = FirebaseApp.initializeApp(
                    ApplicationProvider.getApplicationContext<Context>(),
                    FirebaseOptions.Builder().setProjectId("demo-batchboss-supplier-sync")
                        .setApplicationId("1:123456789:android:emulatoronly")
                        .setApiKey("emulator-only-key").build(),
                    "supplier-race-" + UUID.randomUUID()
                )
                val db = FirebaseFirestore.getInstance(app)
                db.firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
                db.useEmulator("127.0.0.1", 8080)
                try {
                    val suppliers = db.collection("bakeries").document(UUID.randomUUID().toString()).collection("suppliers")
                    val target = suppliers.document("shared-record")
                    await(target.set(mapOf("name" to "Original", "description" to "Before edit", "isFavorite" to false)))
                    // The phone's initial fetch succeeds, then a web edit arrives before upload.
                    val stale = BakerySupplierCloud.decode(target.id, await(target.get(Source.SERVER)).data!!).copy(isFavorite = true)
                    await(target.update(mapOf("name" to "Website name", "description" to "New website description", "phone" to "synthetic-contact")))
                    assertTrue(await(BakerySupplierCloud.writeSupplier(target, stale)))
                    val edited = await(target.get(Source.SERVER))
                    assertEquals("Website name", edited.getString("name"))
                    assertEquals("New website description", edited.getString("description"))
                    assertEquals("synthetic-contact", edited.getString("phone"))
                    assertEquals(true, edited.getBoolean("isFavorite"))

                    // The phone still has a confirmed record when the website deletes it.
                    await(target.delete())
                    assertFalse(await(BakerySupplierCloud.writeSupplier(target, stale.copy(isFavorite = false))))
                    assertFalse(await(target.get(Source.SERVER)).exists())

                    // A genuinely unsent local supplier must still be created successfully.
                    val fresh = suppliers.document("unsent-local")
                    val local = stale.copy(cloudDocumentId = fresh.id, cloudConfirmed = false, name = "Unsent local", about = "Local details")
                    assertTrue(await(BakerySupplierCloud.writeSupplier(fresh, local)))
                    assertEquals("Local details", await(fresh.get(Source.SERVER)).getString("description"))
                    // Retrying an unconfirmed upload also preserves a later website edit.
                    await(fresh.update("description", "Website changed after first upload"))
                    assertTrue(await(BakerySupplierCloud.writeSupplier(fresh, local.copy(isFavorite = false))))
                    assertEquals("Website changed after first upload", await(fresh.get(Source.SERVER)).getString("description"))
                    assertEquals(false, await(fresh.get(Source.SERVER)).getBoolean("isFavorite"))
                    await(fresh.delete())
                } finally {
                    await(db.terminate())
                    app.delete()
                }
            }).get(180, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }
}
