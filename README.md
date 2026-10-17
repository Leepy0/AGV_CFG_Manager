# AGV CFG Manager

ESP32-S3 AGV Wi-Fi 브리지([AGV_Wifi_Bridge](https://github.com/Leepy0/AGV_Wifi_Bridge))의 **호기 설정 · 펌웨어 굽기 · 진단**을 한 화면에서 하는 Windows 프로그램입니다.

> 현재 **계획 단계**입니다. 코드는 아직 없습니다.

- 호기 번호 하나만 입력하면 현장 프로필(SSID · DNS · 서버 …)과 IP 규칙으로 나머지를 채웁니다.
- SSID ↔ 비밀번호, 현장 프로필, 작업 이력(MAC ↔ 호기)을 저장해 다시 씁니다.
- USB 시리얼로 `set` → `save` → `reboot` → `wifi: UP` 확인까지 자동. 여러 대 연속 세팅.
- esptool 로 굽기(전체 · 파티션 유지 · 앱만), 전체 굽기 때 설정 백업 · 복원, 최신 펌웨어 캐시(오프라인 출장 대비).
- (선택) 게이트웨이 PC 에서 UDP 원격 설정, Windows BLE.

| 문서 | 내용 |
|---|---|
| [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) | 개발 계획서: 목표 · 시나리오 · 브리지 인터페이스 레퍼런스 · 기능 명세 · 화면 · 아키텍처 · 워크플로 · 테스트 · 단계 · 배포 |
| [docs/BRIDGE_CHANGE_REQUESTS.md](docs/BRIDGE_CHANGE_REQUESTS.md) | 브리지 펌웨어에 요청하는 변경 (앱은 미반영 상태로도 동작) |
| [CLAUDE.md](CLAUDE.md) | 개발 작업 규칙 |

기술: WPF · .NET 10 · self-contained 단일 exe · 사내망 자동 업데이트(github.com 매니페스트).
