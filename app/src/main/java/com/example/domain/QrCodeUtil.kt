package com.example.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject
import java.util.EnumMap

data class PairingData(
    val token: String,
    val chatId: String
)

object QrCodeUtil {

    private const val TAG = "QrCodeUtil"

    /**
     * Generates a square QR Code Bitmap locally using ZXing without any network dependencies.
     */
    fun generateQrBitmap(
        content: String,
        size: Int = 512,
        darkColor: Int = Color.BLACK,
        lightColor: Int = Color.WHITE
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                put(EncodeHintType.MARGIN, 2)
            }
            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) darkColor else lightColor
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate QR bitmap: ${e.message}", e)
            null
        }
    }

    /**
     * Encodes bot token and chat ID into a standard pairing JSON string.
     */
    fun createPairingPayload(token: String, chatId: String): String {
        return JSONObject().apply {
            put("token", token.trim())
            put("chatId", chatId.trim())
            put("version", 1)
        }.toString()
    }

    /**
     * Parses a scanned QR string into [PairingData].
     * Supports JSON format as well as tolerant token/chatId fallback patterns.
     */
    fun parsePairingPayload(raw: String): PairingData? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        // 1. Try standard JSON format: {"token":"...","chatId":"..."}
        try {
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val json = JSONObject(trimmed)
                val token = when {
                    json.has("token") -> json.getString("token")
                    json.has("bot_token") -> json.getString("bot_token")
                    json.has("botToken") -> json.getString("botToken")
                    else -> ""
                }.trim()

                val chatId = when {
                    json.has("chatId") -> json.getString("chatId")
                    json.has("chat_id") -> json.getString("chat_id")
                    json.has("channelId") -> json.getString("channelId")
                    else -> ""
                }.trim()

                if (token.isNotEmpty() && chatId.isNotEmpty()) {
                    return PairingData(token = token, chatId = chatId)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "JSON parsing failed for QR, trying fallbacks: ${e.message}")
        }

        // 2. Delimited fallbacks: "token|chatId" or "token:::chatId"
        if (trimmed.contains("|||")) {
            val parts = trimmed.split("|||")
            if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return PairingData(parts[0].trim(), parts[1].trim())
            }
        }

        if (trimmed.contains("|")) {
            val parts = trimmed.split("|")
            if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return PairingData(parts[0].trim(), parts[1].trim())
            }
        }

        return null
    }
}
