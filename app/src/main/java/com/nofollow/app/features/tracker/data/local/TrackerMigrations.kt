package com.nofollow.app.features.tracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Migrasi eksplisit supaya riwayat follower tidak hilang saat skema berubah. */
object TrackerMigrations {

    /** followers.picUrl: URL foto dari sweep terakhir (nullable, baris lama null sampai sweep berikutnya mengisinya). */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE followers ADD COLUMN picUrl TEXT")
        }
    }

    /** Tabel follower_history untuk grafik pertumbuhan. Mulai kosong; terisi tiap sweep selesai. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `follower_history` (" +
                        "`ownerId` TEXT NOT NULL, `day` INTEGER NOT NULL, `followerCount` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ownerId`, `day`))",
            )
        }
    }

    /** Fitur Kepo: kepo_people (akun yang like/balas story) dan kepo_hits (catatan interaksi yang sudah dihitung). Mulai kosong. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `kepo_people` (" +
                        "`ownerId` TEXT NOT NULL, `pk` TEXT NOT NULL, `username` TEXT NOT NULL, `fullName` TEXT NOT NULL, " +
                        "`picUrl` TEXT, `followedBy` INTEGER NOT NULL, `firstAt` INTEGER NOT NULL, `lastAt` INTEGER NOT NULL, " +
                        "`likeCount` INTEGER NOT NULL, `replyCount` INTEGER NOT NULL, `seen` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ownerId`, `pk`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `kepo_hits` (" +
                        "`ownerId` TEXT NOT NULL, `storyPk` TEXT NOT NULL, `pk` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "PRIMARY KEY(`ownerId`, `storyPk`, `pk`, `kind`))",
            )
        }
    }

    /**
     * Kepo v2: semua penonton story dicatat (bukan hanya yang like). kepo_views = catatan nonton per story,
     * kepo_people.topRank = urutan penonton teratas polling terakhir. Data lama tetap; mulai terisi dari polling berikutnya.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `kepo_people` ADD COLUMN `topRank` INTEGER")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `kepo_views` (" +
                        "`ownerId` TEXT NOT NULL, `storyPk` TEXT NOT NULL, `pk` TEXT NOT NULL, `seenAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`ownerId`, `storyPk`, `pk`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_kepo_views_ownerId_pk` ON `kepo_views` (`ownerId`, `pk`)")
        }
    }
}