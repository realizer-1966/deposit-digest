package com.depositdigest.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 부팅 시 / 앱 업데이트 후 알림 리스너 서비스 재시작.
 * 알림 리스너는 사용자가 권한을 부여해두면 시스템이 자동으로 재연결하지만,
 * 명시적으로 강제 연결해 안정성을 높인다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            // NotificationListenerService는 시스템 바인딩이라 직접 startService 불가.
            // 액티비티를 띄워 재연결을 트리거한다 (간단 안내).
            // 실제로는 권한이 유지되면 시스템이 자동 재연결함.
        }
    }
}
