# AGV CFG Manager

ESP32-S3 AGV Wi-Fi 브리지([AGV_Wifi_Bridge](https://github.com/Leepy0/AGV_Wifi_Bridge))의 **호기 설정 · 확인 · 진단**을 휴대폰 한 화면에서 하는 **Android 앱**입니다. 현장에서 BLE 터미널 앱에 명령을 한 줄씩 치던 일을 대신합니다.

> 현재 **계획 단계**입니다. 코드는 아직 없습니다.

- 보드 찾기(BLE) → 누르면 연결 · 로그인 · 읽기까지 자동.
- 호기 번호 하나만 입력하면 현장 프로필(SSID · DNS · 서버 …)과 IP 규칙으로 나머지를 채웁니다. 휴대폰이 붙은 Wi-Fi 에서 게이트웨이 · DNS · 마스크를 읽어 새 프로필 초깃값으로 씁니다.
- SSID ↔ 비밀번호, 현장 프로필, 작업 이력을 저장해 다시 씁니다. 프로필은 QR · 파일로 다른 휴대폰에 옮깁니다.
- `set` → `cfg` 대조 → `save` → `reboot` → 재연결 → `wifi: UP` 확인까지 자동. 여러 대 연속 세팅.
- 진단: Wi-Fi 끊김 사유 해석, 게이트웨이 PC 도달 확인, 콘솔.
- 굽기는 범위 밖(PC 에서 `flash.bat`). (선택) USB OTG 시리얼.

| 문서 | 내용 |
|---|---|
| [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) | 개발 계획서: 목표 · 시나리오 · 브리지 인터페이스 레퍼런스 · 기능 명세 · 화면 · 아키텍처 · 워크플로 · Android BLE 지침 · 오류 표 · 테스트 · 단계 · 배포 |
| [docs/BRIDGE_CHANGE_REQUESTS.md](docs/BRIDGE_CHANGE_REQUESTS.md) | 브리지 펌웨어에 요청하는 변경 (앱은 미반영 상태로도 동작) |
| [CLAUDE.md](CLAUDE.md) | 개발 작업 규칙 |

기술: Kotlin · Jetpack Compose · Material 3 · minSdk 26 · GitHub Releases APK + 앱 내 업데이트(github.com 매니페스트).

## 빌드

준비물: JDK 17 이상(CI 는 17), Android SDK 의 `platforms;android-35` · `build-tools;35.0.0`. `local.properties` 에 `sdk.dir=…` 을 적거나 `ANDROID_HOME` 을 설정합니다. `local.properties` 는 커밋하지 않습니다.

```
./gradlew :core:test                                  # JVM 단위 테스트 (하드웨어 없이)
./gradlew :app:assembleDebug                          # 디버그 APK → app/build/outputs/apk/debug/
./gradlew :app:assembleDebug -Pchannel=dev -PversionName=0.1.0 -PversionCode=100
```

`-Pchannel` 은 앱 안의 `BuildConfig.CHANNEL`(기본 `local`), `-PversionName` · `-PversionCode` 는 버전 값을 덮어씁니다. 릴리스 서명과 정식 배포 절차는 계획서 12장(Phase 6)에서 추가합니다.
