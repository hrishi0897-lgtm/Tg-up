package com.example.data.transfer

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.EncryptedCredentialsManager
import com.example.data.local.entity.StandbyBotEntity
import com.example.data.remote.TelegramRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StandbyRecoveryState(
    val isRecovering: Boolean = false,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val progressFraction: Float = 0f,
    val currentFileName: String = "",
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val isCompleted: Boolean = false,
    val recoveredChunksCount: Int = 0,
    val standbyBotLabel: String = ""
)

/**
 * Manages multi-bot resilience recovery using Telegram server-side copyMessage.
 *
 * This allows a standby bot that was pre-registered as a channel member to recover
 * access to all file chunks without needing to have uploaded them, and without transferring
 * any file bytes through the device connection (0 MB mobile data used).
 */
class StandbyRecoveryManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = AppDatabase.getInstance(context)
    private val credentialsManager = EncryptedCredentialsManager(context)
    private val repository = TelegramRepository.getInstance()

    private val _recoveryState = MutableStateFlow(StandbyRecoveryState())
    val recoveryState: StateFlow<StandbyRecoveryState> = _recoveryState.asStateFlow()

    private var recoveryJob: Job? = null

    companion object {
        @Volatile
        private var INSTANCE: StandbyRecoveryManager? = null

        fun getInstance(context: Context): StandbyRecoveryManager {
            return INSTANCE ?: synchronized(this) {
                val instance = StandbyRecoveryManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Starts the manual recovery process to switch the vault to the specified standby bot.
     * Copies all chunk messages server-side using copyMessage, updates database records,
     * and switches the active bot token in EncryptedCredentialsManager.
     */
    fun startRecovery(standbyBot: StandbyBotEntity) {
        if (_recoveryState.value.isRecovering) {
            Log.w("StandbyRecovery", "Recovery already in progress")
            return
        }

        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            try {
                _recoveryState.value = StandbyRecoveryState(
                    isRecovering = true,
                    statusMessage = "Decrypting standby bot credentials...",
                    standbyBotLabel = standbyBot.label
                )

                val standbyToken = credentialsManager.decryptToken(standbyBot.encryptedToken)
                if (standbyToken.isNullOrBlank()) {
                    val err = "Failed to decrypt standby bot token"
                    _recoveryState.update { it.copy(isRecovering = false, errorMessage = err) }
                    return@launch
                }

                val targetChatId = credentialsManager.getChatId()
                if (targetChatId.isNullOrBlank()) {
                    val err = "Storage chat ID is not configured"
                    _recoveryState.update { it.copy(isRecovering = false, errorMessage = err) }
                    return@launch
                }

                // Step 1: Verify standby bot's membership before attempting copyMessage
                _recoveryState.update { it.copy(statusMessage = "Verifying ${standbyBot.label}'s channel access...") }
                val membershipResult = repository.verifyChatMembership(standbyToken, targetChatId)
                if (membershipResult.isFailure) {
                    val err = "Standby bot cannot access storage chat. Please ensure ${standbyBot.label} has been added as an Administrator in your Telegram channel."
                    Log.e("StandbyRecovery", "Membership verification failed: $err")
                    _recoveryState.update { it.copy(isRecovering = false, errorMessage = err) }
                    return@launch
                }

                // Step 2: Fetch all recoverable chunks from database
                _recoveryState.update { it.copy(statusMessage = "Querying vault chunk records...") }
                val recoverableChunks = database.chunkDao().getAllRecoverableChunks()

                if (recoverableChunks.isEmpty()) {
                    // Empty vault or no chunks uploaded yet: switch token directly
                    Log.i("StandbyRecovery", "No chunks to recover. Switching active token directly.")
                    val oldActiveToken = credentialsManager.getBotToken()
                    credentialsManager.setActiveBotToken(standbyToken)
                    database.standbyBotDao().updateVerification(
                        id = standbyBot.id,
                        isVerified = true,
                        timestamp = System.currentTimeMillis(),
                        channelId = targetChatId
                    )
                    _recoveryState.update {
                        it.copy(
                            isRecovering = false,
                            isCompleted = true,
                            currentChunk = 0,
                            totalChunks = 0,
                            progressFraction = 1f,
                            statusMessage = "Vault switched to ${standbyBot.label}. No chunks required recovery."
                        )
                    }
                    return@launch
                }

                val totalChunks = recoverableChunks.size
                _recoveryState.update {
                    it.copy(
                        totalChunks = totalChunks,
                        statusMessage = "Recovering $totalChunks chunks via server-side copyMessage..."
                    )
                }

                // Cache file names for display
                val fileNamesMap = mutableMapOf<String, String>()

                var successCount = 0

                // Step 3: Loop through each chunk and execute copyMessage
                for ((index, chunk) in recoverableChunks.withIndex()) {
                    val currentNumber = index + 1
                    val fileId = chunk.fileId
                    val storedMessageId = chunk.telegramMessageId ?: continue
                    val fromChatId = if (chunk.channelId.isNotBlank()) chunk.channelId else targetChatId

                    val fileName = fileNamesMap.getOrPut(fileId) {
                        database.fileDao().getById(fileId)?.name ?: "Vault Chunk"
                    }

                    _recoveryState.update {
                        it.copy(
                            currentChunk = currentNumber,
                            currentFileName = fileName,
                            progressFraction = (currentNumber.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f),
                            statusMessage = "Copying chunk $currentNumber of $totalChunks ($fileName) server-side..."
                        )
                    }

                    // Execute copyMessage with retry and backoff
                    var attempt = 0
                    val maxRetries = 5
                    var copySuccess = false
                    var lastError: String? = null

                    while (attempt < maxRetries && !copySuccess) {
                        attempt++
                        val copyResult = repository.recoverChunkViaCopyMessage(
                            standbyToken = standbyToken,
                            targetChatId = targetChatId,
                            fromChatId = fromChatId,
                            storedMessageId = storedMessageId
                        )

                        if (copyResult.isSuccess) {
                            copySuccess = true
                            val recovered = copyResult.getOrThrow()
                            database.chunkDao().updateChunkRecovery(
                                fileId = chunk.fileId,
                                chunkIndex = chunk.chunkIndex,
                                channelId = targetChatId,
                                telegramMessageId = recovered.newMessageId,
                                telegramFileId = recovered.newFileId
                            )
                            successCount++
                        } else {
                            lastError = copyResult.exceptionOrNull()?.message ?: "Copy failed"
                            Log.w("StandbyRecovery", "Attempt $attempt failed for chunk ${chunk.chunkIndex} of $fileId: $lastError")
                            if (attempt < maxRetries) {
                                delay((1000L * attempt).coerceAtLeast(1000L))
                            }
                        }
                    }

                    if (!copySuccess) {
                        Log.e("StandbyRecovery", "Failed to recover chunk ${chunk.chunkIndex} of $fileId after $maxRetries attempts: $lastError")
                        // Continue recovering other chunks so partial recovery completes
                    }

                    // Polite delay between requests to respect Telegram rate limits
                    delay(120L)
                }

                // Step 4: Finalize recovery
                _recoveryState.update { it.copy(statusMessage = "Finalizing vault credentials and channel records...") }

                // Update active bot token in credentials manager to the standby bot
                credentialsManager.setActiveBotToken(standbyToken)

                // Update all completed files to point to the active channel
                database.fileDao().updateChannelForCompletedFiles(targetChatId)

                // Update standby bot entity verification state
                database.standbyBotDao().updateVerification(
                    id = standbyBot.id,
                    isVerified = true,
                    timestamp = System.currentTimeMillis(),
                    channelId = targetChatId
                )

                _recoveryState.update {
                    it.copy(
                        isRecovering = false,
                        isCompleted = true,
                        recoveredChunksCount = successCount,
                        progressFraction = 1f,
                        statusMessage = "Recovery complete: $successCount of $totalChunks chunks recovered server-side. Active bot is now ${standbyBot.label}."
                    )
                }

                Log.i("StandbyRecovery", "Recovery finished successfully. Recovered $successCount / $totalChunks chunks.")

            } catch (e: CancellationException) {
                Log.i("StandbyRecovery", "Recovery was cancelled by user")
                _recoveryState.update {
                    it.copy(
                        isRecovering = false,
                        statusMessage = "Recovery cancelled"
                    )
                }
            } catch (e: Throwable) {
                val err = e.message ?: "Recovery encountered an error"
                Log.e("StandbyRecovery", "Fatal error during recovery: $err", e)
                _recoveryState.update {
                    it.copy(
                        isRecovering = false,
                        errorMessage = err,
                        statusMessage = "Recovery failed: $err"
                    )
                }
            }
        }
    }

    fun cancelRecovery() {
        recoveryJob?.cancel()
        _recoveryState.update {
            it.copy(
                isRecovering = false,
                statusMessage = "Recovery cancelled"
            )
        }
    }

    fun resetState() {
        _recoveryState.value = StandbyRecoveryState()
    }
}
