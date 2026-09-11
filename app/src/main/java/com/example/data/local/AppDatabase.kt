package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ChannelDao
import com.example.data.local.dao.ChunkDao
import com.example.data.local.dao.FileDao
import com.example.data.local.dao.FolderDao
import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.ChunkEntity
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FolderEntity

@Database(
    entities = [
        FileEntity::class,
        ChunkEntity::class,
        FolderEntity::class,
        ChannelEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun fileDao(): FileDao
    abstract fun chunkDao(): ChunkDao
    abstract fun folderDao(): FolderDao
    abstract fun channelDao(): ChannelDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE files ADD COLUMN localUri TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE files ADD COLUMN channelId TEXT")
                db.execSQL("ALTER TABLE chunks ADD COLUMN channelId TEXT")
            }
        }

        class Migration3To4(private val context: Context) : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Ensure folders table and its index exist for hierarchical organization
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `folders` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `parentFolderId` TEXT,
                        `createdDate` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parentFolderId` ON `folders` (`parentFolderId`)")

                // 2. Create channels table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `channels` (
                        `channelId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `addedDate` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        PRIMARY KEY(`channelId`)
                    )
                    """.trimIndent()
                )

                // 3. Read existing chatId from CredentialStore (EncryptedCredentialsManager)
                val creds = EncryptedCredentialsManager(context)
                val existingChatId = creds.getChatId()?.trim() ?: ""

                // 3. Create one ChannelEntity row from CredentialStore's existing chat ID
                if (existingChatId.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val stmt = db.compileStatement(
                        "INSERT OR IGNORE INTO `channels` (`channelId`, `displayName`, `addedDate`, `isActive`) VALUES (?, ?, ?, 1)"
                    )
                    stmt.bindString(1, existingChatId)
                    stmt.bindString(2, "My Vault")
                    stmt.bindLong(3, now)
                    stmt.executeInsert()
                }

                // 4. Migrate chunks table to guarantee channelId column exists, is NOT NULL, and backfilled
                val cursor = db.query("PRAGMA table_info(chunks)")
                var hasChannelId = false
                val nameColIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameColIdx != -1 && cursor.getString(nameColIdx) == "channelId") {
                        hasChannelId = true
                        break
                    }
                }
                cursor.close()

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chunks_new` (
                        `fileId` TEXT NOT NULL,
                        `chunkIndex` INTEGER NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `telegramMessageId` INTEGER,
                        `telegramFileId` TEXT,
                        `checksum` TEXT NOT NULL,
                        `size` INTEGER NOT NULL,
                        `isUploaded` INTEGER NOT NULL,
                        `isDownloaded` INTEGER NOT NULL,
                        PRIMARY KEY(`fileId`, `chunkIndex`),
                        FOREIGN KEY(`fileId`) REFERENCES `files`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                if (hasChannelId) {
                    db.execSQL(
                        """
                        INSERT INTO `chunks_new` (
                            `fileId`, `chunkIndex`, `channelId`, `telegramMessageId`, `telegramFileId`, `checksum`, `size`, `isUploaded`, `isDownloaded`
                        )
                        SELECT 
                            `fileId`, `chunkIndex`, COALESCE(NULLIF(`channelId`, ''), '$existingChatId'), `telegramMessageId`, `telegramFileId`, `checksum`, `size`, `isUploaded`, `isDownloaded`
                        FROM `chunks`
                        """.trimIndent()
                    )
                } else {
                    db.execSQL(
                        """
                        INSERT INTO `chunks_new` (
                            `fileId`, `chunkIndex`, `channelId`, `telegramMessageId`, `telegramFileId`, `checksum`, `size`, `isUploaded`, `isDownloaded`
                        )
                        SELECT 
                            `fileId`, `chunkIndex`, '$existingChatId', `telegramMessageId`, `telegramFileId`, `checksum`, `size`, `isUploaded`, `isDownloaded`
                        FROM `chunks`
                        """.trimIndent()
                    )
                }

                db.execSQL("DROP TABLE `chunks`")
                db.execSQL("ALTER TABLE `chunks_new` RENAME TO `chunks`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chunks_fileId` ON `chunks` (`fileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chunks_telegramMessageId` ON `chunks` (`telegramMessageId`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "televault_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, Migration3To4(appContext))
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            try {
                                val creds = EncryptedCredentialsManager(appContext)
                                val chatId = creds.getChatId()?.trim()
                                if (!chatId.isNullOrEmpty()) {
                                    val now = System.currentTimeMillis()
                                    db.execSQL(
                                        "INSERT OR IGNORE INTO `channels` (`channelId`, `displayName`, `addedDate`, `isActive`) VALUES (?, 'My Vault', ?, 1)",
                                        arrayOf(chatId, now)
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("AppDatabase", "Error ensuring default channel exists", e)
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
