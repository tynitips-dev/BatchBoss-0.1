package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.SupplierEntity
import com.example.data.remote.BakerySupplierCloud
import com.example.data.remote.PublicSupplierDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SupplierCompatibilityTest {
    private val listingId = "12345678-1234-4234-8234-123456789abc"
    @Test fun `saved suppliers are isolated between local accounts`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        fun supplier(owner: Long, name: String) = SupplierEntity(userId=owner, name=name, categories="Packaging", rating=0.0, reviewCount=0, openingHours="", phone="", email="", website="", about="")
        try {
            db.supplierDao().insertSupplier(supplier(101,"First bakery"))
            db.supplierDao().insertSupplier(supplier(202,"Second bakery"))
            db.supplierDao().insertSupplier(supplier(0,"Unassigned legacy record"))
            assertEquals(listOf("First bakery"),db.supplierDao().getSuppliersByUser(101).first().map { it.name })
            assertEquals(listOf("Second bakery"),db.supplierDao().getSuppliersByUser(202).first().map { it.name })
            assertEquals(3,db.supplierDao().getAllSuppliers().first().size)
        } finally { db.close() }
    }
    @Test fun `migration retains existing supplier rows and does not guess an owner`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
            .callback(object:SupportSQLiteOpenHelper.Callback(12) {
                override fun onCreate(db:SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE suppliers (id INTEGER PRIMARY KEY, userId INTEGER NOT NULL, name TEXT NOT NULL)")
                    db.execSQL("INSERT INTO suppliers VALUES (7,0,'Existing supplier')")
                }
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int) = Unit
            }).build())
        try {
            val db=helper.writableDatabase
            AppDatabase.MIGRATION_12_13.migrate(db)
            db.query("SELECT id,userId,name,bakeryId,cloudDocumentId,cloudConfirmed,pendingSync FROM suppliers").use { cursor ->
                assertTrue(cursor.moveToFirst());assertEquals(7,cursor.getInt(0));assertEquals(0,cursor.getInt(1))
                assertEquals("Existing supplier",cursor.getString(2));assertEquals("",cursor.getString(3));assertEquals("",cursor.getString(4))
                assertEquals(0,cursor.getInt(5));assertEquals(0,cursor.getInt(6));assertFalse(cursor.moveToNext())
            }
        } finally { helper.close() }
    }
    @Test fun `directory keys match the baker website and reject private account IDs`() {
        assertEquals("directory_"+listingId,BakerySupplierCloud.directoryDocumentId(listingId))
        assertThrows(IllegalArgumentException::class.java) { BakerySupplierCloud.directoryDocumentId("private-account-id") }
    }
    @Test fun `web description edits take precedence over older about fields`() {
        val decoded=BakerySupplierCloud.decode("web-document",mapOf("name" to "Example","description" to "Edited on website","about" to "Old Android description"))
        assertEquals("Edited on website",decoded.about);assertEquals(0.0,decoded.rating,0.0)
        assertEquals(0,decoded.reviewCount);assertEquals("",decoded.openingHours);assertTrue(decoded.cloudConfirmed)
    }
    @Test fun `public directory parses empty results and rejects a private cursor`() {
        assertTrue(PublicSupplierDirectory.parse("""{"result":{"suppliers":[],"nextCursor":null}}""").suppliers.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { PublicSupplierDirectory.parse("""{"result":{"suppliers":[],"nextCursor":"private-account-id"}}""") }
    }
}
