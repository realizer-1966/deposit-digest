# 💰 Deposit Digest

은행 입금 알림을 감지해서 텔레그램으로 전송하는 Android 앱.

**월세 자동 체크 1단계** — 임대관리앱(rental-web) 연동은 2단계에서 추가 예정.

## 동작 방식

```
은행 앱 입금 푸시 알림
    ↓
NotificationListenerService (BankNotificationListener)
    ├─ 설정된 은행 앱 패키지만 통과
    ├─ 입금 키워드 판별 (입금/이체받음/지급…) — 출금 알림은 무시
    ├─ 금액 파싱 ("500,000원" / "50만원")
    └─ 계좌 힌트 일치 시에만 전송 (선택)
    ↓
텔레그램 전송 (HTML 포맷)
    💰 입금 알림 감지
    은행 / 금액 / 시각 / 원본 알림
    ↓
알림 자동 제거 (3초)
```

## 설정 (앱 내 설정 화면)

| 항목 | 설명 | 예시 |
|------|------|------|
| 은행 앱 패키지명 | 한 줄에 하나 | `com.kbstar.kbbank` |
| 은행 라벨 | 텔레그램 표시용 (선택) | `KB국민` |
| 계좌 힌트 | 알림에 이 텍스트가 포함된 것만 전송 (선택) | `123-45` |
| 텔레그램 봇 토큰 | BotFather 발급 | `123:ABC...` |
| 텔레그램 채팅 ID | 대상 채팅 | `577294968` |

## 주요 은행 앱 패키지명

| 은행 | 패키지명 |
|------|----------|
| KB국민 (스마트뱅킹) | `com.kbstar.kbbank` |
| KB국민 (리브) | `com.kbstar.reboot` |
| 신한 쏠 (SOL) | `com.shinhan.sbanking` |
| 하나 | `com.kebhana.hanapush` |
| 우리 (WON뱅킹) | `com.wooribank.smart` |
| NH올원e | `com.nh.corp` |
| 토스 | `viva.republica.toss` |
| 카카오뱅크 | `com.kakaobank.channel` |

> ⚠️ 실제 감지되는 패키지명은 기기마다 다를 수 있으므로,
> 테스트 알림으로 확인 후 설정에 입력하세요.

## 빌드

GitHub Actions (push 시 자동 빌드):
```
Android/** 변경 → .github/workflows/build_android.yaml → APK 아티팩트
```

로컬 (Android Studio / Gradle CLI):
```bash
cd Android/src
./gradlew assembleRelease
```

## 설치 후 체크리스트

1. 앱 실행 → **알림 접근 권한 켜기** (설정 → 특수 앱 접근 → 알림 접근 → DepositDigest 허용)
2. 은행 앱 패키지명 / 텔레그램 토큰·채팅ID 입력 → 저장
3. **테스트 메시지 전송** 버튼으로 텔레그램 연결 확인
4. 실제 입금 발생 시 텔레그램 수신 확인

## 2단계 (예정)

- 감지한 입금내역 → Cloudflare Workers 백엔드 저장
- rental-web (임대관리앱) 이 입금내역 fetch → 세입자 월세 자동 체크
- 설계 문서: `agent-dev-company/projects/rental-web/docs/deposit-autocheck-design.md`

## 기술 스택

- Kotlin, Jetpack Compose (설정 UI만)
- NotificationListenerService
- DataStore (설정 저장)
- OkHttp (텔레그램 Bot API)
- minSdk 26 / targetSdk 37

kakao-digest 프로젝트를 기반으로 만들어졌습니다.