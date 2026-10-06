package com.vpnblockads.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [QueryLogEntity::class, CustomRuleEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queryLogDao(): QueryLogDao
    abstract fun customRuleDao(): CustomRuleDao
}
