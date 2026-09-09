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
        /** 계좌 힌트 목록 — bankPackages 순서와 동일하게 한 줄에 하나. 입력 시 그 계좌의 입금+출금 모두 전송, 비우면 입금만 전송. */
        val accountHints: List<String> = emptyList(),
        val telegramBotToken: String = "",
        val telegramChatId: String = "",
        /** 디버그 모드: 패키지 필터 전에 감지한 모든 알림을 텔레그램으로 보고 */
        val debugMode: Boolean = false,
    )

    private object Keys {
        val bankPackages = stringPreferencesKey("bank_packages")
        val bankLabel = stringPreferencesKey("bank_label")
        val accountHints = stringPreferencesKey("account_hints")
        val telegramBotToken = stringPreferencesKey("telegram_bot_token")
        val telegramChatId = stringPreferencesKey("telegram_chat_id")
        val debugMode = booleanPreferencesKey("debug_mode")
    }

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            bankPackages = (prefs[Keys.bankPackages] ?: "").split("\n")
                .map { it.trim() }.filter { it.isNotEmpty() },
            bankLabel = prefs[Keys.bankLabel] ?: "",
            accountHints = (prefs[Keys.accountHints] ?: "").split("\n").map { it.trim() },
            telegramBotToken = prefs[Keys.telegramBotToken] ?: "",
            telegramChatId = prefs[Keys.telegramChatId] ?: "",
            debugMode = prefs[Keys.debugMode] ?: false,
        )
    }

    suspend fun update(
        bankPackages: List<String>? = null,
        bankLabel: String? = null,
        accountHints: List<String>? = null,
        telegramBotToken: String? = null,
        telegramChatId: String? = null,
        debugMode: Boolean? = null,
    ) {
        context.dataStore.edit { prefs ->
            bankPackages?.let { prefs[Keys.bankPackages] = it.joinToString("\n") }
            bankLabel?.let { prefs[Keys.bankLabel] = it }
            accountHints?.let { prefs[Keys.accountHints] = it.joinToString("\n") }
            telegramBotToken?.let { prefs[Keys.telegramBotToken] = it }
            telegramChatId?.let { prefs[Keys.telegramChatId] = it }
            debugMode?.let { prefs[Keys.debugMode] = it }
        }
    }
}