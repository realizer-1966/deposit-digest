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

        val notifKey = sbn.key  // cancelNotification 에는 전체 key 필요

        scope.launch {
            // 디버그 모드: 패키지 필터 전에 모든 알림을 텔레그램으로 보고
            if (settings.debugMode && settings.telegramBotToken.isNotBlank()) {
                val dbg = "🧪 <b>알림 감지 (디버그)</b>\n" +
                    "패키지: <code>${sbn.packageName}</code>\n" +
                    "제목: $title\n" +
                    "내용: $text"
                runCatching { telegram.sendHtml(settings.telegramBotToken, settings.telegramChatId, dbg) }
            }

            // 설정된 은행 앱 패키지만 통과
            if (settings.bankPackages.isEmpty()) return@launch
            if (settings.bankPackages.none { sbn.packageName == it }) return@launch

            process(settings, sbn.packageName, title, text, notifKey)
        }
    }

    private suspend fun process(
        s: SettingsStore.Settings,
        packageName: String,
        title: String,
        text: String,
        notifKey: String
    ) {
        val fullText = "$title $text"
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date())
        android.util.Log.i("DepositDigest", "[$timestamp] 알림 감지: pkg=$packageName, title=$title, text=$text")

        // 1단계: 입금/출금 키워드 판별
        // ⚠️ "입출금통장" 같은 통장 이름에 "출금" 글자가 포함되므로,
        //    판별 전에 "입출금"을 제거해 오판을 방지한다.
        val normalizedText = fullText.replace("입출금", "")
        android.util.Log.i("DepositDigest", "[$timestamp] 판별 대상 텍스트: $normalizedText")

        val depositKeywords = listOf(
            "입금", "이체받음", "받았습니다", "지급", "예치",
            "수신", "입금완료", "입금되었습니다", "이체되었습니다", "송금받음"
        )
        val isDeposit = depositKeywords.any { normalizedText.contains(it) }
        
        val withdrawKeywords = listOf(
            "출금", "이체했습니다", "이체함", "보냈습니다", "송금함",
            "인출", "출금완료", "지출", "결제", "사용"
        )
        val isWithdraw = withdrawKeywords.any { normalizedText.contains(it) }
        
        android.util.Log.i("DepositDigest", "[$timestamp] 입금=$isDeposit, 출금=$isWithdraw")
        
        // 2단계: 입금 키워드 없고 출금 키워드 있으면 → 출금 알림 무시
        if (isWithdraw && !isDeposit) {
            val debugMsg = "🔍 <b>알림 감지됨 (출금으로 간주)</b>\n" +
                "시간: $timestamp\n" +
                "은행: ${packageName}\n" +
                "제목: $title\n" +
                "내용: $text\n\n" +
                "❌ 출금 알림은 무시됩니다"
            runCatching { telegram.sendHtml(s.telegramBotToken, s.telegramChatId, debugMsg) }
            android.util.Log.i("DepositDigest", "[$timestamp] 출금 알림 — 무시")
            return
        }
        
        // 3단계: 입금 키워드 없으면 로그만 보내고 종료
        if (!isDeposit) {
            val debugMsg = "🔍 <b>알림 감지됨 (입금 아님)</b>\n" +
                "시간: $timestamp\n" +
                "은행: ${packageName}\n" +
                "제목: $title\n" +
                "내용: $text\n\n" +
                "❌ 입금 키워드가 없습니다"
            runCatching { telegram.sendHtml(s.telegramBotToken, s.telegramChatId, debugMsg) }
            android.util.Log.i("DepositDigest", "[$timestamp] 입금 키워드 없음 — 무시")
            return
        }

        // 4단계: 금액 파싱
        val amount = parseAmount(fullText)

        // 5단계: 계좌 힌트 확인
        if (s.accountHint.isNotBlank()) {
            val hint = s.accountHint.trim()
            if (!fullText.contains(hint)) {
                val debugMsg = "🔍 <b>알림 감지됨 (계좌 힌트 불일치)</b>\n" +
                    "시간: $timestamp\n" +
                    "은행: ${packageName}\n" +
                    "제목: $title\n" +
                    "내용: $text\n" +
                    "예상 금액: ${if (amount != null) "%,d원".format(amount) else "미확인"}\n\n" +
                    "❌ 계좌 힌트 <b>$hint</b>가 알림에 없습니다"
                runCatching { telegram.sendHtml(s.telegramBotToken, s.telegramChatId, debugMsg) }
                android.util.Log.i("DepositDigest", "[$timestamp] 계좌 힌트 불일치 ($hint) — 무시")
                return
            }
        }

        // 6단계: 텔레그램 입금 알림 전송
        val bankLabel = s.bankLabel.ifBlank { packageName.substringAfterLast('.') }
        val amountStr = if (amount != null) "%,d원".format(amount) else "금액 미확인"
        val msg = buildString {
            appendLine("💰 <b>입금 알림 감지</b>")
            appendLine("은행: $bankLabel")
            appendLine("금액: $amountStr")
            appendLine("시각: $timestamp")
            appendLine()
            appendLine("<b>원본 알림</b>")
            appendLine("제목: $title")
            append("내용: $text")
        }

        runCatching { telegram.sendHtml(s.telegramBotToken, s.telegramChatId, msg) }
            .onFailure { 
                android.util.Log.e("DepositDigest", "[$timestamp] 텔레그램 전송 실패: ${it.message}")
                // 전송 실패 시에도 로그 보내기
                val failMsg = "❌ <b>텔레그램 전송 실패</b>\n" +
                    "시간: $timestamp\n" +
                    "오류: ${it.message}"
                android.util.Log.e("DepositDigest", "[$timestamp] failMsg=$failMsg")
            }
            .onSuccess { android.util.Log.i("DepositDigest", "[$timestamp] 텔레그램 전송 성공") }

        // 7단계: 알림 자동 제거
        removeNotification(notifKey)
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

    private fun removeNotification(notifKey: String) {
        runCatching { cancelNotification(notifKey) }  // 전체 key ("user|pkg|id|tag") 필요
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        android.util.Log.i("DepositDigest", "리스너 연결됨 — 알림 감지 시작")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}