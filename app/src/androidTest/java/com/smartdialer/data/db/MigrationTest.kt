package com.smartdialer.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.TypedRuleParams
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OrbitDatabase::class.java)

    @Test
    fun migrate1To2_addsContactKeyAndKeepsEveryRuleGeneral() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO free_text_rules (id, text, createdAt, status, typedRuleId) VALUES (1, 'call mom often', 100, 'UNCLASSIFIED', NULL)")
            execSQL("INSERT INTO free_text_rules (id, text, createdAt, status, typedRuleId) VALUES (2, 'never show my boss', 200, 'CONVERTED_TO_TYPED', 7)")
            execSQL(
                "INSERT INTO typed_rules (id, createdAt, type, paramsJson, sourceFreeTextRuleId) VALUES " +
                    "(7, 250, 'NEVER_SHOW', '{\"kind\":\"NEVER_SHOW\",\"contact\":{\"lookupKey\":\"k-boss\",\"displayName\":\"Boss\"}}', 2)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, OrbitDatabase.MIGRATION_1_2)

        db.query("SELECT id, text, status, typedRuleId, contactKey FROM free_text_rules ORDER BY id").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("call mom often", c.getString(c.getColumnIndexOrThrow("text")))
            assertTrue("existing rules stay general", c.isNull(c.getColumnIndexOrThrow("contactKey")))
            assertTrue(c.moveToNext())
            assertEquals("CONVERTED_TO_TYPED", c.getString(c.getColumnIndexOrThrow("status")))
            assertEquals(7L, c.getLong(c.getColumnIndexOrThrow("typedRuleId")))
            assertTrue(c.isNull(c.getColumnIndexOrThrow("contactKey")))
            assertFalse(c.moveToNext())
        }
        db.query("SELECT COUNT(*) FROM typed_rules").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
    }

    @Test
    fun migrate1To2_createsTheContactKeyIndex() {
        helper.createDatabase(dbName, 1).close()
        val db = helper.runMigrationsAndValidate(dbName, 2, true, OrbitDatabase.MIGRATION_1_2)
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'free_text_rules'").use { c ->
            val names = buildList { while (c.moveToNext()) add(c.getString(0)) }
            assertTrue(names.toString(), "index_free_text_rules_contactKey" in names)
        }
    }

    @Test
    fun migratedDatabaseWorksThroughRoomAndAcceptsContactRules() = runBlocking {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO free_text_rules (id, text, createdAt, status, typedRuleId) VALUES (1, 'old rule', 100, 'UNCLASSIFIED', NULL)")
            close()
        }
        helper.closeWhenFinished(helper.runMigrationsAndValidate(dbName, 2, true, OrbitDatabase.MIGRATION_1_2))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, OrbitDatabase::class.java, dbName)
            .addMigrations(OrbitDatabase.MIGRATION_1_2, OrbitDatabase.MIGRATION_2_3)
            .build()
        try {
            val repo = RoomRulesRepository(db)
            val old = repo.freeTextRules.first().single()
            assertEquals("old rule", old.text)
            assertNull(old.contactKey)
            assertEquals(FreeTextStatus.UNCLASSIFIED, old.status)

            repo.addFreeTextRule("about mom", contactKey = "k-mom")
            repo.addTypedRule(TypedRuleParams.NeverShow(com.smartdialer.rules.ContactRef("k-boss", "Boss")))
            val rules = repo.freeTextRules.first()
            assertEquals(listOf("k-mom", null), rules.map { it.contactKey })
            assertEquals(1, repo.typedRules.first().size)
        } finally {
            db.close()
        }
    }
}
