package com.example.homesphere.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ActivityEventEntity::class,
        AutomationRuleEntity::class,
        WaterHistoryEntity::class,
        ConnectedDeviceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class HomeSphereDatabase : RoomDatabase() {
    abstract fun activityEventDao(): ActivityEventDao
    abstract fun automationRuleDao(): AutomationRuleDao
    abstract fun waterHistoryDao(): WaterHistoryDao
    abstract fun connectedDeviceDao(): ConnectedDeviceDao

    companion object {
        @Volatile
        private var INSTANCE: HomeSphereDatabase? = null

        fun getInstance(context: Context): HomeSphereDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HomeSphereDatabase::class.java,
                    "homesphere_local.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
