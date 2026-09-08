package com.depositdigest.app.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.depositdigest.app.data.SettingsStore
import com.depositdigest.app.telegram.TelegramSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 은행 앱 입금 알림 감지 리스너
 *
 * 감지한 알림 텍스트에서 입금 키워드와 금액을 파싱해서
 * 텔레그램으로 전송한다. (1단계: 전송까지만. 임대관리앱 연동은 2단계)
 */
class BankNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val telegram = TelegramSender()

    // 중복 제거: (패키지명+제목+내용) 해시, 3초 윈도
    private val recentProcessed = object : LinkedHashMap<Int, Long>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Long>?): Boolean =
            size > 200
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val settings = runCatching {
            kotlinx.coroutines.runBlocking { SettingsStore(applicationContext).settingsFlow.first() }
        }.getOrNull() ?: return

        // 설정된 은행 앱 패키지만 통과
        if (settings.bankPackages.isEmpty()) return
        if (settings.bankPackages.none { sbn.packageName == it }) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return

        // 중복 제거 (3초)
        val hash = (sbn.packageName + title + text).hashCode()
        val now = System.currentTimeMillis()
        val last = recentProcessed[hash]
        if (last != null && now - last < 3000) return
        recentProcessed[hash] = now

        scope.launch {
            process(settings, sbn.packageName, title, text, sbn.id)
        }
    }

    private suspend fun process(
        s: SettingsStore.Settings,
        packageName: String,
        title: String,
        text: String,
        notifId: Int
    ) {
        val fullText = "$title $text"

        // 입금/출금 키워드 판별 — 입금 관련 키워드가 있을 때만 전송
        val depositKeywords = listOf("입금", "이체받음", "받았습니다", "지급", "예치")
        val isDeposit = depositKeywords.any { fullText.contains(it) }
        val isWithdraw = fullText.contains("출금") || fullText.contains("이체했습니다")
        if (!isDeposit || isWithdraw) return  // 출금 알림은 무시

        // 금액 파싱: "500,000원" / "500000원" / "50만원"
        val amount = parseAmount(fullText)

        // 계좌 힌트 파싱 (마지막 4자리 등): 설정에 지정 계좌 힌트가 있으면 일치할 때만 전송
        if (s.accountHint.isNotBlank()) {
            val hint = s.accountHint.trim()
            if (!fullText.contains(hint)) return
        }

        // 텔레그램 메시지 조립
        val bankLabel = s.bankLabel.ifBlank { packageName.substringAfterLast('.') }
        val amountStr = if (amount != null) "%,d원".format(amount) else "금액 미확인"
        val msg = buildString {
            appendLine("💰 <b>입금 알림 감지</b>")
            appendLine("은행: $bankLabel")
            appendLine("금액: $amountStr")
            appendLine("시각: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date())}")
            appendLine()
            appendLine("<b>원본 알림</b>")
            appendLine("제목: $title")
            append("내용: $text")
        }

        runCatching { telegram.sendHtml(s.telegramBotToken, s.telegramChatId, msg) }
            .onFailure { android.util.Log.e("DepositDigest", "텔레그램 전송 실패: ${it.message}") }

        // 알림 자동 제거
        removeNotification(notifId)
    }

    /** "500,000원", "500000원", "50만원" → Long (원 단위). 파싱 실패 시 null */
    private fun parseAmount(text: String): Long? {
        // "1,234,567원" 또는 "1234567원"
        Regex("([0-9][0-9,]{2,})\\s*원").find(text)?.let { m ->
            val digits = m.groupValues[1].replace(",", "")
            digits.toLongOrNull()?.let { return it }
        }
        // "50만원"
        Regex("([0-9]+)\\s*만원").find(text)?.let { m ->
            m.groupValues[1].toLongOrNull()?.let { return it * 10_000 }
        }
        return null
    }

    private fun removeNotification(notifId: Int) {
        runCatching { cancelNotification(notifId.toString()) }  // String 변환 필수
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}