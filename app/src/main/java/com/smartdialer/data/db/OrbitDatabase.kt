package com.smartdialer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FreeTextRuleEntity::class,
        TypedRuleEntity::class,
        RankingCacheEntity::class,
        LlmExchangeEntity::class,
        WhatsAppEventEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun freeTextRuleDao(): FreeTextRuleDao
    abstract fun typedRuleDao(): TypedRuleDao
    abstract fun rankingCacheDao(): RankingCacheDao
    abstract fun llmExchangeDao(): LlmExchangeDao
    abstract fun whatsAppEventDao(): WhatsAppEventDao

    companion object {
        fun create(context: Context): OrbitDatabase =
            Room.databaseBuilder(context, OrbitDatabase::class.java, "orbit.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /** Adds the optional contact a free-text rule is about. Existing rules stay general (null). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE free_text_rules ADD COLUMN contactKey TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_free_text_rules_contactKey ON free_text_rules (contactKey)")
            }
        }

        /**
         * Stage 4: how each free-text rule was classified (explanation, and the proposal for a rule that needs
         * review), the cached ranking and the last LLM exchange. Existing rules keep their status and get no
         * explanation; the unclassified ones are classified the next time the AI is on.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE free_text_rules ADD COLUMN explanation TEXT")
                db.execSQL("ALTER TABLE free_text_rules ADD COLUMN proposedType TEXT")
                db.execSQL("ALTER TABLE free_text_rules ADD COLUMN proposedSlot INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ranking_cache (contactKey TEXT NOT NULL, rank INTEGER NOT NULL, " +
                        "score INTEGER NOT NULL, tier TEXT NOT NULL, reason TEXT NOT NULL, source TEXT NOT NULL, " +
                        "generatedAt INTEGER NOT NULL, inputsHash TEXT NOT NULL, promptVersion TEXT NOT NULL, " +
                        "model TEXT NOT NULL, PRIMARY KEY(contactKey))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS llm_exchanges (purpose TEXT NOT NULL, at INTEGER NOT NULL, " +
                        "outcome TEXT NOT NULL, request TEXT, response TEXT, errors TEXT NOT NULL, PRIMARY KEY(purpose))",
                )
            }
        }

        /** WhatsApp signals: sender, time and direction only. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS whatsapp_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "contactKey TEXT NOT NULL, at INTEGER NOT NULL, outgoing INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_whatsapp_events_at ON whatsapp_events (at)")
            }
        }
    }
}
