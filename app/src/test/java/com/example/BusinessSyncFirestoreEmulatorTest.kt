package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PendingCloudChange
import com.example.data.remote.FirebaseService
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Uses only the existing synthetic supplier test path, which is allowed by local-only test rules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BusinessSyncFirestoreEmulatorTest {
    @Test fun `production transaction retries safely and refuses stale edits or deleted records`() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR_HOST") == "127.0.0.1:8080")
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit(Callable {
                val app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext<Context>(),
                    FirebaseOptions.Builder().setProjectId("demo-batchboss-supplier-sync")
                        .setApplicationId("1:123:android:localfixture").setApiKey("emulator-only").build(),
                    "business-fixture-" + UUID.randomUUID())
                val db = FirebaseFirestore.getInstance(app)
                db.firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
                db.useEmulator("127.0.0.1", 8080)
                try {
                    val ref = db.collection("bakeries").document(UUID.randomUUID().toString()).collection("suppliers").document("transaction-fixture")
                    val first = PendingCloudChange("fixture", "suppliers/transaction-fixture", "suppliers", 1, "first", expectedUpdatedAt = null)
                    val data = mapOf("name" to "First", "total" to 250.0, "vat" to 0.0,
                        "phone" to "synthetic-contact", "items" to listOf(mapOf("name" to "Cake", "productId" to "website-uuid", "quantity" to 2, "unitPrice" to 125, "lineTotal" to 250)),
                        "updatedAt" to FieldValue.serverTimestamp())
                    runBlocking { FirebaseService.writeBusinessDocument(ref, data, first) }
                    val confirmed = Tasks.await(ref.get(Source.SERVER), 30, TimeUnit.SECONDS)
                    val timestamp = confirmed.getTimestamp("updatedAt")!!.toDate().time
                    val invoice = FirebaseService.documentToInvoice(confirmed, "fixture")!!
                    val quote = FirebaseService.documentToQuote(confirmed, "fixture")!!
                    assertEquals(250.0, invoice.amount, 0.0)
                    assertEquals(0.0, invoice.taxRatePercent, 0.0)
                    assertEquals("Cake", invoice.items.single().itemName)
                    assertEquals("synthetic-contact", invoice.clientPhone)
                    assertEquals(250.0, quote.quotedPrice, 0.0)
                    assertEquals(0.0, quote.taxRatePercent, 0.0)
                    assertEquals(2.0, quote.items.single().quantity, 0.0)
                    runBlocking { FirebaseService.writeBusinessDocument(ref, data, first) }
                    assertEquals(confirmed.getTimestamp("updatedAt"), Tasks.await(ref.get(Source.SERVER), 30, TimeUnit.SECONDS).getTimestamp("updatedAt"))

                    // An edit made while the first upload was in flight recognises its own ancestor.
                    val next = first.copy(revision = "second", ancestors = "first")
                    runBlocking { FirebaseService.writeBusinessDocument(ref, data + ("name" to "Second"), next) }
                    assertEquals("Second", Tasks.await(ref.get(Source.SERVER), 30, TimeUnit.SECONDS).getString("name"))

                    Tasks.await(ref.update(mapOf("name" to "Website edit", "_clientMutationId" to "website", "updatedAt" to com.google.firebase.Timestamp(9999999999L, 0))), 30, TimeUnit.SECONDS)
                    val stale = first.copy(revision = "third", expectedUpdatedAt = timestamp)
                    try {
                        runBlocking { FirebaseService.writeBusinessDocument(ref, data + ("name" to "Must not overwrite"), stale) }
                        fail("Stale edit must fail")
                    } catch (_: Exception) { }
                    assertEquals("Website edit", Tasks.await(ref.get(Source.SERVER), 30, TimeUnit.SECONDS).getString("name"))

                    Tasks.await(ref.delete(), 30, TimeUnit.SECONDS)
                    try {
                        runBlocking { FirebaseService.writeBusinessDocument(ref, data, stale) }
                        fail("Deleted remote record must not be resurrected")
                    } catch (_: Exception) { }
                    assertFalse(Tasks.await(ref.get(Source.SERVER), 30, TimeUnit.SECONDS).exists())
                } finally {
                    Tasks.await(db.terminate(), 30, TimeUnit.SECONDS)
                    app.delete()
                }
            }).get(150, TimeUnit.SECONDS)
        } finally { executor.shutdownNow() }
    }
}
