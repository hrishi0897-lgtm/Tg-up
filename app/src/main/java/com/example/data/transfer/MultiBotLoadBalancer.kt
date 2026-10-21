package com.example.data.transfer

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.EncryptedCredentialsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class BotWorkerMetric(
    val tokenPreview: String,
    val label: String,
    val isPrimary: Boolean,
    val activeChunks: Int,
    val completedChunks: Int,
    val bytesDispatched: Long,
    val isCoolingDown: Boolean
)

/**
 * Multi-Bot Load Balancer for parallel chunk uploads and downloads.
 *
 * Distributes chunk transfers across the primary bot token and all verified standby bots
 * in the pool using a least-loaded + round-robin strategy while respecting per-bot
 * HTTP 429 rate-limit cooldowns.
 */
class MultiBotLoadBalancer private constructor(
    private val context: Context,
    private val database: AppDatabase,
    private val credentialsManager: EncryptedCredentialsManager
) {
    private val roundRobinCursor = AtomicInteger(0)
    private val activePerBot = ConcurrentHashMap<String, AtomicInteger>()
    private val completedPerBot = ConcurrentHashMap<String, AtomicInteger>()
    private val bytesPerBot = ConcurrentHashMap<String, AtomicLong>()
    private val cooldownUntilMs = ConcurrentHashMap<String, Long>()
    private val botLabels = ConcurrentHashMap<String, String>()

    private val _botMetrics = MutableStateFlow<List<BotWorkerMetric>>(emptyList())
    val botMetrics: StateFlow<List<BotWorkerMetric>> = _botMetrics.asStateFlow()

    companion object {
        private const val TAG = "MultiBotLoadBalancer"

        @Volatile
        private var INSTANCE: MultiBotLoadBalancer? = null

        fun getInstance(context: Context): MultiBotLoadBalancer {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                INSTANCE ?: MultiBotLoadBalancer(
                    appCtx,
                    AppDatabase.getInstance(appCtx),
                    EncryptedCredentialsManager(appCtx)
                ).also { INSTANCE = it }
            }
        }
    }

    /**
     * Collects all available bot tokens (primary token + token pool + verified standby bots).
     */
    suspend fun getActiveBotPool(): List<String> {
        val tokens = linkedSetOf<String>()
        val primary = credentialsManager.getBotToken()?.trim()
        if (!primary.isNullOrEmpty()) {
            tokens.add(primary)
            botLabels.putIfAbsent(primary, "Primary Bot")
        }

        for ((idx, poolToken) in credentialsManager.getTokenPool().withIndex()) {
            val clean = poolToken.trim()
            if (clean.isNotEmpty()) {
                tokens.add(clean)
                botLabels.putIfAbsent(clean, if (clean == primary) "Primary Bot" else "Pool Bot #${idx + 1}")
            }
        }

        try {
            val standbyBots = database.standbyBotDao().getStandbyBotsList().filter { it.isVerifiedMember }
            for (bot in standbyBots) {
                val decrypted = credentialsManager.decryptToken(bot.encryptedToken)?.trim()
                if (!decrypted.isNullOrEmpty()) {
                    tokens.add(decrypted)
                    val display = if (!bot.username.isNullOrBlank()) "@${bot.username}" else bot.label
                    botLabels[decrypted] = display
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading standby bots for load balancer: ${e.message}")
        }

        val list = tokens.toList()
        emitMetrics(list)
        return list
    }

    /**
     * Selects the next optimal bot token for a chunk transfer.
     * Prefers bots not in HTTP 429 cooldown and with the fewest active concurrent chunks.
     */
    suspend fun acquireBotForChunk(fallbackPrimaryToken: String): String {
        val pool = getActiveBotPool()
        if (pool.size <= 1) {
            val single = pool.firstOrNull() ?: fallbackPrimaryToken
            activePerBot.getOrPut(single) { AtomicInteger(0) }.incrementAndGet()
            emitMetrics(pool.ifEmpty { listOf(fallbackPrimaryToken) })
            return single
        }

        val now = System.currentTimeMillis()
        val available = pool.filter { (cooldownUntilMs[it] ?: 0L) <= now }
        val candidates = available.ifEmpty { pool }

        val startIdx = (roundRobinCursor.getAndIncrement() and Int.MAX_VALUE) % candidates.size
        var chosen = candidates[startIdx]
        var minActive = activePerBot[chosen]?.get() ?: 0

        for (i in candidates.indices) {
            val token = candidates[(startIdx + i) % candidates.size]
            val active = activePerBot[token]?.get() ?: 0
            if (active < minActive) {
                minActive = active
                chosen = token
            }
        }

        activePerBot.getOrPut(chosen) { AtomicInteger(0) }.incrementAndGet()
        emitMetrics(pool)
        return chosen
    }

    fun releaseBotForChunk(token: String, bytesTransferred: Long, succeeded: Boolean) {
        activePerBot[token]?.updateAndGet { maxOf(0, it - 1) }
        if (succeeded) {
            completedPerBot.getOrPut(token) { AtomicInteger(0) }.incrementAndGet()
            if (bytesTransferred > 0) {
                bytesPerBot.getOrPut(token) { AtomicLong(0L) }.addAndGet(bytesTransferred)
            }
        }
        refreshMetricsSnapshot()
    }

    fun markRateLimited(token: String, retryAfterSeconds: Int = 15) {
        val cooldownMs = (retryAfterSeconds.coerceIn(3, 120)) * 1000L
        cooldownUntilMs[token] = System.currentTimeMillis() + cooldownMs
        Log.w(TAG, "Bot ${maskToken(token)} rate-limited; cooling down for ${retryAfterSeconds}s")
        refreshMetricsSnapshot()
    }

    fun refreshMetricsSnapshot() {
        val primary = credentialsManager.getBotToken()?.trim()
        val knownTokens = linkedSetOf<String>()
        if (!primary.isNullOrEmpty()) knownTokens.add(primary)
        knownTokens.addAll(credentialsManager.getTokenPool().map { it.trim() }.filter { it.isNotEmpty() })
        knownTokens.addAll(activePerBot.keys)
        knownTokens.addAll(completedPerBot.keys)
        emitMetrics(knownTokens.toList())
    }

    private fun emitMetrics(tokens: List<String>) {
        val primary = credentialsManager.getBotToken()?.trim()
        val now = System.currentTimeMillis()
        val metrics = tokens.mapIndexed { idx, token ->
            BotWorkerMetric(
                tokenPreview = maskToken(token),
                label = botLabels[token] ?: if (token == primary) "Primary Bot" else "Bot Worker #${idx + 1}",
                isPrimary = token == primary,
                activeChunks = activePerBot[token]?.get() ?: 0,
                completedChunks = completedPerBot[token]?.get() ?: 0,
                bytesDispatched = bytesPerBot[token]?.get() ?: 0L,
                isCoolingDown = (cooldownUntilMs[token] ?: 0L) > now
            )
        }
        _botMetrics.value = metrics
    }

    private fun maskToken(token: String): String {
        val clean = token.trim()
        if (clean.length <= 10) return clean
        return "${clean.take(6)}…${clean.takeLast(4)}"
    }
}
