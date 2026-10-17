# AGV CFG Manager

ESP32-S3 AGV Wi-Fi 브리지([AGV_Wifi_Bridge](https://github.com/Leepy0/AGV_Wifi_Bridge))의 **호기 설정 · 확인 · 진단**을 휴대폰 한 화면에서 하는 **Android 앱**입니다. 현장에서 BLE 터미널 앱에 명령을 한 줄씩 치던 일을 대신합니다.

- 보드 찾기(BLE) → 누르면 연결 · 로그인 · 읽기까지 자동.
- 호기 번호 하나만 입력하면 현장 프로필(SSID · DNS · 서버 …)과 IP 규칙으로 나머지를 채웁니다. 휴대폰이 붙은 Wi-Fi 에서 게이트웨이 · DNS · 마스크를 읽어 새 프로필의 초깃값으로 씁니다.
- SSID ↔ 비밀번호, 현장 프로필, 작업 이력을 저장해 다시 씁니다. 프로필은 QR · 파일로 다른 휴대폰에 옮깁니다.
- `set` → `cfg` 대조 → `save` → `reboot` → 재연결 → `wifi: UP` 확인까지 자동. 여러 대 연속 세팅.
- 진단: Wi-Fi 끊김 사유 해석, 게이트웨이 PC 도달 확인, 콘솔.
- 앱 내 업데이트: 정보 화면의 [지금 확인] 또는 시작 때 자동 확인 → 받기 → sha256 검사 → 설치.
- 굽기는 범위 밖입니다(PC 에서 `flash.bat`).

| 문서 | 내용 |
|---|---|
| [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) | 개발 계획서: 목표 · 시나리오 · 브리지 인터페이스 · 기능 명세 · 화면 · 아키텍처 · 워크플로 · 테스트 · 단계(11장 끝 "현황") · 배포(12장) |
| [docs/BRIDGE_CHANGE_REQUESTS.md](docs/BRIDGE_CHANGE_REQUESTS.md) | 브리지 펌웨어에 요청하는 변경 (앱은 미반영 상태로도 동작) |
| [docs/DECISIONS.md](docs/DECISIONS.md) | 구현 중 내린 결정과 이유 |
| [CLAUDE.md](CLAUDE.md) | 개발 작업 규칙 |

기술: Kotlin · Jetpack Compose · Material 3 · minSdk 26 · GitHub Releases APK + 앱 내 업데이트(github.com 매니페스트).

## 설치 (현장 휴대폰)

1. 휴대폰 브라우저에서 아래 주소를 엽니다. 사내망에서는 github.com 만 열려 있으면 됩니다.
   `https://github.com/Leepy0/AGV_CFG_Manager/releases/latest/download/AgvCfgManager.apk`
2. 받은 `AgvCfgManager.apk` 를 누르면 "알 수 없는 앱 설치" 안내가 나옵니다. 브라우저(또는 파일 앱)에 **이 출처 허용**을 한 번 켭니다.
3. 설치 후 앱을 엽니다. 다음 업데이트부터는 앱 안에서 합니다: ⚙ → **정보** → [지금 확인] → [받기] → [설치]. 시작 때 자동 확인은 설정 → 업데이트 자동 확인에서 끌 수 있습니다.

업데이트는 **같은 서명 키**로 만든 APK 만 덮어 설치됩니다. 아래 "릴리스" 절차로 만든 `AgvCfgManager.apk` 가 정식 APK 입니다. `dev` 프리릴리스(main 최신 빌드)는 시험용이며 정식 릴리스가 아닙니다.

## 첫 세팅 흐름 (S1 요약)

1. 보드 전원을 넣습니다. 설정 모드가 열려 `AGVBR-01-XXXX` 같은 이름으로 광고합니다.
2. 보드 탭 **[보드 찾기]** → 가까운 보드를 누릅니다. 연결 · 로그인 · 읽기는 자동입니다.
3. 설정 탭에서 프로필(예: 본사 1공장)을 고르고 **호기 번호**를 입력합니다. IP · SSID · 비밀번호 · 마스크 · 게이트웨이 · DNS · 서버가 채워집니다. 필요하면 SSID 를 라이브러리에서 고릅니다.
4. 하단 **[적용]** → 16개 설정 · 되읽기 대조 · 저장 · 재부팅 · 재연결 · Wi-Fi 확인까지 자동. 결과 카드와 이력에 남습니다.
5. 여러 대를 이어서 할 때는 **연속 모드**를 켭니다. 호기 번호가 자동으로 올라갑니다.
6. 문제가 있으면 진단 탭의 상태 카드와 콘솔을 봅니다. 앱 로그는 정보 화면의 [로그 내보내기]로 공유합니다(비밀번호는 기록하지 않습니다).

## 빌드

준비물: JDK 17 이상(CI 는 17), Android SDK 의 `platforms;android-35` · `build-tools;35.0.0`. `local.properties` 에 `sdk.dir=…` 을 적거나 `ANDROID_HOME` 을 설정합니다. `local.properties` 는 커밋하지 않습니다.

```
./gradlew :core:test :app:testDebugUnitTest            # JVM 단위 테스트 (하드웨어 없이)
./gradlew :app:assembleDebug                           # 디버그 APK → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease -Pchannel=release -PversionName=0.1.0 -PversionCode=100
```

- `-Pchannel` 은 앱 안의 `BuildConfig.CHANNEL`(기본 `local`), `-PversionName` · `-PversionCode` 는 버전 값, `-PsourceRevision` 은 정보 화면의 커밋 표시입니다.
- 릴리스 서명은 `KEYSTORE_FILE` · `KEYSTORE_PASSWORD` · `KEY_ALIAS` · `KEY_PASSWORD` 네 값을 `-P` 나 환경변수로 **모두** 넘겼을 때만 적용됩니다. 하나라도 없으면 서명하지 않은 `app-release-unsigned.apk` 가 나오며 로컬 빌드는 실패하지 않습니다.

## 릴리스 (정식 배포)

CI(`.github/workflows/android.yml`)가 만듭니다. 정식 릴리스는 `AgvCfgManager.apk`, `AgvCfgManager.apk.sha256`, `AgvCfgManager.json`(업데이트 매니페스트) 세 파일을 붙여 Latest 로 올립니다.

**한 번만 준비:** 키를 저장소 밖에서 만듭니다.

```
keytool -genkeypair -v -keystore agv-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias agvcfg
```

GitHub 저장소 Settings → Secrets and variables → Actions 에 다음 네 개를 등록합니다.

| 이름 | 값 |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 agv-release.jks` 의 출력 전체 |
| `KEYSTORE_PASSWORD` | keystore 비밀번호 |
| `KEY_ALIAS` | 키 별칭 (위 예에서는 `agvcfg`) |
| `KEY_PASSWORD` | 키 비밀번호 |

**릴리스 절차:**

1. `app/build.gradle.kts` 의 기본 `versionName` 을 새 버전(예: `0.1.0`)으로 맞추고 main 에 머지합니다.
2. 태그를 밀거나 수동 실행합니다.
   - `git tag v0.1.0 && git push origin v0.1.0`
   - 또는 Actions → android → Run workflow → `release` 체크.
3. CI 가 태그(또는 기본 versionName)와 `app/build.gradle.kts` 의 기본 versionName 이 다르면 빌드 전에 실패합니다. secrets 가 없어도 명확한 메시지와 함께 실패합니다.
4. 확인: `curl -sL https://github.com/Leepy0/AGV_CFG_Manager/releases/latest/download/AgvCfgManager.json` 에 `version` · `sha256` 이 있고, 받은 APK 크기가 `size` 와 같아야 합니다.

`versionCode` 는 `X*10000 + Y*100 + Z` 입니다(0.1.0 → 100, 1.2.3 → 10203). Y · Z 는 0~99 여야 합니다.

**main 푸시**마다 `dev` 프리릴리스(`AgvCfgManager-dev.apk`)가 덮어 올라갑니다. secrets 가 있으면 release 키로 서명하고, 없으면 디버그 키로 서명합니다. 디버그 키 APK 는 정식 APK 로 덮어 설치되지 않습니다.

**서명 키 보관 주의**

- 같은 키로 계속 서명해야 기존 휴대폰에 덮어 설치됩니다. 키나 비밀번호를 잃으면 모든 기기에서 앱을 지우고 다시 설치해야 합니다(패키지명이 같아도 서명이 다르면 설치되지 않습니다).
- `*.jks` · `*.keystore` 는 `.gitignore` 로 막혀 있지만, 파일을 저장소 밖에 두세요. 담당자 두 명 이상이 오프라인 사본을 가지는 것을 권합니다.
- Secrets 값을 메신저나 문서에 옮겨 적지 마세요. 바꿀 때는 같은 이름으로 다시 등록합니다.

## 지원 브리지 버전

펌웨어 **0.5.0 ~ 0.5.5** 를 지원합니다(기준 0.5.5). 구버전은 출력으로 기능을 감지하므로 설정 · 읽기 · 적용이 그대로 동작합니다. 브리지에 요청한 변경은 [docs/BRIDGE_CHANGE_REQUESTS.md](docs/BRIDGE_CHANGE_REQUESTS.md) 를 참고합니다.

## 현장 확인 (실기)

이 저장소의 자동 테스트는 하드웨어 없이 돕니다. 실제 보드와 휴대폰에서 확인할 항목은 [계획서 10.4](docs/DEVELOPMENT_PLAN.md#104-실기-체크리스트-단계별-실기-체크-에서-수행) 에 있고, 아직 수행하지 않은 항목은 [11장 "현황"](docs/DEVELOPMENT_PLAN.md) 에 정리합니다.
