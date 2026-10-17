# 결정 기록

구현 중 내린 결정을 한 줄씩 남깁니다. 계획서(DEVELOPMENT_PLAN.md) 와 다르게 정한 것은 이유를 함께 적습니다.

| 날짜 | 항목 | 결정 | 이유 |
|---|---|---|---|
| 2026-10-17 | 패키지명 | `kr.agv.cfgmanager` 를 임시로 사용 | 계획 14장 7. 회사 도메인이 정해지면 바꾸되, 바꾸면 기존 설치본 업데이트가 깨지므로 정식 배포(Phase 6) 전에 확정 |
| 2026-10-17 | Gradle · AGP · Kotlin | Gradle 8.14.3(wrapper), AGP 8.13.2, Kotlin 2.2.21 | 로컬 Gradle 8.14.3 과 호환되는 최신 AGP 8.x. AGP 9 는 Gradle 9 와 내장 Kotlin 전환이 필요해 보류 |
| 2026-10-17 | Compose BOM · AndroidX | Compose BOM 2025.06.01, activity-compose 1.10.1, lifecycle 2.9.4, navigation-compose 2.9.8 | BOM 2025.12 · 2026.x 와 activity 1.13 은 compileSdk 36~37 과 AGP 9.1 을 요구해 compileSdk 35 를 유지하려고 한 단계 낮춤. compileSdk 36 으로 올리면 재검토 |
| 2026-10-17 | compileSdk · targetSdk · minSdk | compileSdk 35, targetSdk 35, minSdk 26 | 계획 1.4 · 14장 8. 로컬 SDK 에 platform-35 만 설치되어 있음 |
| 2026-10-17 | JDK · 바이트코드 타깃 | 바이트코드 타깃 17 고정, `jvmToolchain` 자동 설치는 쓰지 않음 | CI 는 JDK 17, 로컬은 JDK 21. toolchain 자동 다운로드는 사내망 · 오프라인에서 실패할 수 있음 |
| 2026-10-17 | 빌드 값 | `-Pchannel`(기본 local), `-PversionName`(기본 0.1.0), `-PversionCode`(기본 100) | 계획 12장. 0.1.0 의 versionCode 는 공식 `X*10000+Y*100+Z` 로 100 이 맞음 (12장 매니페스트 예시의 `versionCode: 10` 은 공식과 어긋나 예시 오류로 봄) |
| 2026-10-17 | 테마 | dynamic color 끔, 시스템 다크 모드를 따름, 색은 `ui/theme/Color.kt` 한 곳 | 계획 5.7. 현장 화면 색을 고정 |
| 2026-10-17 | 아이콘 | `material-icons-core` 만 사용 | `material-icons-extended` 는 크기가 커서 쓰지 않음. 탭에 필요한 4개는 core 에 있음 |
| 2026-10-17 | 탭 상태 | NavHost 4탭, `popUpTo(startDestinationId)` + `saveState` · `restoreState` · `launchSingleTop` | 탭 전환 시 화면 상태 보존. `findStartDestination` 대신 `startDestinationId` 를 써서 별도 import 를 줄임 |
| 2026-10-17 | 경고 정책 | `:core` · `:app` 모두 `allWarningsAsErrors = true` | CLAUDE.md "새 경고 없이" 를 빌드가 강제 |
| 2026-10-17 | CI | PR 은 테스트 · 디버그 빌드만. main 푸시는 `dev` prerelease 를 덮어써 `AgvCfgManager-dev.apk` 첨부. 정식 릴리스는 Phase 6 | 계획 11장 Phase 0 · 12장 |
| 2026-10-17 | 간격 · 글꼴 · 모양 토큰 | `AgvSpacing`(4/8/12/16/24/32dp), `AgvTypography`, `AgvShapes` 를 `ui/theme/Tokens.kt` 한 곳에 | 화면 코드의 dp · sp · 색 하드코딩 금지 |
| 2026-10-17 | 설정 키 개수 | 16개 (계획 3.1 제목의 "17개" 는 오기) | 브리지 `app_config.c apply()` 의 키 분기는 16개이고 7.1 전송 순서와 같다. 계획 3.1 · F-E1 을 16개로 고침 |
| 2026-10-17 | IP 앞자리 0 | 앱은 거부, 브리지는 받아들임 | 브리지 `parse_ip()` 는 `sscanf("%3u.…")` 라 `010` 을 10진 10으로 읽는다(값 불변). 앱은 입력 실수를 막으려고 거부한다. 계획 3.1 의 "브리지도 거부" 설명을 소스 기준으로 고침 |
| 2026-10-17 | 0.0.0.0 입력 | ip · mask · server · dns · dns2 · rfid_dst 에서 앱은 거부 | 브리지는 `0.0.0.0` 을 dhcp · auto · off 와 같은 뜻으로 받는다. 되읽기 표기와 어긋나지 않도록 앱은 해당 키워드를 쓰라고 안내한다. gw 의 0.0.0.0 은 허용 |
| 2026-10-17 | BLE · 콘솔 줄 한도 | BLE 199바이트, 콘솔 160바이트(인용 포함, UTF-8 바이트 기준) | 브리지 `sble_take_line` 은 `CMD_LINE_MAX`(200) − NUL 까지만 복사하고 나머지를 조용히 버린다. 계획의 200 은 199 로 고침. 콘솔 `max_cmdline_length` 도 바이트로 센다 |
| 2026-10-17 | 비밀번호 표현 | `BridgeConfig.pass == null` 은 "유지", 장치 값은 `passLength` 만 | 장치는 비밀번호를 되읽지 않는다(3.1). 입력이 있으면 장치와 비교할 수 없으므로 변경으로 본다. 빈 입력은 보내지 않는다(7.1) |
| 2026-10-17 | 되읽기 판정 | 키 값 표준형 비교만 사용, `* differs` 줄은 판정에 쓰지 않음 | 스냅샷이 오래되면 편집 사본과 실행 설정의 차이가 달라져 거짓 실패가 난다. 값 대조로 충분하다 |
| 2026-10-17 | 취소 시점 | SendSet · ReadBack 에서만 취소. 저장 이후는 NonCancellable | 계획 7.1 의 "저장 전까지만 활성". 취소 요청은 Finished(Cancelled) 로 알리고 CancellationException 을 다시 던진다 |
| 2026-10-17 | 재시도 정책 | 명령 시간 초과는 1회 재전송, 연결 끊김은 재연결 후 SendSet 부터 최대 2회 | 계획 7.1 의 "재연결 후 처음부터". 저장 · 재부팅 단계의 끊김은 실패로 끝낸다(저장 여부를 확실히 모르기 때문) |
| 2026-10-17 | 광고 이름 · 샘플 MAC | 이름 끝 4자리 = STA MAC 끝 2바이트. 샘플 MAC 을 `a1:b2:c3:d4:a1:b2` 로 고침 | 펌웨어 `setup_prepare_wifi()` 는 `mac[4]`,`mac[5]` 를 쓴다. 계획 샘플의 MAC `…:e5:f6` 과 이름 `A1B2` 가 어긋나 있었다 |
| 2026-10-17 | 응답 끝 판정 | 명령별 `ReplyRule` (한 줄 · 접두어 줄 뒤 유휴 · 유휴만 · 콘솔 프롬프트), 유휴 250ms · 최대 3초 | 계획 3.2.1 · 6.2. 시간은 코루틴 시간을 써서 `runTest` 가상 시간으로 검증. BR-8 이 반영되면 규칙만 바꾸면 된다 |
| 2026-10-17 | 프로필 BLE 비밀번호 | 가져올 때 없으면 빈 값 (기본값 `agvsetup` 을 앱에 넣지 않음) | 내보내기 파일이 비밀번호를 뺀 상태일 수 있다. 빈 값이면 사용자가 입력하도록 상위 계층이 막는다 |
| 2026-10-17 | cfg json (BR-1) | 미구현. `{` 로 시작하면 `JsonConfigReader`(항상 null) 를 거쳐 텍스트 파서로 폴백 | 브리지에 아직 없다. 구현 자리와 폴백 구조만 둔다 (Phase 1 지시) |
| 2026-10-17 | JSON 보존 방식 | `@Serializable` 대신 `JsonObject` 직접 조립 · 분해, 모르는 키는 `extras` 에 보관 | 계획 6.3 의 모르는 필드 보존 요구. kotlinx `ignoreUnknownKeys` 는 보존하지 못한다 |
| 2026-10-17 | 가짜 채널 문법 | `ChannelKind.Fake` 는 BLE 문법(따옴표 없음, CRLF, 청크 전송)을 따른다 | 계획 10.2. 하드웨어 없이 BLE 경로를 재현하는 것이 목적이다 |
| 2026-10-17 | 테스트 도구 · 환경 | `kotlinx-coroutines-test` 를 테스트 전용으로 추가. 한글 테스트 이름 때문에 빌드는 UTF-8 로캘이 필요(`LANG=C.UTF-8`) | 가상 시간 테스트를 위해서다. 한글 메서드 이름은 JVM 클래스 파일 이름이 되므로 파일 시스템 인코딩이 UTF-8 이어야 한다. CI(ubuntu) 는 기본이 UTF-8 |
| 2026-10-17 | 세션 위치 | `BridgeSession` · `ConsoleLog` · `BoardSource` 를 `session/` 패키지에 둠 (계획 6.1 목록에 없음) | 화면과 BLE 가 함께 쓰고 JVM 에서 테스트해야 해서 Android 의존이 없는 곳에 둠. 계획 6.1 구조 표는 다음 Phase 에서 함께 고친다 |
| 2026-10-17 | 가짜 브리지 저장 | SharedPreferences `agv_settings` 한 키 (DataStore 는 Phase 3~4 에서 설정이 늘 때 도입) | 값이 하나뿐이라 의존성을 더하지 않음. 릴리스 빌드는 `BuildConfig.DEBUG` 로 가짜 모드를 막는다 |
| 2026-10-17 | 연결 3단계 표시 | 채널의 `waitReady` 가 연결 · 로그인을 한 번에 하므로 채널이 "연결 / 로그인" 단계를 알리고 세션이 "읽기" 를 알림. 가짜 모드는 로그인 단계를 따로 알리지 않음 | `IBridgeChannel` 계약(Phase 1)을 바꾸지 않으려고 |
| 2026-10-17 | 비밀번호 남은 횟수 | BLE 채널은 틀리면 링크를 끊고, 시도마다 새로 연결한다. 남은 횟수는 세션이 주소별 실패 수로 센다(5 - n). 다섯 번째는 `TooManyFailures` | 브리지의 5회 제한은 한 연결 안의 횟수다. 같은 연결에서 재시도하는 구조보다 단순하고, 실기 체크 8 에서 브리지 응답과 맞는지 확인해야 한다 |
| 2026-10-17 | 연결 안의 로그인 기록 | 채널 내부 로그인은 세션이 `login ***` 를 콘솔에 먼저 남긴다. 값은 `ConsoleLog` 가 가린다 | 계획 F-D1 "앱이 보내는 명령도 모두 표시". 채널 `raw` 흐름은 IBridgeChannel 규약대로 마스킹하지 않는다 |
| 2026-10-17 | 재연결 판정 | 재부팅 · 예기치 않은 끊김은 30초 동안 주소로 다시 연결(시도당 8초). `setup off` 는 자동 재연결하지 않는다 | 설정 모드를 닫으면 보드가 광고를 멈추므로 30초 재시도는 의미가 없다 |
| 2026-10-17 | 재스캔 병행 | 계획 8.5 의 "재스캔 + getRemoteDevice 병행" 대신 순차로 구현: 직접 연결 실패 → 주소 광고를 최대 6초 기다림 → 다시 연결. GATT 133 은 500ms 간격 3회 후 실패 | 두 경로를 동시에 돌리면 스캔 시작 횟수(30초 5회)를 넘기기 쉬워 순차로 두었다. 실기에서 재연결이 느리면 병행으로 바꾼다 |
| 2026-10-17 | 5분 유휴 | 사용자 명령 · 연결 · 수동 새로고침만 활동으로 셈. 자동 5초 새로고침은 셈하지 않음 | 자동 새로고침이 켜져 있어도 방치된 연결은 끊기도록 |
| 2026-10-17 | 스캔 간격 | 6초 간격 검사를 두 곳에 둔다: 뷰모델은 사용자에게 안내, 스캐너는 실제 시작을 지연 | 뷰모델만 막으면 재부팅 뒤 주소 찾기 경로가 제한을 넘는다 |
| 2026-10-17 | 콘솔 공유 · 빠른 명령 | 공유는 화면에 보이는 원문(이미 가려진 값)만 보낸다. 빠른 명령 버튼(F-D3)은 Phase 5 로 미룸 | 비밀번호가 공유 파일에 들어가지 않게 하고, 계획 11장 Phase 5 범위를 지킴 |
| 2026-10-17 | 테스트 의존성 | `androidx.compose.ui:ui-test-junit4`, `ui-test-manifest`(debug), `kotlinx-coroutines-android`, `lifecycle-runtime-compose` 추가. 이 환경에서는 `--offline` 으로 해석되지 않아 온라인으로 빌드 | Compose 테스트는 작성만 하고 실행하지 않는다(에뮬레이터 없음). 의존성은 카탈로그에 고정 버전으로 둠 |
| 2026-10-17 | 보드 탭 빈 상태 · 이력 표식 | 이력 표식(`이력: n호기`)은 Phase 4 의 이력 저장 뒤에 붙인다. 지금은 이름의 호기 번호만으로 "설정 없음(01)" / "N호기 설정됨" 을 보인다 | 이력 파일이 아직 없다 |
| 2026-10-17 | 적용 실행 위치 | `ApplyRunner`(앱 수명 scope, `UNDISPATCHED` 시작)가 `BridgeSession.withExclusive` 안에서 `ApplyWorkflow` 를 만든다. 진행 · 결과는 Runner 가 쥔다 | 회전 · 탭 전환에도 진행 패널을 복원하고, 워크플로는 채널마다 만들어야 하므로 AppContainer 에 싱글턴으로 둘 수 없다 |
| 2026-10-17 | 적용 중 세션 규칙 | `withExclusive` 동안 콘솔 · 상태 새로고침 · 다른 보드 연결 · 끊기 · 자동 재연결 · 유휴 끊김을 막는다. 재연결 · 읽기는 워크플로(WaitReady · VerifyWifi · 끝의 읽기)가 맡고 세션은 중복 `stat`/`cfg` 를 보내지 않는다 | 같은 채널에 두 흐름이 동시에 명령을 보내면 응답이 꼬인다(계획 6.4 · 7.1) |
| 2026-10-17 | 콘솔 `reboot` · `setup off` | 줄을 보고 상태를 바꾸지 않고 `session.reboot()` · `session.closeSetupMode()` 로 넘긴다 | 재부팅 상태 전이와 재연결 시작을 한 곳에서만 처리한다. 콘솔 사용성은 그대로 둔다 |
| 2026-10-17 | 설정 폼 추가 검사 | 계획 F-E3 의 "IP 가 GW · 서버와 같지 않음(오류)", "마지막 옥텟 0/255(경고)" 는 `ConfigForm`(앱) 에 둔다. `:core` 검증 코드 enum 은 바꾸지 않음 | `ConfigValidator` 는 브리지 `apply()` 규칙을 그대로 옮긴 것이라, 앱 정책은 따로 둔다 |
| 2026-10-17 | 호기 번호 · IP 모드 | 호기 번호 변경은 `SettingsViewModel.setAgv` 한 곳으로 모은다. IP 모드 "규칙" 은 비활성 자리(Phase 4 프로필 전) | Phase 4 의 IP 재계산 · 이력 제안을 이 한 곳에 붙이면 된다 |
| 2026-10-17 | 비밀번호 보기 · −/+ | 보기 · 숨기기 는 글자 버튼, −/+ 도 글자 버튼 | `material-icons-core` 에 Visibility · Remove 가 없다(`material-icons-extended` 는 크기 때문에 쓰지 않음). 글자가 있으면 아이콘이 없어도 뜻이 전달된다 |
| 2026-10-17 | 이력 기록 범위 | 성공 · 실패 · 취소를 기록한다. 바뀐 값이 없는 적용(`NoChange`)은 장치에 아무 명령도 나가지 않으므로 기록하지 않는다. `저장만` 은 `action=apply` + `note` 로 구분 | 부록 B-3 의 `action` 값 목록(`apply`·`defaults`)을 넘기지 않는다 |
| 2026-10-17 | 프로필 · 라이브러리 자리 | 프로필 주입은 `SettingsViewModel.applyProfileValues`, 프로필 · 비밀번호 저장 체크는 Phase 4 로 미룸. 디버그 가짜 시나리오 선택(Wi-Fi 끊김 · busy 등)은 만들지 않음 | Phase 3 범위. 시나리오는 `ApplyFlowTest` 가 `FakeDeviceOptions` 로 대신 검증한다 |
