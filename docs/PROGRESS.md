# 진행 기록

마지막 갱신: 2026-10-17 (Phase 6 완료) · 브랜치 `ccr-558b4c2a-30eszy` · 기준 브리지 0.5.5

## 진행 방식

계획서(`docs/DEVELOPMENT_PLAN.md`) 11장의 Phase 를 순서대로, Phase 마다 Haiku 서브에이전트 1개가 구현 · 테스트 · 커밋하고 오케스트레이터가 보고를 취합해 푸시했습니다. 공통 지시: CLAUDE.md 준수, 브리지 저장소는 읽기만, 계획서와 소스가 다르면 소스 기준으로 계획서 정정, 결정은 `docs/DECISIONS.md` 에 기록, 검증 명령은 아래.

```
LANG=C.UTF-8 LC_ALL=C.UTF-8 ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

(로캘 지정 필수 — 한글 테스트 이름이 만드는 클래스 파일 경로 때문에 ASCII 로캘에서는 컴파일이 깨짐. CI(ubuntu)와 Android Studio 는 UTF-8 이라 문제 없음.)

## Phase 별 현황

| Phase | 내용 | 커밋 | 테스트(core/app) | 상태 |
|---|---|---|---|---|
| 0 | Gradle 멀티모듈(`:core` JVM · `:app` Android), Compose M3 빈 화면, 테마 토큰, CI 뼈대(dev prerelease) | 049679f | 3 / – | 완료 |
| 1 | `:core` 모델 · 파서 · 검증 · ApplyWorkflow · BatchCoordinator · FakeBridgeChannel, fixtures 21개 | b2a2068 | 135 / – | 완료 |
| 2 | BLE 채널(GattQueue · 스캔 · 8장 지침) · BridgeSession · 보드 탭 · 연결 칩 · 진단 콘솔/상태 카드 · 가짜 브리지 모드 | e1c21bf | 135 / 46 | 완료 |
| 3 | 설정 폼 · 적용 워크플로(진행 패널 · 결과 카드) · 이력 저장 · `withExclusive` · `reboot()` | dbfb71e | 135 / 78 | 완료 |
| 4 | 프로필(CRUD · IP 규칙 · 내보내기/가져오기 · 휴대폰 Wi-Fi 에서 가져오기) · SSID 라이브러리(Keystore 암호화) · 자동 채움 · 연속 세팅 · 이력 탭 | 341b1b4 | 154 / 102 | 완료 |
| 5 | 빠른 명령 · 게이트웨이 도달 확인 · 공장 초기화 · QR · 앱 설정 · 로그 파일 · 정보 · 출장 준비 · 세션 요약 · NavigationSuiteScaffold | 156301b | 163 / 125 | 완료 |
| 6 | 앱 내 업데이트(매니페스트 · 폴백 · 받기 · sha256 · 설치) · 릴리스 서명 · 정식 릴리스 CI · README 사용법 | 이 Phase 커밋 | 213 / 125 | 완료 (실기 12 미검증) |
| 7 | USB OTG 시리얼 (선택): 콘솔 채널 · 보드 목록 USB 행 · 로그인 없는 연결 | 이 Phase 커밋 | 217 / 125 | 완료 (실기 미검증) |

전 Phase 에서 `allWarningsAsErrors=true` 로 경고 0, `:app:assembleDebug` 성공. 모든 테스트는 JVM(가짜 브리지)이며 **실기 · 에뮬레이터 검증은 하나도 하지 않았습니다**.

## Phase 6 현황

- 구현: `:core` `update/`(매니페스트 · 버전 · `releases/latest` 태그 폴백 · 받기 · 상태 기계, 테스트 50개), `:app` `update/`(HttpURLConnection · FileProvider 설치 · 알 수 없는 앱 설치 안내), 정보 화면 업데이트 섹션, 시작 3초 뒤 자동 확인(스낵바 한 줄), 설정 "준비 중" 제거.
- 서명: `KEYSTORE_FILE` 등 네 값이 모두 있을 때만 release 서명. 미서명 release 빌드 성공. 임시 테스트 키로 서명 빌드 후 `apksigner verify` 통과(키는 저장소 밖).
- CI: PR · main 은 테스트 + 디버그 빌드, main 은 dev 프리릴리스(secrets 없으면 디버그 키), `v*.*.*` 태그 또는 `release=true` 는 정식 Release(`AgvCfgManager.apk` · `.sha256` · `.json`). 매니페스트 생성 스크립트는 임시 git 저장소에서 시험했다. **워크플로 자체는 GitHub 에서 아직 한 번도 돌지 않았다.**
- 정식 `v0.1.0` 은 만들지 않았다(secrets 없음). 절차는 README "릴리스" 절. 계획과 다른 점은 계획서 11장 "현황" 과 DECISIONS.md.
- 남은 결정: 패키지명 확정(계획 14장 7, 정식 릴리스 전 필수). 본사 프로필 값(14장 5).

## 취합 검증 (오케스트레이터, Phase 6 뒤)

`LANG=C.UTF-8 LC_ALL=C.UTF-8 ./gradlew :core:cleanTest :core:test :app:cleanTestDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` → EXIT 0, core 213 / app 125 테스트 실패 0, `app-debug.apk` 생성(약 11.5MB). 커밋 메시지에 트레일러 · 모델 이름 없음, keystore · local.properties 미추적 확인. Actions 실행 · 실기 검증은 하지 않음.

## 계획서에서 소스 기준으로 정정한 것 (Phase 1)

- 설정 키는 17개가 아니라 **16개**.
- 브리지 `parse_ip()` 는 앞자리 0(`192.168.010.1`)을 받아들임. 앱 검증은 입력 실수 방지로 거부(앱 정책).
- BLE 줄 한도 **199B**(`CMD_LINE_MAX` 200 − NUL).
- BLE 이름 끝 4자리 = STA MAC 끝 2바이트 (샘플 MAC 을 맞춤).
- `setup` 응답에 `off`/`opening`/`unavailable` 변형 추가.

## 미구현 · 보류

- F-E10 휴대폰 Wi-Fi 스캔, F-P7 프로필별 요약, F-W3 생체 인증 비밀번호 보기, F-W4 브리지 Wi-Fi 스캔(BR-4 대기), Phase 7 USB OTG.
- BR-1(`cfg json`) 경로는 폴백 구조만(브리지 미반영).
- 디자인 체크리스트 부분 미달: 출장 준비 · 정보 화면의 로딩/빈 상태, 게이트웨이 확인 · 공장 초기화의 진행 표시.
- 프로필의 BLE 비밀번호는 `profiles.json` 에 평문(계획 B-1 대로). Keystore 로 감쌀지 결정 필요.
- `network_security_config` 가 cleartext 를 base-config 로 허용(게이트웨이 HEAD 용). 범위를 좁힐지 검토.
- Gradle wrapper `distributionSha256Sum` 미기재.
- 사내 개발 PC 에서 빌드하려면 `services.gradle.org` · `dl.google.com` · `repo.maven.apache.org` · `plugins.gradle.org` 접근이 필요(CLAUDE.md 의 "github.com 만" 가정과 충돌 — 미러 또는 허용 목록 결정 필요).

## PC 원격 세션에서 이어서 할 일

1. `git fetch origin ccr-558b4c2a-30eszy` 후 체크아웃. `main` 이 아직 없으므로 이 브랜치로 `main` 을 만들 것.
2. Android Studio 로 열기(AGP 8.13.2 · Kotlin 2.2.21 · compileSdk 35 · JDK 17/21). `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug` 로 기준선 확인.
3. **실기 체크리스트**(계획서 10.4) 1~12 를 브리지 보드(0.5.5)와 함께 수행. 특히: 스캔/연결/MTU/CCCD, 청크 쓰기, 133 재시도, 비밀번호 5회 끊김(브리지의 한도는 "한 연결 안" 이고 앱은 주소별로 세므로 동작 차이 확인), 재부팅 뒤 주소 재연결, 응답 유휴 250ms 가 느린 폰에서 충분한지(설정에서 150~800ms 조정), Keystore 암복호, SAF/FileProvider 공유, QR 카메라, 게이트웨이 HEAD, 공장 초기화 후 재연결(30초 창), 회전 · 다크 · Android 8/10/12/14 권한 흐름.
4. Phase 6 결과 확인 뒤 정식 릴리스 준비: 서명 keystore 생성 · GitHub Secrets(`KEYSTORE_BASE64` · `KEYSTORE_PASSWORD` · `KEY_ALIAS` · `KEY_PASSWORD`) 등록 · 패키지명(`kr.agv.cfgmanager` 임시) 확정 · `v0.1.0` 릴리스. 키 분실 시 전 기기 재설치이므로 오프라인 보관.
5. 본사 프로필 기본값(DNS · 서브넷 · 서버 IP)을 `ProfileTemplate` 초기 템플릿에 반영.
6. 브리지 저장소에 `docs/BRIDGE_CHANGE_REQUESTS.md` 의 BR-1 · BR-8 (높음)부터 이슈로 등록.

## 환경 메모 (이 클라우드 세션)

- Android SDK: `/opt/android-sdk` (platforms;android-35, build-tools;35.0.0), `local.properties` 는 gitignore.
- JDK 21, Gradle wrapper 8.14.3, Maven Central 429 가 가끔 나면 재시도.
