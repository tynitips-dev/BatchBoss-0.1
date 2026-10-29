package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class Version22MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sampleTables = listOf("suppliers", "customers", "recipes", "invoices", "packaging_items")

    @Test fun `installed version22 schema migrates without changing business records`() = migrate("canonical14")
    @Test fun `canonical schema13 retains packaging sync and business records`() = migrate("canonical13")
    @Test fun `alternate supplier schema13 retains queued and confirmed supplier fields`() = migrate("supplierDevelopment13")
    @Test fun `schema12 completes all intermediate migrations`() = migrate("canonical12")
    @Test fun `schema11 preserves invoices when packaging storage is introduced`() = migrate("canonical11")

    private fun fixture(name: String): JSONObject {
        val text = javaClass.classLoader!!.getResourceAsStream("v22-migration-fixtures.json")!!.bufferedReader().use { it.readText() }
        val items = JSONObject(text).getJSONArray("fixtures")
        return (0 until items.length()).map { items.getJSONObject(it) }.first { it.getString("name") == name }
    }

    private fun helper(name: String, version: Int, create: (SupportSQLiteDatabase) -> Unit): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = create(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected fixture upgrade")
            }).build())

    private fun exists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }

    private fun seed(db: SupportSQLiteDatabase, table: String) {
        val names = mutableListOf<String>()
        val values = mutableListOf<Any>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                val column = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                names += column
                values += when {
                    column == "id" -> 7L
                    column == "userId" -> 42L
                    column == "bakeryId" -> "test-bakery"
                    column == "cloudConfirmed" || column == "pendingSync" -> 1L
                    type.contains("INT", true) -> 123L
                    type.contains("REAL", true) -> 12.5
                    else -> "preserve-$table-$column"
                }
            }
        }
        db.execSQL("INSERT INTO `$table` (${names.joinToString(",") { "`$it`" }}) VALUES (${names.joinToString(",") { "?" }})", values.toTypedArray())
    }

    private fun row(db: SupportSQLiteDatabase, table: String): Map<String, String?> =
        db.query("SELECT * FROM `$table` WHERE id=7").use { cursor ->
            assertTrue("Record missing from $table", cursor.moveToFirst())
            cursor.columnNames.mapIndexed { index, name -> name to if (cursor.isNull(index)) null else cursor.getString(index) }.toMap()
        }

    private fun migrate(kind: String) {
        val fixture = fixture(kind)
        val name = "v22-migration-${UUID.randomUUID()}.db"
        val before = mutableMapOf<String, Map<String, String?>>()
        val original = helper(name, fixture.getInt("version")) { db ->
            val sql = fixture.getJSONArray("sql")
            for (i in 0 until sql.length()) db.execSQL(sql.getString(i))
            for (table in sampleTables) if (exists(db, table)) { seed(db, table); before[table] = row(db, table) }
        }
        try {
            original.writableDatabase
            original.close()
            val room = AppDatabase.builder(context, name).allowMainThreadQueries().setJournalMode(RoomDatabaseJournalMode).build()
            try {
                val migrated = room.openHelper.writableDatabase // Room validates every table and index here.
                assertEquals(15, migrated.version)
                for ((table, oldRow) in before) {
                    val current = row(migrated, table)
                    for ((column, value) in oldRow) assertEquals("Changed $table.$column for $kind", value, current[column])
                }
                val supplier = row(migrated, "suppliers")
                assertTrue(supplier.keys.containsAll(listOf("bakeryId", "cloudDocumentId", "publicListingId", "city", "province", "cloudConfirmed", "pendingSync")))
                if (kind != "supplierDevelopment13") {
                    assertEquals("", supplier["bakeryId"])
                    assertEquals("", supplier["cloudDocumentId"])
                    assertEquals("0", supplier["cloudConfirmed"])
                    assertEquals("0", supplier["pendingSync"])
                }
            } finally { room.close() }
        } finally { original.close(); context.deleteDatabase(name) }
    }

    @Test fun `unsupported database version fails without clearing its records`() {
        val name = "unsupported-migration-${UUID.randomUUID()}.db"
        val fixture = fixture("canonical14")
        val original = helper(name, 9) { db ->
            val sql = fixture.getJSONArray("sql")
            for (i in 0 until sql.length()) db.execSQL(sql.getString(i))
            seed(db, "suppliers")
        }
        try {
            original.writableDatabase
            original.close()
            val room = AppDatabase.builder(context, name).allowMainThreadQueries().build()
            try { assertThrows(IllegalStateException::class.java) { room.openHelper.writableDatabase } }
            finally { room.close() }
            val unchanged = helper(name, 9) { error("Existing fixture must not be replaced") }
            try { assertEquals("preserve-suppliers-name", row(unchanged.writableDatabase, "suppliers")["name"]) }
            finally { unchanged.close() }
        } finally { original.close(); context.deleteDatabase(name) }
    }

    private val RoomDatabaseJournalMode = androidx.room.RoomDatabase.JournalMode.TRUNCATE
}
