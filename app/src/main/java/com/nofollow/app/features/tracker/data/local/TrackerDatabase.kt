package com.nofollow.app.features.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nofollow.app.features.kepo.data.local.KepoDao
import com.nofollow.app.features.kepo.data.local.KepoHitEntity
import com.nofollow.app.features.kepo.data.local.KepoPersonEntity
import com.nofollow.app.features.kepo.data.local.KepoViewEntity

@Database(
    entities = [
        FollowerEntity::class,
        FollowEventEntity::class,
        SyncStateEntity::class,
        DailyUsageEntity::class,
        FollowerHistoryEntity::class,
        KepoPersonEntity::class,
        KepoHitEntity::class,
        KepoViewEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun followerDao(): FollowerDao
    abstract fun eventDao(): FollowEventDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun usageDao(): DailyUsageDao
    abstract fun historyDao(): FollowerHistoryDao
    abstract fun kepoDao(): KepoDao
}