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
