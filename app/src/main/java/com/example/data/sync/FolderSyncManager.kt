package com.example.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.SyncFolderEntity
import com.example.data.transfer.TransferManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

data class FolderSyncRunSummary(
    val foldersChecked: Int,
    val newFilesQueued: Int,
    val skippedUpToDate: Int
)

/**
 * Manages Automated Folder Sync & Camera Backup from local SAF DocumentTree directories
 * into target TeleVault folders with optional client-side AES-256 encryption.
 */
class FolderSyncManager private constructor(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val transferManager = TransferManager.getInstance(context)

    private val _isSyncingFolders = MutableStateFlow(false)
    val isSyncingFolders: StateFlow<Boolean> = _isSyncingFolders.asStateFlow()

    companion object {
        private const val TAG = "FolderSyncManager"

        @Volatile
        private var INSTANCE: FolderSyncManager? = null

        fun getInstance(context: Context): FolderSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FolderSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Registers a local SAF DocumentTree URI for automated or one-tap delta synchronization.
     */
    suspend fun registerSyncFolder(
        treeUri: Uri,
        targetVaultFolderId: String?,
        targetVaultFolderName: String,
        autoSyncEnabled: Boolean = true,
        encryptOnSync: Boolean = true,
        defaultTags: String = "Synced"
    ): SyncFolderEntity = withContext(Dispatchers.IO) {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                takeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.w(TAG, "Non-fatal: could not persist URI permission: ${e.message}")
        }

        val displayName = resolveTreeDisplayName(treeUri)

        val entity = SyncFolderEntity(
            id = UUID.randomUUID().toString(),
            localTreeUri = treeUri.toString(),
            localDisplayName = displayName,
            targetVaultFolderId = targetVaultFolderId,
            targetVaultFolderName = targetVaultFolderName,
            lastSyncedAt = 0L,
            autoSyncEnabled = autoSyncEnabled,
            encryptOnSync = encryptOnSync,
            defaultTags = defaultTags,
            syncedFilesCount = 0,
            syncedSignaturesJson = "{}"
        )
        database.syncFolderDao().insert(entity)
        entity
    }

    /**
     * Executes delta synchronization for a single registered [SyncFolderEntity] or all auto-sync folders.
     */
    suspend fun syncFolderNow(syncFolderId: String): Result<FolderSyncRunSummary> = withContext(Dispatchers.IO) {
        val folder = database.syncFolderDao().getById(syncFolderId)
            ?: return@withContext Result.failure(IllegalArgumentException("Sync folder not found"))
        runDeltaSyncForFolders(listOf(folder))
    }

    suspend fun syncAllEnabledFolders(onlyAutoSync: Boolean = false): Result<FolderSyncRunSummary> = withContext(Dispatchers.IO) {
        val all = database.syncFolderDao().getAll()
        val targets = if (onlyAutoSync) all.filter { it.autoSyncEnabled } else all
        if (targets.isEmpty()) {
            return@withContext Result.success(FolderSyncRunSummary(0, 0, 0))
        }
        runDeltaSyncForFolders(targets)
    }

    private suspend fun runDeltaSyncForFolders(folders: List<SyncFolderEntity>): Result<FolderSyncRunSummary> {
        _isSyncingFolders.value = true
        return try {
            val existingVaultFiles = database.fileDao().getAll()
            val existingKeys = existingVaultFiles.map { "${it.name}|${it.size}|${it.folderId ?: "root"}" }.toMutableSet()

            var totalQueued = 0
            var totalSkipped = 0

            for (syncFolder in folders) {
                val treeUri = Uri.parse(syncFolder.localTreeUri)
                val sigJson = try {
                    JSONObject(syncFolder.syncedSignaturesJson)
                } catch (_: Exception) {
                    JSONObject()
                }

                val urisToQueue = mutableListOf<Uri>()
                val childDocs = listDirectChildFiles(treeUri)
                for ((docUri, name, size) in childDocs) {
                    if (size <= 0L) continue
                    val sigKey = "$name|$size"
                    val vaultKey = "$name|$size|${syncFolder.targetVaultFolderId ?: "root"}"

                    if (sigJson.has(sigKey) || existingKeys.contains(vaultKey)) {
                        totalSkipped++
                        sigJson.put(sigKey, true)
                    } else {
                        urisToQueue.add(docUri)
                        sigJson.put(sigKey, true)
                        existingKeys.add(vaultKey)
                    }
                }

                if (urisToQueue.isNotEmpty()) {
                    transferManager.enqueueBatchUpload(
                        uris = urisToQueue,
                        folderId = syncFolder.targetVaultFolderId,
                        encryptBeforeUpload = syncFolder.encryptOnSync,
                        tags = syncFolder.defaultTags.ifBlank { "Synced" }
                    )
                    totalQueued += urisToQueue.size
                }

                val updated = syncFolder.copy(
                    lastSyncedAt = System.currentTimeMillis(),
                    syncedFilesCount = sigJson.length(),
                    syncedSignaturesJson = sigJson.toString()
                )
                database.syncFolderDao().update(updated)
            }

            Result.success(
                FolderSyncRunSummary(
                    foldersChecked = folders.size,
                    newFilesQueued = totalQueued,
                    skippedUpToDate = totalSkipped
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Folder delta sync failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            _isSyncingFolders.value = false
        }
    }

    private fun resolveTreeDisplayName(treeUri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            var resolved: String? = null
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    resolved = cursor.getString(0)
                }
            }
            resolved?.takeIf { it.isNotBlank() }
                ?: treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
                ?: "Local Folder"
        } catch (_: Exception) {
            treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: "Local Folder"
        }
    }

    private fun listDirectChildFiles(treeUri: Uri): List<Triple<Uri, String, Long>> {
        val result = mutableListOf<Triple<Uri, String, Long>>()
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val docId = if (idCol != -1) cursor.getString(idCol) else null
                    val name = if (nameCol != -1) cursor.getString(nameCol) else null
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) else ""

                    if (docId != null && name != null && mime != DocumentsContract.Document.MIME_TYPE_DIR) {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        result.add(Triple(fileUri, name, size))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list child files in SAF tree: ${e.message}")
        }
        return result
    }
}
