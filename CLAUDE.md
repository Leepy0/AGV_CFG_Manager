# AGV CFG Manager — 작업 규칙

ESP32-S3 AGV Wi-Fi 브리지의 호기 설정 · 펌웨어 굽기 · 진단용 Windows(WPF, .NET 10) 프로그램. **지금은 계획 단계**이며 코드는 아직 없습니다.

## 먼저 읽을 것 (순서대로)

1. `docs/DEVELOPMENT_PLAN.md` — 0장(작업 방식) → 1 · 2장 → 10장(단계). 3장은 구현 중 레퍼런스.
2. `docs/BRIDGE_CHANGE_REQUESTS.md` — 브리지에 요청한 변경. 앱은 미반영 상태로도 동작해야 함.
3. 화면 작업 전: 스킬 `field-app-ui-design`. 빌드 · 배포: `wpf-build-release`. 자동 업데이트: `dotnet-self-update`.

## 브리지 저장소

- 참조 소스: `Leepy0/AGV_Wifi_Bridge` (이 환경에서는 `../AGV_Wifi_Bridge`). **읽기만** 하고 수정하지 않는다. 변경이 필요하면 `docs/BRIDGE_CHANGE_REQUESTS.md` 에 항목을 추가한다.
- 계획서 3장과 브리지 소스가 다르면 소스가 맞다. 계획서를 고치고 커밋에 적는다.
- 기준 브리지 버전 0.5.5. 구버전(0.5.0~0.5.4, <0.5.0)도 지원해야 하며 기능 유무는 출력으로 감지한다.

## 구현 규칙

- 로직은 전부 `src/AgvCfgManager.Core`(WPF 비의존)에, UI 는 `src/AgvCfgManager` 에. Core 는 xUnit 으로 테스트한다.
- 하드웨어 없이 돌릴 수 있게 `FakeBridgeChannel` 을 먼저 만들고, 앱은 `--fake` 로 실행 가능해야 한다.
- 비동기: UI 스레드에서 `.Result` / `.Wait()` 금지. 긴 작업은 `IProgress<T>` + `CancellationToken`.
- 비밀번호 · 사내 IP 를 로그 · 테스트 fixture · 스크린샷에 남기지 않는다(예시 IP 는 `192.168.10.x`).
- 외부 네트워크는 github.com 만 가정한다. `api.github.com` 에 의존하는 코드는 쓰지 않는다.
- 한국어 UI · 주석 · 문서, 영어 식별자. 간격 · 폰트 · 색은 `Themes/Tokens.xaml` 토큰만 사용.
- 코드 주석 · 커밋에 모델 이름을 넣지 않는다.

## 단계 완료 절차

1. 해당 Phase 의 완료 기준을 전부 확인한다 (계획서 10장).
2. `dotnet test` 통과, `dotnet build -c Release` 경고 없이.
3. 결정한 것이 있으면 `docs/DECISIONS.md` 에 한 줄.
4. 커밋 메시지: 한국어 요약 한 줄(무엇을 · 왜) + 필요하면 본문. 푸시.

## 빌드 · 실행

```
dotnet build
dotnet test
dotnet run --project src/AgvCfgManager -- --fake      # 가짜 브리지로 UI 실행
dotnet publish src/AgvCfgManager -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true
```
