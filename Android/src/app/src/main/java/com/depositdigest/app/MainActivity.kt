package com.depositdigest.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.depositdigest.app.data.SettingsStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    SettingsScreenContent()
}

@Composable
fun SettingsScreenContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var bankPackages by remember { mutableStateOf("") }
    var bankLabel by remember { mutableStateOf("") }
    var accountHint by remember { mutableStateOf("") }
    var botToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var listenerEnabled by remember { mutableStateOf(false) }

    // 설정 로드
    LaunchedEffect(Unit) {
        store.settingsFlow.collect { s ->
            bankPackages = s.bankPackages.joinToString("\n")
            bankLabel = s.bankLabel
            accountHint = s.accountHint
            botToken = s.telegramBotToken
            chatId = s.telegramChatId
        }
    }

    // 알림 접근 권한 확인
    LaunchedEffect(Unit) {
        val cn = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        listenerEnabled = cn.contains("com.depositdigest.app")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("💰 Deposit Digest", style = MaterialTheme.typography.headlineSmall)
        Text(
            "은행 입금 알림을 감지해서 텔레그램으로 전송합니다.",
            style = MaterialTheme.typography.bodySmall
        )

        if (!listenerEnabled) {
            Text("⚠️ 알림 접근 권한이 꺼져 있습니다. 아래 버튼으로 켜주세요.", color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) {
                Text("알림 접근 권한 켜기")
            }
        } else {
            Text("✅ 알림 접근 권한 사용 중", color = MaterialTheme.colorScheme.primary)
        }

        OutlinedTextField(
            value = bankPackages,
            onValueChange = { bankPackages = it },
            label = { Text("은행 앱 패키지명 (한 줄에 하나)") },
            placeholder = { Text("com.kbstar.kbbank\ncom.shinhan.sbanking") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        OutlinedTextField(
            value = bankLabel,
            onValueChange = { bankLabel = it },
            label = { Text("은행 라벨 (선택, 예: KB국민)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = accountHint,
            onValueChange = { accountHint = it },
            label = { Text("계좌 힌트 (선택, 예: 123-45)") },
            placeholder = { Text("알림에 이 텍스트가 포함된 것만 전송") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = botToken,
            onValueChange = { botToken = it },
            label = { Text("텔레그램 봇 토큰") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = chatId,
            onValueChange = { chatId = it },
            label = { Text("텔레그램 채팅 ID") },
            modifier = Modifier.fillMaxWidth()
        )

        Row {
            Button(onClick = {
                scope.launch {
                    store.update(
                        bankPackages = bankPackages.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                        bankLabel = bankLabel,
                        accountHint = accountHint,
                        telegramBotToken = botToken,
                        telegramChatId = chatId,
                    )
                    saved = true
                }
            }) {
                Text("저장")
            }
            Spacer(Modifier.height(8.dp))
            if (saved) {
                Text("  저장됨 ✓", color = MaterialTheme.colorScheme.primary)
            }
        }

        TextButton(onClick = {
            // 테스트 메시지 전송
            scope.launch {
                val s = store.settingsFlow
                // 간단 테스트: 텔레그램 전송 확인
                val sender = com.depositdigest.app.telegram.TelegramSender()
                sender.sendHtml(botToken, chatId, "🧪 <b>Deposit Digest 테스트</b>\n설정 저장 및 전송 정상 동작!")
            }
        }) {
            Text("테스트 메시지 전송")
        }
    }
}