package com.depositdigest.app.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 텔레그램 봇 API로 입금 감지 메시지를 전송.
 */
class TelegramSender {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /** HTML 파싱 모드로 메시지 전송 */
    suspend fun sendHtml(botToken: String, chatId: String, html: String): Boolean =
        withContext(Dispatchers.IO) {
            if (botToken.isBlank() || chatId.isBlank()) return@withContext false

            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", html)
                put("parse_mode", "HTML")
            }.toString()

            val request = Request.Builder()
                .url("https://api.telegram.org/bot$botToken/sendMessage")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val err = resp.body?.string().orEmpty()
                        throw IOException("Telegram 전송 실패 ${resp.code}: $err")
                    }
                    resp.isSuccessful
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
}