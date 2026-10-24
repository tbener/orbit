package com.smartdialer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FreeTextRuleEntity::class, TypedRuleEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun freeTextRuleDao(): FreeTextRuleDao
    abstract fun typedRuleDao(): TypedRuleDao

    companion object {
        fun create(context: Context): OrbitDatabase =
            Room.databaseBuilder(context, OrbitDatabase::class.java, "orbit.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Adds the optional contact a free-text rule is about. Existing rules stay general (null). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE free_text_rules ADD COLUMN contactKey TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_free_text_rules_contactKey ON free_text_rules (contactKey)")
            }
        }
    }
}
