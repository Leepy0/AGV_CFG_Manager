# AGV CFG Manager — 작업 규칙

ESP32-S3 AGV Wi-Fi 브리지의 호기 설정 · 확인 · 진단용 **Android 앱**(Kotlin, Jetpack Compose, Material 3). 현장 경로인 휴대폰 BLE(NUS)로 브리지에 붙는다. **지금은 계획 단계**이며 코드는 아직 없습니다.

## 먼저 읽을 것 (순서대로)

1. `docs/DEVELOPMENT_PLAN.md` — 0장(작업 방식) → 1 · 2장 → 11장(단계). 3장(브리지 인터페이스)과 8장(Android BLE 지침)은 구현 중 레퍼런스.
2. `docs/BRIDGE_CHANGE_REQUESTS.md` — 브리지에 요청한 변경. 앱은 미반영 상태로도 동작해야 함.
3. 화면 작업 전: 스킬 `field-app-ui-design`(Android 절). 데스크톱용 스킬(`wpf-build-release`, `dotnet-self-update`)은 배포 방식(github.com 매니페스트)만 참고.

## 브리지 저장소

- 참조 소스: `Leepy0/AGV_Wifi_Bridge` (이 환경에서는 `../AGV_Wifi_Bridge`). **읽기만** 하고 수정하지 않는다. 변경이 필요하면 `docs/BRIDGE_CHANGE_REQUESTS.md` 에 항목을 추가한다.
- 계획서 3장과 브리지 소스가 다르면 소스가 맞다. 계획서를 고치고 커밋에 적는다.
- 기준 브리지 버전 0.5.5. 구버전(0.5.0~0.5.4)도 지원해야 하며 기능 유무는 출력으로 감지한다.

## 구현 규칙

- 로직은 전부 `:core`(순수 Kotlin/JVM, Android 의존 없음)에, BLE · 저장소 · UI 는 `:app` 에. `:core` 는 JVM 단위 테스트로 검증한다.
- 하드웨어 없이 돌릴 수 있게 `FakeBridgeChannel` 을 먼저 만들고, 디버그 빌드의 개발자 옵션 "가짜 브리지" 로 UI 를 실행할 수 있어야 한다.
- GATT 연산은 한 번에 하나(`GattQueue`), 명령도 한 번에 하나(`Mutex`). 끊김 뒤에는 항상 `gatt.close()`.
- 메인 스레드에서 블로킹 I/O 금지. 긴 작업은 코루틴 + 진행 Flow + 취소.
- 비밀번호 · 사내 IP 를 로그 · 테스트 fixture · 스크린샷에 남기지 않는다(예시 IP 는 `192.168.10.x`). 콘솔 화면에서도 `login` · `set pass` 값은 `***`.
- 앱은 사내망 · 외부 네트워크와 무관하게 동작해야 한다. 빌드 의존성은 `google()` · `mavenCentral()` 과 저장소 안(`third_party/`)만 쓴다. JitPack 등 다른 호스트 의존성은 추가하지 않고, 필요하면 소스를 `third_party/` 에 둔다. 런타임에도 외부 호스트에 의존하는 기능을 기본 동작으로 두지 않는다 (업데이트 확인처럼 명시적 기능만 예외).
- 한국어 UI · 주석 · 문서, 영어 식별자. 색 · 간격 · 글꼴은 `ui/theme` 토큰만 사용. dynamic color 끔, light/dark 모두 정의.
- 라이브러리 버전은 작업 시점의 stable 을 확인해 `gradle/libs.versions.toml` 에 둔다. Material 3 Expressive 가 alpha 면 쓰지 않는다.
- 코드 주석 · 커밋에 모델 이름을 넣지 않는다.

## 단계 완료 절차

1. 해당 Phase 의 완료 기준을 전부 확인한다 (계획서 11장).
2. `./gradlew :core:test :app:assembleDebug` 통과, 새 경고 없이.
3. 결정한 것이 있으면 `docs/DECISIONS.md` 에 한 줄.
4. 커밋 메시지: 한국어 요약 한 줄(무엇을 · 왜) + 필요하면 본문. 푸시.

## 빌드 · 실행

```
./gradlew :core:test                 # JVM 단위 테스트
./gradlew :app:assembleDebug         # 디버그 APK (개발자 옵션에서 가짜 브리지 켜기)
./gradlew :app:assembleRelease -PversionName=0.1.0 -PversionCode=100 -Pchannel=release
```
