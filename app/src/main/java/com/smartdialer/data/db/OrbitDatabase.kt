package com.smartdialer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FreeTextRuleEntity::class, TypedRuleEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun freeTextRuleDao(): FreeTextRuleDao
    abstract fun typedRuleDao(): TypedRuleDao

    companion object {
        fun create(context: Context): OrbitDatabase =
            Room.databaseBuilder(context, OrbitDatabase::class.java, "orbit.db").build()
    }
}
