package com.depositdigest.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "deposit_digest_settings")

class SettingsStore(private val context: Context) {

    data class Settings(
        /** 감지 대상 은행 앱 패키지명 (한 줄에 하나) */
        val bankPackages: List<String> = emptyList(),
        /** 텔레그램에 표시할 은행 라벨 (선택, 예: "KB국민") */
        val bankLabel: String = "",
        /** 내 계좌 식별 힌트 (선택, 예: "123-45" — 알림에 이 텍스트가 포함된 것만 전송) */
        val accountHint: String = "",
        val telegramBotToken: String = "",
        val telegramChatId: String = "",
        /** 디버그 모드: 패키지 필터 전에 감지한 모든 알림을 텔레그램으로 보고 */
        val debugMode: Boolean = false,
    )

    private object Keys {
        val bankPackages = stringPreferencesKey("bank_packages")
        val bankLabel = stringPreferencesKey("bank_label")
        val accountHint = stringPreferencesKey("account_hint")
        val telegramBotToken = stringPreferencesKey("telegram_bot_token")
        val telegramChatId = stringPreferencesKey("telegram_chat_id")
        val debugMode = booleanPreferencesKey("debug_mode")
    }

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            bankPackages = (prefs[Keys.bankPackages] ?: "").split("\n")
                .map { it.trim() }.filter { it.isNotEmpty() },
            bankLabel = prefs[Keys.bankLabel] ?: "",
            accountHint = prefs[Keys.accountHint] ?: "",
            telegramBotToken = prefs[Keys.telegramBotToken] ?: "",
            telegramChatId = prefs[Keys.telegramChatId] ?: "",
            debugMode = prefs[Keys.debugMode] ?: false,
        )
    }

    suspend fun update(
        bankPackages: List<String>? = null,
        bankLabel: String? = null,
        accountHint: String? = null,
        telegramBotToken: String? = null,
        telegramChatId: String? = null,
        debugMode: Boolean? = null,
    ) {
        context.dataStore.edit { prefs ->
            bankPackages?.let { prefs[Keys.bankPackages] = it.joinToString("\n") }
            bankLabel?.let { prefs[Keys.bankLabel] = it }
            accountHint?.let { prefs[Keys.accountHint] = it }
            telegramBotToken?.let { prefs[Keys.telegramBotToken] = it }
            telegramChatId?.let { prefs[Keys.telegramChatId] = it }
            debugMode?.let { prefs[Keys.debugMode] = it }
        }
    }
}