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
