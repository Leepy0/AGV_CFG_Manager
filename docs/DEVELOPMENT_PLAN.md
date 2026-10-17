# AGV CFG Manager 개발 계획서

ESP32-S3 AGV Wi-Fi 브리지([AGV_Wifi_Bridge](https://github.com/Leepy0/AGV_Wifi_Bridge))의 **호기 설정 · 펌웨어 굽기 · 진단**을 GUI 한 화면에서 끝내는 Windows 데스크톱 프로그램 계획서입니다.

- 문서 버전: 1.0 (2026-10-17) · 기준 브리지: **0.5.5** (`CMakeLists.txt` `PROJECT_VER`, DNS 키 포함) · 게이트웨이 0.3.0
- 독자: **이 프로그램을 구현할 개발 담당(AI 포함)**. 브리지 소스를 다시 분석하지 않아도 되도록 앱이 의존하는 브리지 사실을 3장에 모두 옮겨 적었습니다. 3장과 브리지 소스가 다르면 **소스가 맞습니다**. 그때는 이 문서를 고치세요.
- 함께 볼 문서: [BRIDGE_CHANGE_REQUESTS.md](BRIDGE_CHANGE_REQUESTS.md)(브리지에 요청할 변경), 저장소 루트의 `CLAUDE.md`(작업 규칙)
- 사용할 스킬: `field-app-ui-design`(모든 화면 작업 전), `wpf-build-release`(빌드·배포), `dotnet-self-update`(자동 업데이트)

## 목차

0. [읽는 순서와 작업 방식](#0-읽는-순서와-작업-방식)
1. [배경 · 목표 · 범위](#1-배경--목표--범위)
2. [사용 시나리오](#2-사용-시나리오)
3. [브리지 인터페이스 레퍼런스 (앱이 의존하는 사실)](#3-브리지-인터페이스-레퍼런스-앱이-의존하는-사실)
4. [기능 명세](#4-기능-명세)
5. [화면 설계](#5-화면-설계)
6. [아키텍처 · 데이터 모델](#6-아키텍처--데이터-모델)
7. [워크플로 상세](#7-워크플로-상세)
8. [오류 · 메시지 표](#8-오류--메시지-표)
9. [테스트 전략](#9-테스트-전략)
10. [개발 단계](#10-개발-단계)
11. [빌드 · 배포 · 자동 업데이트](#11-빌드--배포--자동-업데이트)
12. [브리지 변경 요청 요약](#12-브리지-변경-요청-요약)
13. [리스크 · 결정이 필요한 사항](#13-리스크--결정이-필요한-사항)
- [부록 A. 콘솔 입출력 샘플 전문](#부록-a-콘솔-입출력-샘플-전문)
- [부록 B. 저장 파일 JSON 예시](#부록-b-저장-파일-json-예시)
- [부록 C. 용어](#부록-c-용어)

---

## 0. 읽는 순서와 작업 방식

1. 1장 → 2장으로 "무엇을, 왜" 를 잡습니다.
2. 3장은 **구현 중 수시로 찾아보는 레퍼런스**입니다. 처음엔 3.1(설정 키)과 3.2(콘솔 프로토콜)만 읽고 넘어갑니다.
3. 10장의 단계(Phase) 순서대로 구현합니다. 각 단계의 **완료 기준**을 모두 만족해야 다음 단계로 갑니다.
4. 화면을 만들기 전에 `field-app-ui-design` 스킬을 읽고, 5장의 화면 설계를 따릅니다.
5. 모호한 부분은 13장의 결정 사항을 먼저 확인하고, 거기에 없으면 **가장 단순한 쪽**을 고른 뒤 `docs/DECISIONS.md` 에 한 줄 기록합니다.
6. 하드웨어 없이도 전 기능을 돌릴 수 있도록 **가짜 브리지(FakeBridgeChannel)** 를 1단계에서 먼저 만듭니다. 실기 확인은 각 단계의 "실기 체크" 항목으로 따로 합니다.

---

## 1. 배경 · 목표 · 범위

### 1.1 지금 상황

브리지 호기 설정은 세 경로(USB 콘솔 · 휴대폰 BLE · 서버 원격 UDP)로 **같은 텍스트 명령**(`set agv 7` …)을 한 줄씩 입력합니다. 한 호기당 10개 안팎의 값을 손으로 치고, 현장마다 반복됩니다.

| 불편 | 구체적으로 |
|---|---|
| 반복 입력 | SSID · 비밀번호 · 마스크 · 게이트웨이 · DNS · 서버 IP · 포트 · 보드레이트는 **같은 현장이면 전부 같은 값**인데 호기마다 다시 침 |
| 오타 · 누락 | IP 한 자리 틀림, `save` 안 하고 `reboot`, `mask` 빠짐 → 호기가 안 붙고 원인을 찾느라 시간 소모 |
| 비밀번호 관리 | Wi-Fi 비밀번호를 기억하거나 메모에서 복사. 브리지는 비밀번호를 되읽어 주지 않음(`pass=(8 chars)`) |
| 굽기 절차 | `flash.bat COM5` 실행, 포트 찾기, esptool 설치 확인, 전체 이미지면 설정이 지워져 다시 설정 |
| 확인 | 설정 후 `stat` 으로 `wifi: UP` 을 보고, 게이트웨이 화면에서 호기가 뜨는지 따로 확인 |
| 출장 | 해외 현장은 SSID · DNS · 서버 IP 가 다름. 어떤 값을 썼는지 기록이 흩어짐 |

### 1.2 목표

1. **호기 번호 하나만 입력하면 나머지는 자동으로 채워진다.** 현장 프로필(SSID · DNS · 서버 …)과 IP 규칙(`192.168.10.{100+호기}`)에서 계산.
2. **저장할 수 있는 값은 모두 저장하고 다시 쓴다.** SSID ↔ 비밀번호 라이브러리, 현장 프로필, 작업 이력(MAC ↔ 호기).
3. **실수를 앱이 막는다.** 브리지와 같은 검증 규칙, 호기 번호 중복 경고, `save` → `reboot` → `wifi: UP` 확인까지 자동.
4. **굽기도 같은 화면에서.** 포트 자동 선택, 최신 펌웨어 자동 확인, 보드 상태에 맞는 굽기 방식 추천, 전체 굽기 때 설정 백업·복원.
5. **여러 대를 연속으로.** 다음 보드를 꽂으면 호기 번호가 +1 된 채 대기.
6. **인터넷이 없어도 된다.** 펌웨어 · 프로필을 미리 받아 두고 현장에서는 오프라인으로.

### 1.3 범위

| 포함 | 제외 (이번 계획에서) |
|---|---|
| USB 시리얼로 설정 읽기 · 쓰기 · 확인 | 메인보드(TMS320F2808) 펌웨어 OTA 조작 (게이트웨이 화면이 담당) |
| 현장 프로필 · SSID 라이브러리 · IP 규칙 · 이력 | 게이트웨이 서버의 배포 작업 · 호기 현황 대체 |
| esptool 로 굽기 (전체 · 파티션 유지 · 앱만) + 설정 백업 · 복원 | 브리지 펌웨어 자체 빌드 |
| 진단 (콘솔, Wi-Fi 상태 해석, 오류 코드 설명) | Android/iOS 앱 (13장 결정 사항 1) |
| (선택) 게이트웨이 PC 에서 UDP 원격 설정 | |
| (선택) Windows BLE 로 현장 설정 | |
| 사내망 자동 업데이트 | |

### 1.4 전제와 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 플랫폼 | **Windows 데스크톱, WPF, .NET 10 (`net10.0-windows`)** | 첫 설치는 반드시 PC 에서 USB 로 굽기 때문에 PC 앱이 전 과정을 덮음. 게이트웨이 · BridgeTest 가 C# 이라 패킷 코드를 재사용. 팀의 빌드 · 배포 · 자동 업데이트 관례가 WPF 기준 |
| 기본 연결 경로 | **USB 시리얼 콘솔** (1순위) → UDP 원격 (2순위, 게이트웨이 PC 전용) → BLE (3순위, 선택) | 콘솔이 가장 단순하고 비밀번호 없이 쓸 수 있으며 굽기와 같은 케이블 |
| 브리지 수정 | 앱은 **현재 브리지(0.5.5)와 수정 없이 동작**해야 함. 12장의 변경 요청이 반영되면 더 단순한 경로를 쓰되, 반영 전에도 텍스트 파싱으로 동작 | 호기마다 브리지 버전이 다를 수 있음 |
| 배포 | self-contained 단일 exe + 사내망 매니페스트 자동 업데이트 (`dotnet-self-update` 스킬) | 설치 없이 복사, 사내망에서 api.github.com 차단 |
| 언어 | UI · 문서 · 주석 한국어, 식별자 영어 | 브리지 저장소와 동일 |
| 앱 이름 | 프로그램 `AGV CFG Manager`, 실행 파일 `AgvCfgManager.exe`, 네임스페이스 `AgvCfgManager` | |

---

## 2. 사용 시나리오

각 시나리오의 **사용자가 손으로 하는 일**만 굵게 표시했습니다. 나머지는 앱이 합니다.

### S1. 새 보드 첫 세팅 (벤치, USB)

1. 보드를 UART 쪽 USB 에 꽂는다. 앱이 새 포트를 감지해 선택하고 연결한다.
2. 보드가 비어 있으면(부팅 로그 없음 / `bridge>` 없음) 펌웨어 탭을 열어 "전체 굽기" 를 권한다. **[전체 굽기]** → 진행률 → 재부팅 → 다시 연결.
3. 앱이 `ota` · `stat` · `cfg` 로 장치를 읽는다: fw 0.5.5, MAC, 설정 상태 `default`(저장 없음).
4. 설정 탭: 프로필 "본사 1공장" 이 이미 선택돼 있다. **호기 번호 7 입력** → IP `192.168.10.107`, SSID · 비밀번호 · 마스크 · GW · DNS · 서버 · 포트 · 보드레이트 전부 채워짐.
5. **[적용]** → `set …` 16개 → `cfg` 로 되읽어 대조 → `save` → `reboot` → 부팅 대기 → `stat` 폴링 → `wifi: UP ip=192.168.10.107`.
6. 완료 카드: "07호기 · AGVBR-07-A1B2 · 192.168.10.107 · Wi-Fi 연결됨 RSSI -52". 이력에 기록.

### S2. 여러 대 연속 세팅

1. S1 의 5 가 끝나면 **[다음 호기]**. 호기 번호가 8 로 올라가고 "다음 보드를 연결하세요" 상태.
2. **보드를 바꿔 꽂는다.** 포트 사라짐 → 나타남을 감지해 자동 연결 · 자동 읽기.
3. 이미 설정된 보드(`state=saved`)면 경고 배너("이미 12호기로 설정된 보드입니다") 와 함께 자동 채움을 멈춘다. 빈 보드면 8호기 값이 채워진 채 **[적용]** 만 누른다.

### S3. 운용 중 호기 재설정 (서버 IP 변경 등)

1. 보드에 USB 연결 → 장치 읽기 → 설정 탭에 **장치의 현재값**이 채워진다(비밀번호는 "(8자, 유지)").
2. **서버 IP 만 수정** → 변경 1건 표시 → **[적용]**. 비밀번호는 보내지 않으므로 그대로 유지된다.

### S4. 해외 현장

1. 출장 전: **[출장 준비]** → 최신 펌웨어 zip 캐시, 프로필 내보내기(JSON) 를 USB 메모리로.
2. 현장: **새 프로필 "OO공장"** 만들기 — SSID · 비밀번호 · 서브넷 · GW · DNS · 서버 IP · IP 규칙 입력. 한 번만.
3. 이후는 S1 · S2 와 같다. 이력이 프로필별로 남아 나중에 어떤 값을 썼는지 찾을 수 있다.

### S5. 펌웨어 올리기 (USB)

1. 펌웨어 탭: 장치 fw 0.5.1, 최신 0.5.5 (캐시 또는 GitHub Releases).
2. 앱이 보드 상태로 방식을 고른다: 0.5.0 미만 · `nopart` → "파티션 포함 4개 파일(설정 유지)", 0.5.x → "앱만(설정 유지)", 응답 없는 보드 → "전체 이미지".
3. 전체 이미지를 고르면: 위험 확인창 + "굽기 전 설정 백업 → 굽기 후 복원" 체크(기본 켬). 비밀번호는 라이브러리에서 가져오고 없으면 물어본다.
4. **[굽기]** → esptool 진행률 → 부팅 대기 → `ota` 로 fw 확인 → (백업했으면) S1 의 5 를 자동 실행.

### S6. 게이트웨이 PC 에서 원격 설정 (선택 기능)

1. 연결 종류 "원격(UDP)" → 호기 IP · 번호 입력. 이 PC 가 브리지의 `server` IP 여야 응답한다.
2. 같은 설정 화면에서 수정 → **[적용]** → 원격 `save` 는 **확인 대기(trial)** 로 저장되고 재부팅 뒤 180초 안에 서버 패킷이 필요하다. 앱이 새 IP 로 PING 을 보내 확정한다(게이트웨이가 켜져 있으면 게이트웨이도 자동 PING).

### S7. 문제 진단

1. "게이트웨이에 안 보여요": 진단 탭 → `stat` 해석 카드: `wifi: DOWN last_reason=201` → "AP 를 찾지 못함: SSID 철자, 2.4GHz, 채널 1~13 확인".
2. `wifi: UP` 인데 안 보이면: `rx_foreign` 증가 → "브리지 `server` 가 게이트웨이 PC IP 와 다름" 안내. 콘솔 탭에서 직접 명령도 가능.

---

## 3. 브리지 인터페이스 레퍼런스 (앱이 의존하는 사실)

출처: 브리지 저장소 `main/app_config.c`(검증 규칙), `main/console_cmd.c`(USB 콘솔), `main/cmd.c`(BLE · UDP 공용 명령), `main/net_bridge.c`(UDP), `main/setup_ble.c`(BLE), `tools/BridgeTest.cs`(UDP 패킷 C# 구현), `docs/FLASH_GUIDE.md`(굽기). 이 환경에서는 `../AGV_Wifi_Bridge` 에 체크아웃돼 있습니다.

### 3.1 설정 키 (17개)

브리지의 `set <key> <value>` 키와 검증 규칙입니다. 앱의 `ConfigValidator` 는 이 표를 **그대로** 구현합니다 (`app_config.c` 의 `apply()`).

| 키 | 뜻 | 타입 · 규칙 | 빌드 기본값 | 브리지 버전 | 프로필 자동 채움 |
|---|---|---|---|---|---|
| `agv` | 호기 번호 | 정수 1~99 | 1 | 전부 | ✗ (호기마다 입력) |
| `ssid` | Wi-Fi 이름 | 1~32 **바이트**(UTF-8), 공백 가능 | `AGV_TEST` | 전부 | ○ |
| `pass` | Wi-Fi 비밀번호 | 8~64 바이트, 또는 `-` = 없음(오픈). **되읽기 불가**(길이만) | `agv12345` | 전부 | ○ (SSID 라이브러리) |
| `ip` | 고정 IP | 10진 dotted-quad 또는 `dhcp` | (DHCP) | 전부 | ○ (IP 규칙으로 계산) |
| `mask` | 서브넷 마스크 | dotted-quad, `0.0.0.0` 불가 | `255.255.255.0` | 전부 | ○ |
| `gw` | 기본 게이트웨이 | dotted-quad (`0.0.0.0` 허용) | `0.0.0.0` | 전부 | ○ |
| `dns` | 기본 DNS | dotted-quad 또는 `auto` | `auto` | **0.5.5+** | ○ (회사 고정값) |
| `dns2` | 보조 DNS | dotted-quad 또는 `auto` | `auto` | **0.5.5+** | ○ |
| `server` | 게이트웨이 PC IP | dotted-quad, `0.0.0.0` 불가 | `192.168.0.10` | 전부 | ○ |
| `server_port` | 서버 UDP 포트 | 1~65535 | 50000 | 전부 | ○ |
| `local_port` | 브리지 수신 UDP 포트 | 1~65535 | 50000 | 전부 | ○ |
| `rfid_dst` | RFID 송신 IP | dotted-quad, `255.255.255.255` = 브로드캐스트, `off` = 안 보냄 | 브로드캐스트 | 전부 | ○ |
| `rfid_port` | RFID 송신 포트 | 1~65535 | 50001 | 전부 | ○ |
| `main_baud` | 메인보드(J4) 보드레이트 | 1200~1000000 | 115200 | 전부 | ○ |
| `rfid_baud` | RFID 리더 보드레이트 | 1200~1000000 | 9600 | 전부 | ○ |
| `hb_ms` | heartbeat 주기 ms | `0`(끔) 또는 100~2000 | 1000 | 전부 | ○ (기본 1000 고정 권장) |

- IP 는 **앞자리 0 없는 10진 표기**만 받습니다 (`192.168.010.1` 거부). 각 옥텟 0~255.
- `set` 은 **편집 사본**만 바꿉니다. `save` 로 NVS 에 쓰고 **재부팅해야 적용**됩니다. 편집 사본은 재부팅하면 사라집니다(저장 전이면 아무 영향 없음).
- 편집 사본을 되돌리는 명령은 **없습니다** (변경 요청 BR-3). 그때까지 앱은 "실패하면 그냥 `save` 를 보내지 않는다" 로 안전합니다.
- 원격(UDP)의 `save` 는 **trial**(확인 대기)로 저장됩니다. 3.3 참고.

### 3.2 USB 시리얼 콘솔 프로토콜

| 항목 | 값 |
|---|---|
| 포트 | DevKitC-1 의 **"UART" 라고 적힌 USB** (CP210x `VID_10C4&PID_EA60` 또는 CH343 `VID_1A86&PID_55D3`). "USB" 쪽(ESP32-S3 내장 USB, `VID_303A`)은 로그만 나오고 **명령 입력 불가** |
| 속도 | 115200 8N1, 흐름 제어 없음 |
| DTR/RTS | 포트를 열 때 **DTR=false, RTS=false** 로 열어야 보드가 리셋되지 않음 (자동 리셋 회로). 드라이버에 따라 열 때 잠깐 리셋되는 경우가 있으므로, 연결 직후 부팅 로그가 보이면 프롬프트 복귀까지(최대 10초) 기다린 뒤 식별을 시작한다 |
| 프롬프트 | `bridge> ` — 브리지는 `"bridge>"` 로 지정하지만 ESP-IDF `esp_console` REPL 이 뒤에 **공백 한 칸**을 붙이고, 로그 색상(`CONFIG_LOG_COLORS`, 기본 켬)이 있으면 앞뒤에 ANSI 색 escape(`\x1b[0;32m` … `\x1b[0m`)가 붙을 수 있음. 앱은 수신 스트림에서 **ANSI escape(`\x1b\[[0-9;]*[A-Za-z]`)를 먼저 제거**한 뒤 `bridge>\s*$` 로 프롬프트를 판정한다 |
| 줄 끝 | **입력은 `\n` 하나만** (`\r` 도 `\n` 으로 바뀌므로 `\r\n` 을 보내면 빈 줄이 하나 더 들어감). 출력은 `\r\n` |
| 터미널 모드 | 앱이 ESC 시퀀스 probe(`ESC[5n`)에 응답하지 않으면 0.5초 뒤 **dumb 모드**로 들어감. dumb 모드는 입력 글자를 **그대로 echo** 하고 줄 편집 · 색 없음. 앱은 probe 에 응답하지 않는다 |
| 최대 명령 길이 | 160자 |
| 인용 | 콘솔은 `argc/argv` 로 토큰을 나누므로 **공백이 있는 값은 큰따옴표로 감쌈**. 값 안의 `"` 와 `\` 는 `\"`, `\\` 로 이스케이프. 앱은 **문자열 값(ssid, pass)은 항상 따옴표로 감싼다** |
| 로그 섞임 | 명령 출력 사이에 ESP 로그(`I (12345) net: …`, `W (…) …`, 색 escape 포함 가능)가 끼어들 수 있음. ANSI 제거 후 `^[IWEDV] \(\d+\) ` 로 시작하는 줄은 명령 결과에서 제외하고 콘솔 화면에만 표시 |

**명령 실행 규약 (앱):** 명령 + `\n` 송신 → ANSI 제거한 수신 버퍼가 `bridge>` (+공백) 로 **끝날 때까지** 읽음(기본 2초, `reboot` 는 예외) → 그 사이 텍스트에서 첫 줄(echo, `bridge> cfg` 꼴)을 떼고 로그 줄을 걸러낸 나머지가 결과. 연결 직후에는 빈 줄 `\n` 을 보내 프롬프트를 한 번 받고 그 전 데이터는 버린다. 알 수 없는 명령은 ESP-IDF 가 `Unrecognized command` 로 답한다(브리지 0.5.3 이하에 `log` 를 보낸 경우 등). 인코딩은 UTF-8(공백 · 한글 SSID 바이트 그대로).

콘솔 명령과 출력 형식 (파서가 의존):

| 명령 | 출력 (정확한 형식) |
|---|---|
| `cfg` | 부록 A-1. `key=value` 토큰, 7~8줄. `ssid="…"` 는 따옴표 안에 공백 가능. `pass=(8 chars)`. 마지막에 `state=saved\|default\|trial`, 편집 사본이 실행 중 설정과 다르면 `* differs from running config - 'save' then 'reboot' to apply` 한 줄 추가 |
| `set <key> <value>` | 성공 `ok - 'save' then 'reboot' to apply` / 실패 `invalid key or value` / 인자 오류 `usage: set <key> <value>` + 키 목록 2~3줄 |
| `save` | `saved` / `save failed: <ESP_ERR_…>` |
| `reboot` | 즉시 재시작(출력 없음, 프롬프트 안 옴). 메인보드 굽는 중이면 `busy: mainboard programming - reboot refused, retry when done` 을 내고 재시작하지 않음 |
| `defaults` | `saved config erased - reboot to use build defaults` (NVS 삭제, 재부팅 시 빌드 기본값 + 설정 모드 자동 열림) |
| `stat` | 부록 A-2. `wifi: UP\|DOWN mac=… ip=… rssi=… ch=… bssid=…` / `net : tx=… rx_foreign=… disc=… last_reason=…` / `setup: …` / `dsp : …` / `main: …` / `rfid: …` / `heap: …`. **콘솔 `stat` 에는 fw 줄이 없음**(BLE · UDP 의 `stat` 에는 첫 줄로 있음) |
| `ota` | `fw=0.5.5 part=ota_0 state=valid\|pending\|busy` ← **펌웨어 버전은 여기서** |
| `setup` / `setup on` / `setup off` | `setup: off name=AGVBR-07-A1B2` 또는 `setup: on name=… ble=advertising\|connected` |
| `dsp` | 메인보드 상태 한 줄 (`state=idle\|downloading\|staged\|programming\|nopart` 포함) — `nopart` 면 구 파티션 |
| `log <none\|error\|warn\|info\|debug\|verbose>` | 런타임 로그 레벨 (0.5.4+). 앱은 연결 직후 `log warn` 으로 낮춰 출력 섞임을 줄여도 됨(저장 안 됨) |
| `mon 1\|0` | 모든 프레임 hex 덤프 — 진단 탭 전용 |
| `help` | 명령 목록 |

MAC 은 소문자 `aa:bb:cc:dd:ee:ff` 입니다(문서 그림은 대문자지만 코드는 소문자). 앱은 소문자로 정규화합니다.

**부팅 거동:** 전원 · 리셋 · `reboot` 뒤 로그가 쏟아지고 `cfg_print()` 로 설정 7줄이 찍힌 뒤 Wi-Fi 연결이 시작되며, `app_main` 끝에서 REPL 이 뜹니다(probe 0.5초 → dumb 모드 안내 3줄 → `bridge>`). 통상 3~6초. 저장된 설정이 없으면 `setup: setup mode on - "AGVBR-01-XXXX"` 와 함께 BLE 설정 모드가 자동으로 열립니다. 콘솔 `reboot` 뒤에는 설정 모드가 다시 열리지 않습니다(BLE 에서 `reboot` 한 경우만 다시 열림).

**하드 리셋 (예비):** 보통은 `reboot` 명령을 씁니다. 응답이 없을 때만 esptool 의 방식(RTS=true 100ms → RTS=false, DTR 은 false 유지)으로 EN 을 당깁니다. DTR=true 상태에서 RTS 를 내리면 다운로드 모드로 들어가니 주의.

**굽기와 포트:** esptool 이 포트를 열어야 하므로 굽기 전에 앱의 시리얼 연결을 **반드시 닫습니다**. 굽기 뒤 `--after hard_reset` 으로 재부팅되며 USB-UART 칩은 그대로라 같은 COM 번호로 다시 열면 됩니다.

### 3.3 서버 원격 설정 (UDP) — 선택 기능용

패킷 규격(리틀 엔디언, 헤더 10바이트): `A5 5A | ver=1 | type | agv_id | flags=0 | seq(u16) | len(u16) | payload`. C# 구현은 `tools/BridgeTest.cs` 의 `Proto` 클래스를 그대로 가져옵니다.

| 항목 | 사실 |
|---|---|
| 보내는 곳 | 브리지 IP : `local_port`(기본 50000) |
| 송신원 조건 | **브리지에 설정된 `server` IP 에서 온 패킷만** 처리(`rx_foreign` 으로 폐기). 즉 앱은 게이트웨이 PC(또는 `server` 로 지정된 PC)에서만 쓸 수 있음 |
| 호기 조건 | 헤더 `agv_id` 가 브리지 `agv` 와 같아야 함 |
| 명령 | `CFG_CMD`(0x40) payload = 명령 한 줄(UTF-8). 응답 `CFG_RESULT`(0x41), **seq 는 요청 seq 그대로**, 응답 목적지는 **요청 패킷의 송신 endpoint(IP:포트)** → 앱은 임의의 포트(ephemeral)를 써도 응답을 받음. 게이트웨이가 50000 을 쓰는 PC 에서도 공존 가능 |
| 명령 집합 | `cfg` · `set` · `save` · `reboot` · `stat`(fw 줄 포함) · `setup [on\|off]` · `dsp` · `help`. **`defaults` 불가**. `set` 값은 **줄 끝까지**(따옴표 없이 공백 허용) |
| `save` 응답 | `saved (trial) - 'reboot' to apply. After reboot the bridge needs a server packet within 180 s or it restores the previous config (gateway 0.3.0+ pings automatically)` |
| trial 확정 | 재부팅 뒤 **서버 IP 에서 온 하향 패킷 1개**(PING 0x20 등)를 받으면 확정. 180초 안에 없으면 이전 설정으로 복구 후 재부팅. 서버 IP 를 바꿨다면 **새 서버**가 보내야 함 |
| PING/PONG | `PING`(0x20) 임의 payload → `PONG`(0x21) seq · payload 그대로. 앱의 "응답 확인 · 확정" 용 |
| INFO | `INFO_REQ`(0x11) → `INFO`(0x12) JSON: `agv, mac, ip, fw, part, ota, cfg, setup, up, dsp, dspb, dsp_build, dsp_st, staged` |
| heartbeat | `STATUS`(0x10)는 **`server:server_port` 로만** 감. 게이트웨이가 50000 을 점유한 PC 에서는 앱이 못 봄. 게이트웨이가 꺼진 벤치에서는 앱이 50000 을 열어 받을 수 있음(BridgeTest 방식) |
| 재부팅 지연 | `reboot` 응답(`rebooting`)을 보낸 뒤 약 1초 후 재시작 |

### 3.4 BLE (Nordic UART Service) — 선택 기능용

| 항목 | 사실 |
|---|---|
| 광고 이름 | `AGVBR-<호기 2자리>-<STA MAC 끝 4자리 대문자>` 예 `AGVBR-07-A1B2`. 설정 모드가 열려 있을 때만 광고 |
| 설정 모드 열림 | 저장 설정 없이 부팅(첫 설치) · BOOT 버튼(GPIO0) 3초 · 콘솔/원격 `setup on` · BLE 에서 `reboot` 한 직후 |
| 닫힘 | `setup off` · 휴대폰 없이 Wi-Fi 연결 상태 5분 · 콘솔/전원 재부팅(저장 설정 있을 때) |
| UUID | 서비스 `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`, RX(앱→ESP, Write/WriteNoResponse) `6E400002-…`, TX(ESP→앱, Notify) `6E400003-…` |
| 연결 | 1대만. 페어링 없음(앱 수준 비밀번호). 연결 간격 45~75ms 요청 |
| 첫 메시지 | notify 구독 직후 `AGV Wi-Fi Bridge AGVBR-07-A1B2 (fw 0.5.5)\r\nlogin <password>\r\n` |
| 로그인 | `login agvsetup`(빌드 기본값) → `ok - type help`. 틀리면 1초 뒤 `wrong password`. **같은 연결에서 5회 연속 실패 시** `wrong password - too many failures, disconnecting` 후 끊김. 로그인 전 명령은 `login required: login <password>` |
| 줄 처리 | CR · LF · CRLF 모두 줄 끝. 줄바꿈 없이 끝난 입력은 0.3초 뒤 한 줄로 처리. 수신 버퍼 256B. 출력 줄 끝 `\r\n`, MTU-3 단위 분할 |
| 명령 | `cmd.c` 와 같음 + `defaults` 가능. `save` 는 즉시 확정(현장 저장), 대기 중인 원격 복구도 취소 |
| 주의 | BLE 가 켜진 동안 Wi-Fi 지연 증가 → 운용 중 호기는 AGV 정지 상태에서 |

### 3.5 펌웨어 · 굽기

**빌드 zip** (`agv_wifi_bridge_<버전>.zip`, GitHub Releases 또는 Actions 아티팩트) 안의 폴더 `agv_wifi_bridge_<버전>/`:

| 파일 | 오프셋 | 용도 |
|---|---|---|
| `agv_wifi_bridge_merged.bin` | `0x0` | 전체 이미지 (**nvs 까지 덮어 설정이 지워짐**) |
| `bootloader.bin` | `0x0` | |
| `partition-table.bin` | `0x8000` | 0.5.0 부터 바뀐 파티션 표 (OTA 로는 못 바꿈) |
| `ota_data_initial.bin` | `0xf000` | 부팅 슬롯 → `ota_0` |
| `agv_wifi_bridge.bin` | `0x20000` | 앱 (OTA 에도 쓰는 파일) |
| `flash_args` | | 위 4개 파일의 `write_flash` 인자 (zip 평면 구조 기준) |
| `flash.bat` · `flash.sh` · `FLASH_GUIDE.md` · `images/` · `README.txt` | | 사람용 |

Releases 에는 zip 외에 `agv_wifi_bridge.bin` · `agv_wifi_bridge_merged.bin` 도 **고정 이름**으로 첨부됩니다.

**굽기 방식 3가지** (esptool, `--chip esp32s3 --port COMx --baud 921600 --before default_reset --after hard_reset`):

| 방식 | 명령 | 설정(nvs) | 쓰는 경우 |
|---|---|---|---|
| A. 전체 | `write_flash 0x0 agv_wifi_bridge_merged.bin` | **지워짐** | 새 보드, 응답 없는 보드, 완전 초기화 |
| B. 파티션 포함 4개 | `write_flash 0x0 bootloader.bin 0x8000 partition-table.bin 0xf000 ota_data_initial.bin 0x20000 agv_wifi_bridge.bin` (= `write_flash @flash_args`) | 유지 | **0.5.0 미만** 또는 `dsp` 상태 `nopart` 인 보드를 0.5.x 로 올릴 때 |
| C. 앱만 | `write_flash 0xf000 ota_data_initial.bin 0x20000 agv_wifi_bridge.bin` | 유지 | 이미 0.5.x 파티션인 보드. 0.5.0 미만에는 쓰지 않음 |
| 지우기 | `erase_flash` | 지워짐 | 완전 초기화 전 단계 |

- esptool 탐색 순서(`flash.bat` 과 동일): Arduino IDE 내장 `%LOCALAPPDATA%\Arduino15\packages\esp32\tools\esptool_py\*\esptool.exe` → PATH 의 `esptool` → `python -m esptool`. 앱은 여기에 **내장 esptool.exe**(espressif/esptool Releases 의 Windows 바이너리)를 최우선으로 둡니다.
- 진행률: stdout 의 `Writing at 0x000a0000... (37 %)` 에서 `\((\d+) %\)`. 성공 끝: `Hash of data verified.` 그리고 `Hard resetting via RTS pin...`.
- 실패 대처(FLASH_GUIDE 3장): 시리얼 모니터가 포트를 잡고 있음 → 닫기 / `Failed to connect` → BOOT 누른 채 RESET 후 재시도 / 케이블 불안정 → `-b 460800` → `-b 115200` 으로 낮춰 재시도.
- 보드: 플래시 **4MB 이상** 필요. 4MB 미만이면 계속 재부팅.

**버전 · 최신 확인 (사내망: api.github.com 차단, github.com 허용):**

| 용도 | URL |
|---|---|
| 최신 버전 번호 | `https://github.com/Leepy0/AGV_Wifi_Bridge/releases/latest` 를 **리다이렉트 따라가지 않고** 요청 → `Location: …/releases/tag/v0.5.5` 에서 `0.5.5` |
| 최신 zip | `https://github.com/Leepy0/AGV_Wifi_Bridge/releases/download/v<ver>/agv_wifi_bridge_<ver>.zip` |
| 항상 최신 앱 bin | `https://github.com/Leepy0/AGV_Wifi_Bridge/releases/latest/download/agv_wifi_bridge.bin` (고정 이름) |
| 매니페스트 (변경 요청 BR-6 반영 후) | `https://github.com/Leepy0/AGV_Wifi_Bridge/releases/latest/download/bridge.json` |

### 3.6 버전 호환 매트릭스

앱은 **기능 유무를 출력으로 감지**하고(예: `cfg` 에 `dns=` 줄이 있는지), 버전 문자열은 표시와 굽기 방식 추천에만 씁니다.

| 브리지 | 특징 | 앱 대응 |
|---|---|---|
| < 0.5.0 | 구 파티션(dspfw 없음). BLE 없음(0.4.x 이하 일부는 SoftAP) | 굽기 방식 B 권장. 설정 키는 dns/dns2 제외 동일 |
| 0.5.0 ~ 0.5.2 | 새 파티션, BLE 설정, `stat` 에 mac 없음(0.5.2 까지) | MAC 은 heartbeat/INFO 또는 부팅 로그에서. 콘솔 `stat` 의 `mac=` 토큰이 없으면 "알 수 없음" |
| 0.5.3 | `stat` wifi 줄에 `mac=` | |
| 0.5.4 | `log` 명령 | |
| **0.5.5** (현재) | `dns` · `dns2` 키, `cfg` 에 `dns=… dns2=…` 줄 | DNS 입력란 활성 |
| OTA 로 0.5.x 를 받은 구 파티션 보드 | `dsp` 상태 `nopart` | 굽기 방식 B 안내 |

### 3.7 브리지 소스 참조 표

| 알고 싶은 것 | 파일 · 위치 |
|---|---|
| 키별 검증 규칙 | `main/app_config.c` `apply()` |
| `cfg` 출력 형식 | `main/app_config.c` `cfg_format()` |
| 콘솔 명령 · 응답 문자열 | `main/console_cmd.c` |
| BLE · UDP 명령 · 응답 문자열 | `main/cmd.c` |
| `stat` 의 wifi/net 줄 | `main/net_bridge.c` `net_format_status()` |
| UDP 패킷 처리 · 서버 IP 필터 | `main/net_bridge.c` `net_rx_task()` `handle_cfg_cmd()` |
| UDP 패킷 C# 구현 | `tools/BridgeTest.cs` `Proto` · `Agv` |
| BLE UUID · 줄 처리 | `main/setup_ble.c` |
| 설정 모드 열림 · 닫힘 | `main/setup.c`, README "휴대폰 BLE" |
| 굽기 방식 · 문제 해결 | `docs/FLASH_GUIDE.md` 3 · 9장 |
| Wi-Fi 끊김 사유 코드 | FLASH_GUIDE 9장 + ESP-IDF `wifi_err_reason_t` (8장 표) |
| 빌드 기본값 | `main/Kconfig.projbuild` |
| 릴리스 zip 구성 | `.github/workflows/build.yml` Package 단계 |

---

## 4. 기능 명세

번호 규칙: `F-<영역><번호>`. 우선순위 **필수**(없으면 목표 미달) / **권장**(반복 제거 · 실수 방지에 효과 큼) / **선택**(여유 있을 때). 구현 단계는 10장.

### 4.1 연결 (C)

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-C1 | 필수 | 포트 목록 · 자동 선택 | 1초마다 포트 목록 갱신. 친숙한 이름(장치 관리자 이름, 예 `Silicon Labs CP210x USB to UART Bridge (COM5)`) 표시. CP210x · CH34x 를 위로, `VID_303A`(ESP 내장 USB)는 "콘솔 입력 불가" 표시. 후보가 하나면 자동 선택, 새로 나타난 포트가 있으면 그것을 선택 |
| F-C2 | 필수 | 연결 · 해제 | 115200, DTR/RTS false 로 열기. 빈 줄로 프롬프트 동기화(최대 3초). 실패 사유 구분: 포트 사용 중(다른 프로그램) / 응답 없음(펌웨어 없음 · 다른 포트) / 권한 |
| F-C3 | 필수 | 장치 식별 | 연결 직후 `ota` → fw/part/ota상태, `stat` → mac/ip/wifi/설정모드 이름, `cfg` → 설정 전체 + state. 이 셋을 묶어 `DeviceSnapshot` 으로 보관. 변경 요청 BR-1 이 반영된 펌웨어면 `cfg json` 한 번으로 대체 |
| F-C4 | 필수 | 연결 상태 상시 표시 | 상태 칩: 끊김 / 연결 중 / 연결됨(장치 이름 · fw) / 굽는 중 / 재부팅 대기. "마지막 읽기: n초 전" |
| F-C5 | 권장 | 보드 교체 감지 | 포트 사라짐 → 끊김 표시, 다시 나타남 → 자동 재연결(사용자가 켠 경우). 연속 세팅(F-B)의 기반 |
| F-C6 | 선택 | 연결 종류 전환 | USB / 원격(UDP) / BLE. 같은 설정 화면을 공유. 4.11 · 4.12 |

### 4.2 설정 편집 (E)

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-E1 | 필수 | 설정 폼 | 3.1 의 17개 키. 그룹: 호기 / Wi-Fi / 네트워크(IP·마스크·GW, 고급: DNS·DNS2) / 서버(IP·포트·수신 포트) / 고급(RFID 송신·포트, 보드레이트 2개, hb_ms). 고급 그룹은 접힘 기본 |
| F-E2 | 필수 | 장치값 ↔ 적용값 대조 | 장치에서 읽은 값과 폼의 값이 다르면 항목 옆에 "변경" 표시, 하단에 "변경 n개". 비밀번호는 장치값 "(8자)" 로 표시하고 폼이 비어 있으면 **유지** |
| F-E3 | 필수 | 입력 검증 | 3.1 규칙을 입력 즉시(0.1초 안) 검사해 항목 아래 한 줄 안내. 전부 통과해야 [적용] 활성. 추가 검사: IP · GW · 서버가 같은 서브넷인지(마스크 기준, 경고만), IP 가 GW · 서버와 같지 않은지, 마지막 옥텟 0/255 금지(경고) |
| F-E4 | 필수 | 프로필 자동 채움 | 프로필 선택 → 호기 번호 외 전부 채움. 호기 번호 바꾸면 IP 규칙으로 IP 재계산(사용자가 IP 를 직접 고친 뒤에는 재계산하지 않고 "규칙과 다름" 표시 + [규칙값으로] 버튼) |
| F-E5 | 필수 | 비밀번호 처리 | SSID 입력/선택 시 라이브러리에서 자동 채움. 표시/숨김 토글. 라이브러리에 없고 장치에도 없으면 필수 입력. `-`(오픈 AP) 선택지 |
| F-E6 | 권장 | 호기 번호 보조 | 프로필 내 이력의 다음 번호 제안(최대값+1). 이력에 같은 번호가 **다른 MAC** 으로 있으면 경고 배너("07호기는 2026-10-02 에 MAC a1:b2:… 보드에 설정했습니다. 교체라면 계속") |
| F-E7 | 권장 | DHCP 모드 | IP 모드: 규칙 / DHCP / 직접 입력. DHCP 선택 시 IP 란 비활성 + "게이트웨이는 고정 IP 권장(ACS agvOpCtrl 과 일치 필요)" 안내 |
| F-E8 | 권장 | 장치값으로 되돌리기 | 폼 전체 또는 항목별 되돌리기(Ctrl+Z 수준의 단순 되돌림) |
| F-E9 | 선택 | 설정 복사 · 붙여넣기 | 현재 폼을 `key=value` 텍스트로 복사 / 붙여넣기(콘솔 `cfg` 출력 그대로 붙여 넣어도 파싱) |

### 4.3 현장 프로필 (P)

프로필 = 한 현장(또는 한 Wi-Fi 망)에서 호기 공통으로 쓰는 값 묶음. **반복 제거의 핵심.**

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-P1 | 필수 | 프로필 CRUD | 이름, 메모, 3.1 의 `agv` 제외 전 항목 + IP 규칙 + BLE 비밀번호(기본 `agvsetup`). 편집 대화상자. 삭제는 확인(되돌릴 수 없음) |
| F-P2 | 필수 | 기본 프로필 | 첫 실행 시 "본사" 프로필을 **빌드 기본값 + 회사 고정값**으로 생성해 둠(DNS 등 실제 값은 13장 결정 사항 5 에서 확정, 그때까지 자리표시자). 마지막 사용 프로필 기억 |
| F-P3 | 필수 | IP 규칙 | `offset` 모드: 기준 네트워크 `192.168.10.0` + 오프셋 100 → 호기 7 = `.107`. 계산 결과가 1~254 를 벗어나면 오류. `dhcp` / `manual` 모드 |
| F-P4 | 권장 | 내보내기 · 가져오기 | JSON 파일. 비밀번호는 기본 제외, "비밀번호 포함(평문)" 체크 시 포함 + 경고. 가져올 때 같은 이름은 덮어쓸지 확인 |
| F-P5 | 권장 | 프로필 복제 | "본사" 를 복제해 "OO공장" 만들기 → SSID · 서버만 수정 |
| F-P6 | 선택 | 프로필별 요약 | 몇 대 설정했는지, 마지막 사용일 |

### 4.4 SSID 라이브러리 (W)

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-W1 | 필수 | SSID ↔ 비밀번호 저장 | 적용에 성공한 SSID/비밀번호를 자동 저장(동의 체크 "이 비밀번호 저장", 기본 켬). Windows DPAPI(현재 사용자) 로 암호화해 저장 |
| F-W2 | 필수 | 자동 채움 | SSID 란은 콤보(입력 + 목록). 선택하거나 일치하는 SSID 를 입력하면 비밀번호 채움, 마지막 사용일 표시 |
| F-W3 | 권장 | 관리 | 목록 보기 · 삭제 · 비밀번호 보기(토글, 감사 로그) |
| F-W4 | 선택 | Wi-Fi 스캔 연동 | 변경 요청 BR-4(`scan`) 반영 시 장치가 보는 SSID 목록을 콤보에 "현장에서 보임 · RSSI" 로 표시 |

### 4.5 적용 워크플로 (A) — 7.1 상세

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-A1 | 필수 | 적용 | `set` × n → `cfg` 대조 → `save` → `reboot` → 부팅 대기 → `stat` 확인. 진행 단계 표시 + 취소(`save` 전까지) |
| F-A2 | 필수 | 결과 카드 | 성공: 호기 · 장치 이름 · IP · RSSI · fw. 실패: 단계 · 원인 · 다음 행동(8장 표) |
| F-A3 | 필수 | 이력 기록 | 성공/실패 모두 기록(4.8) |
| F-A4 | 권장 | 저장만 / 재부팅 없이 | "저장만(재부팅은 나중에)" 옵션 — 메인보드 작업 중 `busy` 로 재부팅이 거부된 경우의 재시도 경로 |
| F-A5 | 권장 | 공장 초기화 | `defaults` + `reboot`. 위험 확인(대상 이름 표시, 동사 버튼 "07호기 설정 지우기") |

### 4.6 연속 세팅 (B)

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-B1 | 권장 | 연속 모드 | 토글. 적용 성공 후 호기 +1, "다음 보드를 연결하세요" 대기. 보드 교체 감지(F-C5) → 자동 연결 · 읽기 · 폼 채움 |
| F-B2 | 권장 | 이미 설정된 보드 보호 | 읽은 보드가 `state=saved` 면 자동 채움 대신 경고 배너. 사용자가 "덮어쓰기" 를 눌러야 진행 |
| F-B3 | 선택 | 자동 적용 | 빈 보드(`state=default`)면 사용자 클릭 없이 적용. 기본 끔, 켤 때 확인 |
| F-B4 | 선택 | 세션 요약 | 이번 연속 작업으로 설정한 호기 목록을 표로, 클립보드 복사 |

### 4.7 펌웨어 (FW) — 7.2 상세

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-FW1 | 필수 | 패키지 선택 | 로컬 zip(또는 풀어 둔 폴더) 선택, 최근 목록. zip 검증: 3.5 의 파일 5개 존재, 버전은 폴더명 `agv_wifi_bridge_<ver>` 또는 `README.txt` 첫 줄에서 |
| F-FW2 | 필수 | 굽기 (A/B/C) | esptool 실행, 진행률, 로그, 취소(프로세스 종료 후 "보드가 불완전 상태일 수 있음 → 다시 굽기" 안내). 굽기 전 시리얼 연결 자동 해제, 후 자동 재연결 · fw 확인 |
| F-FW3 | 필수 | 방식 추천 | 장치 상태로 추천(3.5 표): 응답 없음 → A, fw<0.5.0 또는 `nopart` → B, 그 외 → C. A 는 위험 확인창(설정 지워짐) |
| F-FW4 | 필수 | esptool 찾기 | 내장 → Arduino → PATH → `python -m esptool`. 설정에서 경로 지정 가능. 없으면 안내 + pip 명령 복사 |
| F-FW5 | 권장 | 설정 백업 · 복원 | A 방식 때 기본 켬: 굽기 전 `cfg` 읽기(비밀번호는 라이브러리/프로필) → 굽기 → 부팅 → 적용 워크플로로 복원 |
| F-FW6 | 권장 | 최신 확인 · 다운로드 | 3.5 의 URL 로 최신 버전 조회, zip 다운로드 → `%LOCALAPPDATA%\AgvCfgManager\firmware\` 캐시. 장치 fw 와 비교해 "업데이트 가능" 표시. 인터넷 없으면 캐시만 |
| F-FW7 | 권장 | 재시도 속도 낮춤 | 실패 시 921600 → 460800 → 115200 자동 재시도 제안 |
| F-FW8 | 선택 | 지우기 | `erase_flash` (위험 확인) |
| F-FW9 | 선택 | 여러 대 연속 굽기 | 연속 모드와 결합: 꽂으면 굽고, 설정까지 |

### 4.8 이력 (H)

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-H1 | 필수 | 기록 | 시각 · 프로필 · 호기 · MAC · 장치 이름 · IP · SSID · fw(전/후) · 연결 종류 · 작업(적용/굽기/초기화) · 결과 · 메모. `history.jsonl` 에 추가만(삭제 없음) |
| F-H2 | 권장 | 조회 | 표(가상화), 프로필 · 호기 · MAC 검색, 기간 필터. 행 클릭 → 그때 값으로 폼 채우기 |
| F-H3 | 권장 | 내보내기 | CSV (설치 보고용) |
| F-H4 | 선택 | 라벨 텍스트 | "AGV-07 · AGVBR-07-A1B2 · 192.168.10.107 · a1:b2:c3:d4:e5:f6" 클립보드 복사 (라벨 프린터용) |

### 4.9 진단 · 콘솔 (D)

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-D1 | 필수 | 콘솔 | 원문 입출력(고정폭), 입력줄 + 히스토리(↑↓), 끝 따라가기 토글, 검색, 저장. 앱이 보내는 명령도 모두 표시(비밀번호 값은 `***` 로 마스킹) |
| F-D2 | 필수 | 상태 카드 | `stat` 해석: Wi-Fi UP/DOWN · RSSI(좋음/보통/약함) · IP · 채널 · 끊김 사유 설명(8.3) · 송수신 카운터 · 설정 모드 · 메인보드 상태 · 설정 상태(saved/default/trial). [새로고침], 자동 5초 토글 |
| F-D3 | 권장 | 빠른 명령 버튼 | `stat` · `cfg` · `ota` · `setup on/off` · `dsp` · `log warn/info` · `mon 1/0` |
| F-D4 | 권장 | 게이트웨이 연결 점검 안내 | `wifi: UP` 인데 `rx=0` → "게이트웨이 화면에서 PING 을 보내 보세요", `rx_foreign` 증가 → server IP 불일치 안내 |
| F-D5 | 선택 | 부팅 로그 캡처 | `reboot` 뒤 부팅 로그를 따로 저장(문제 보고용) |

### 4.10 앱 공통 (G)

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-G1 | 필수 | 설정 저장 | 창 크기 · 위치 · 열 너비, 마지막 포트 · 프로필, 테마(라이트/다크/시스템), esptool 경로, 자동 업데이트 확인 여부 |
| F-G2 | 필수 | 로그 파일 | `%LOCALAPPDATA%\AgvCfgManager\logs\app-YYYYMMDD.log`, 7일 보관. 비밀번호 절대 기록 금지 |
| F-G3 | 필수 | 자동 업데이트 | `dotnet-self-update` 스킬 그대로 (11장) |
| F-G4 | 권장 | 출장 준비 | 한 번에: 최신 펌웨어 캐시 + 프로필 내보내기 + esptool 내장 확인 → 체크리스트 표시 |
| F-G5 | 권장 | 정보 창 | 버전 · 채널 · 지원 브리지 버전 범위 · 업데이트 확인 · 로그 폴더 열기 |
| F-G6 | 선택 | 명령행 옵션 | `--port COM5 --profile 본사 --agv 7 --apply` (스크립트 연동), `--fake`(가짜 브리지로 실행) |

### 4.11 원격 설정 (UDP) (R) — 선택, Phase 7

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-R1 | 선택 | 원격 연결 | 대상 IP · 호기 번호 입력. `cfg` 로 응답 확인. 응답 없음 → "이 PC IP 가 브리지 server 값과 같은지 · 호기 번호" 안내 |
| F-R2 | 선택 | 원격 적용 | `set` 은 따옴표 없이 줄 끝까지. `save` 는 trial 안내 표시. `reboot` 뒤 새 IP 로 PING 을 5초마다 최대 180초 보내 확정, INFO_REQ 로 `cfg=saved` 확인 |
| F-R3 | 선택 | 벤치 모드 | 게이트웨이가 없을 때 50000 을 열어 heartbeat 를 받아 온라인 호기 목록 표시(BridgeTest `list` 와 같음). 50000 바인드 실패 → "게이트웨이 실행 중, 대상 IP 직접 입력" |

### 4.12 BLE (L) — 선택, Phase 8

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-L1 | 선택 | 스캔 · 연결 | `AGVBR-` 로 시작하는 광고만. 연결 → notify 구독 → `login <프로필 BLE 비밀번호>` |
| F-L2 | 선택 | 명령 | 3.4 규약. 같은 설정 화면. `reboot` 뒤 다시 광고되면 재연결해 `stat` 확인 |

---

## 5. 화면 설계

`field-app-ui-design` 스킬 규칙을 따릅니다: 화면당 주요 행동 1개, 상태 상시 표시, 4의 배수 간격, 색은 상태에만, 라이트/다크, 키보드만으로 조작, 긴 작업은 진행률 + 취소.

### 5.1 전체 구조

```
┌────────────────────────────────────────────────────────────────────────┐
│ [연결]  포트 ▾ Silicon Labs CP210x (COM5)   [연결]   ● 연결됨 AGVBR-07-A1B2 · fw 0.5.5 · cfg saved │
├────────────────────────────────────────────────────────────────────────┤
│  설정   펌웨어   진단   이력                                   ⓘ  ⚙      │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│                         (탭 내용)                                      │
│                                                                        │
├────────────────────────────────────────────────────────────────────────┤
│ 상태줄: 마지막 읽기 3초 전 · 프로필 본사 · 연속 모드 꺼짐 · 업데이트 없음 │
└────────────────────────────────────────────────────────────────────────┘
```

- 창 최소 1000×680, 기본 1200×760. 창 상태 저장.
- 탭 4개 고정. 프로필 편집 · 라이브러리 · 정보 · 설정은 대화상자.
- 연결 패널은 항상 위에. 연결 종류 전환(F-C6)은 포트 콤보 왼쪽의 작은 세그먼트(USB / 원격 / BLE), 선택 기능이 없는 동안은 USB 만.

### 5.2 설정 탭 (주요 행동: **적용**)

```
프로필 [본사 1공장 ▾] [편집] [새로]      호기 번호 [ 7 ]  ← 크게 (28px), 포커스 기본
─────────────────────────────────────────────────────────────────────
 항목               적용할 값                       장치 현재값
 Wi-Fi
   SSID             [AGV_WIFI            ▾]          AGV_WIFI
   비밀번호         [••••••••  ] [표시]  ☑ 저장      (8자) 유지
 네트워크
   IP 모드          (●) 규칙  ( ) DHCP  ( ) 직접
   IP               [192.168.10.107]  ← 규칙값         192.168.10.107
   서브넷 마스크    [255.255.255.0 ]                   255.255.255.0
   게이트웨이       [192.168.10.1  ]                   192.168.10.1
   ▸ 고급 (DNS 192.168.10.1 · 8.8.8.8)                  auto · auto     ⚠ 변경
 서버
   서버 IP          [192.168.10.10 ]                   192.168.0.10     ⚠ 변경
   서버 포트        [50000]   수신 포트 [50000]
 ▸ 고급 (RFID 브로드캐스트:50001 · 115200 · 9600 · hb 1000)
─────────────────────────────────────────────────────────────────────
 변경 3개 · 검증 통과                 [장치값으로]   [ 적용 (저장 후 재부팅) ]  ← 가장 큼, Ctrl+Enter
 ☐ 연속 모드   ☐ 저장만(재부팅 안 함)
```

- "장치 현재값" 열은 연결 전엔 "—". 다른 항목은 값 옆에 ⚠ 변경 아이콘 + 텍스트(색만으로 구분하지 않음).
- 적용 중에는 폼이 비활성, 하단이 진행 패널로 바뀜: 단계 목록(설정 전송 12/16 → 저장 → 재부팅 → Wi-Fi 확인) + 진행바 + [취소](저장 전까지만 활성).
- 완료 카드는 같은 자리에 표시: 성공은 중립색 + ✓, 실패는 알람색 + 원인 + [다시 시도] / [콘솔 보기].
- 연속 모드가 켜져 있으면 완료 카드에 [다음 호기 (8)] 가 기본 버튼.
- 키보드: Tab 순서 = 시각 순서, Ctrl+Enter 적용, Ctrl+Z 장치값으로, F5 장치 다시 읽기, Esc 취소.

### 5.3 펌웨어 탭 (주요 행동: **굽기**)

```
 장치: fw 0.5.1 · 슬롯 ota_0 · 파티션 구버전(nopart)        최신: 0.5.5 (캐시됨 · 2026-10-14)   [최신 확인]
 패키지  [agv_wifi_bridge_0.5.5.zip ▾] [찾기…]  검증 ✓ 5개 파일
 방식    (●) B. 파티션 포함 4개 — 설정 유지   ← 추천: 파티션 구버전
         ( ) C. 앱만 — 설정 유지
         ( ) A. 전체 이미지 — 설정 지워짐   ☑ 굽기 전 설정 백업 → 굽기 후 복원
 포트    COM5 (연결 중이면 자동으로 끊고 굽습니다)  속도 [921600 ▾]
 ──────────────────────────────────────────────────
 [ 굽기 ]                                            ▸ esptool: 내장 v5.x
 진행: ████████░░░░ 62%  Writing at 0x001a0000      [취소]
 로그 (접힘)
```

- A 방식 선택 시 [굽기] 를 누르면 확인창: "이 보드(AGVBR-07-A1B2)의 저장된 호기 설정이 지워집니다. 백업·복원이 켜져 있습니다." 버튼 "전체 굽기" / "취소"(기본 포커스).
- 굽기 후 자동 재연결 · fw 확인 결과를 같은 자리에 카드로.

### 5.4 진단 탭

왼쪽 상태 카드(Wi-Fi · 네트워크 카운터 · 설정 모드 · 메인보드 · 설정 상태) + [새로고침] [자동 5초], 오른쪽 콘솔(고정폭, 입력줄, 빠른 명령 버튼 한 줄). 카드의 끊김 사유는 코드 + 설명 + 할 일(8.3). RSSI 는 숫자 + 등급 텍스트 + 작은 막대.

### 5.5 이력 탭

표(시각 · 프로필 · 호기 · 장치 이름 · MAC · IP · SSID · fw · 작업 · 결과 · 메모) + 검색/필터 + [CSV 내보내기] + [이 값으로 폼 채우기]. 행 높이 28~32px, 숫자 우측 정렬.

### 5.6 대화상자

| 대화상자 | 내용 |
|---|---|
| 프로필 편집 | 이름 · 메모 / Wi-Fi(SSID, 비밀번호 저장 여부) / 네트워크(IP 규칙: 모드 · 기준 네트워크 · 오프셋 · 예시 계산 표시, 마스크, GW, DNS, DNS2) / 서버 / 고급 / BLE 비밀번호. [복제] [삭제] |
| SSID 라이브러리 | 목록(SSID · 마지막 사용 · 사용 횟수) · 비밀번호 보기 토글 · 삭제 |
| 정보 | 버전 · 채널 · 커밋 · 업데이트 상태 · [지금 확인] · [로그 폴더] · 지원 브리지 버전 |
| 설정 | 테마 · 자동 업데이트 · esptool 경로 · 펌웨어 캐시 폴더 · 연결 시 `log warn` 자동 전송 여부 |
| 위험 확인 | 대상(장치 이름 · 호기) · 영향 · 복구 가능 여부, 동사 버튼, 취소 기본 포커스 |

### 5.7 테마 토큰

`Themes/Tokens.xaml` 에만 정의: 간격 4/8/12/16/24/32, 폰트 12/14/18/20/28(Segoe UI Variable, 로그는 Cascadia Mono), 굵기 400/600, 중립 회색 10단계, 강조 1색, 상태 3색(정상 = 중립 강조, 경고, 알람). 라이트/다크 모두. WPF Fluent `ThemeMode` 사용, 커스텀 `Style` 은 `BasedOn` 필수.

---

## 6. 아키텍처 · 데이터 모델

### 6.1 프로젝트 구조

```
AGV_CFG_Manager/
├─ AgvCfgManager.sln
├─ Directory.Build.props              # LangVersion, Nullable, TreatWarningsAsErrors(테스트 제외)
├─ src/
│  ├─ AgvCfgManager.Core/             # net10.0 — WPF 비의존. 모든 로직은 여기
│  │  ├─ Bridge/
│  │  │  ├─ IBridgeChannel.cs         # ExecAsync(line, timeout, ct) / 이벤트 / 종류
│  │  │  ├─ SerialBridgeChannel.cs    # System.IO.Ports, 프롬프트 동기화, 로그 줄 분리
│  │  │  ├─ UdpBridgeChannel.cs       # (Phase 7) BridgeTest Proto 이식
│  │  │  ├─ BleBridgeChannel.cs       # (Phase 8) WinRT GATT NUS
│  │  │  ├─ BridgeProto.cs            # UDP 패킷 Build/TryParse (BridgeTest.cs 그대로)
│  │  │  ├─ BridgeCommands.cs         # 명령 문자열 조립 (quote/escape), 응답 판정
│  │  │  ├─ ConsoleParsers.cs         # cfg/stat/ota/setup/dsp 텍스트 → 모델
│  │  │  ├─ BridgeConfig.cs           # 17개 키 모델 + Diff
│  │  │  ├─ BridgeStatus.cs           # stat 결과 모델
│  │  │  ├─ DeviceSnapshot.cs         # fw/part/ota/mac/name/config/state
│  │  │  ├─ ConfigValidator.cs        # 3.1 규칙
│  │  │  └─ WifiReason.cs             # 코드 → 설명/조치
│  │  ├─ Profiles/  Profile.cs IpRule.cs ProfileStore.cs NetworkStore.cs(DPAPI) ProfileExport.cs
│  │  ├─ Workflows/ ApplyWorkflow.cs FlashWorkflow.cs BackupRestore.cs BatchCoordinator.cs WorkflowProgress.cs
│  │  ├─ Firmware/  FirmwarePackage.cs(zip 검증) ReleaseLocator.cs(GitHub) FirmwareCache.cs EsptoolLocator.cs EsptoolRunner.cs FlashPlan.cs
│  │  ├─ History/   HistoryEntry.cs HistoryStore.cs(jsonl, CSV)
│  │  ├─ Ports/     SerialPortInfo.cs PortEnumerator.cs(WMI) PortWatcher.cs
│  │  ├─ Fake/      FakeBridgeChannel.cs FakeDevice.cs   # 브리지 cmd 의미 그대로 흉내
│  │  ├─ Settings/  AppSettings.cs SettingsStore.cs AppPaths.cs
│  │  ├─ Update/    Updater.cs   # AmrMapEditor 에서 복사
│  │  └─ Logging/   AppLog.cs
│  └─ AgvCfgManager/                  # net10.0-windows WPF
│     ├─ App.xaml(.cs) MainWindow.xaml(.cs)
│     ├─ Themes/Tokens.xaml, Themes/Controls.xaml
│     ├─ Views/   ConnectionBar, ConfigTab, FirmwareTab, DiagnosticsTab, HistoryTab, ProfileEditorDialog, NetworkLibraryDialog, AboutDialog, SettingsDialog, DangerConfirmDialog
│     ├─ ViewModels/  (CommunityToolkit.Mvvm) MainViewModel, ConnectionViewModel, ConfigViewModel, FirmwareViewModel, DiagnosticsViewModel, HistoryViewModel, ProfileEditorViewModel
│     ├─ Services/  DialogService, ClipboardService, UiDispatcher
│     └─ Resources/ esptool.exe (EmbeddedResource, CI 가 넣음) · 아이콘
├─ tests/
│  └─ AgvCfgManager.Core.Tests/       # xUnit
│     ├─ Fixtures/  console_cfg_055.txt console_stat_up.txt console_stat_down_201.txt console_boot.txt ota.txt …
│     ├─ ConsoleParsersTests.cs ConfigValidatorTests.cs IpRuleTests.cs BridgeCommandsTests.cs
│     ├─ ApplyWorkflowTests.cs (FakeBridgeChannel) FlashPlanTests.cs ProfileStoreTests.cs HistoryStoreTests.cs
├─ tools/  fetch-esptool.ps1          # CI: espressif/esptool Releases 에서 Windows 바이너리 받아 sha256 검증 후 Resources 에 복사
├─ .github/workflows/build-exe.yml
└─ docs/  DEVELOPMENT_PLAN.md BRIDGE_CHANGE_REQUESTS.md DECISIONS.md (개발 중 결정 기록)
```

NuGet: `CommunityToolkit.Mvvm`, `System.IO.Ports`, `System.Management`(포트 친숙 이름), `Microsoft.Extensions.Logging` 또는 자체 로거, 테스트 `xunit` · `FluentAssertions`(선택). BLE 는 `net10.0-windows10.0.19041.0` TFM 의 WinRT 프로젝션(추가 패키지 없음).

### 6.2 핵심 인터페이스

```csharp
public enum ChannelKind { Serial, Udp, Ble, Fake }

public interface IBridgeChannel : IAsyncDisposable
{
    ChannelKind Kind { get; }
    bool IsConnected { get; }
    /// 명령 한 줄을 보내고 결과 텍스트(프롬프트·echo·로그 제외)를 돌려줌.
    Task<string> ExecAsync(string line, TimeSpan timeout, CancellationToken ct);
    /// reboot 처럼 프롬프트가 안 돌아오는 명령: 보내기만 함.
    Task SendRawAsync(string line, CancellationToken ct);
    /// 부팅 완료(프롬프트 복귀)까지 대기. Serial: bridge> 재등장, Udp: cfg 응답, Ble: 재광고·재연결.
    Task WaitReadyAsync(TimeSpan timeout, CancellationToken ct);
    event EventHandler<string> RawReceived;     // 콘솔 화면용 원문 (비밀번호 마스킹은 호출 측)
    event EventHandler Disconnected;
}
```

- `BridgeCommands.Set(key, value)` 는 Serial 이면 `set key "escaped"`, Udp/Ble 면 `set key value`(줄 끝까지).
- `ConsoleParsers.ParseCfg(string) → BridgeConfig + state + differs`, `ParseStat → BridgeStatus`, `ParseOta → (fw, part, otaState)`, `ParseSetup → (on, name)`, `ParseDsp → dspState`. 토큰은 `key=value` 를 공백으로 나누되 `ssid="…"` 는 따옴표 안 공백을 보존. 모르는 토큰은 무시(상위 호환).
- `ConfigValidator.Validate(BridgeConfig, context) → IReadOnlyList<ValidationMessage>` (Error/Warning, 키, 문구).
- `DeviceSnapshot` 은 불변. 새로 읽으면 통째로 교체. `ReadAtUtc` 보유 → "n초 전".

### 6.3 저장 위치와 파일

`%LOCALAPPDATA%\AgvCfgManager\`

| 파일 | 내용 | 형식 |
|---|---|---|
| `settings.json` | 앱 설정(F-G1) | JSON dictionary |
| `profiles.json` | 프로필 목록 + 마지막 프로필 (부록 B-1) | JSON |
| `networks.json` | SSID 라이브러리. 비밀번호는 DPAPI(`ProtectedData`, CurrentUser) 암호화 base64 (부록 B-2) | JSON |
| `history.jsonl` | 작업 이력 1줄 1건 (부록 B-3) | JSON Lines, 추가만 |
| `firmware\` | 받은 zip 캐시 (`agv_wifi_bridge_<ver>.zip`) + `index.json` | |
| `tools\esptool\` | 내장 esptool 추출본 (`esptool.exe` + `version.txt` + sha256) | |
| `logs\app-YYYYMMDD.log` | 앱 로그 7일 | 텍스트 |
| `updates\` | 자동 업데이트 다운로드 | |

모든 JSON 은 `version` 필드로 스키마 버전을 가지며, 읽을 때 모르는 필드는 보존합니다. 쓰기는 임시 파일 → 이름 변경(원자적).

### 6.4 스레딩 · 수명

- Core 는 전부 `async`. UI 는 `await` 만, `.Result`/`.Wait()` 금지.
- `SerialBridgeChannel` 은 `BaseStream.ReadAsync` 루프 태스크 1개가 바이트를 읽어 라인 어셈블러에 넣고, `ExecAsync` 는 `TaskCompletionSource` 로 프롬프트 복귀를 기다림. 동시에 명령 1개만(`SemaphoreSlim(1)`).
- 워크플로는 `IProgress<WorkflowProgress>` 로 단계 · 퍼센트 · 메시지를 보고하고 `CancellationToken` 을 존중. UI 는 해당 탭만 비활성화.
- `PortWatcher` 는 1초 타이머로 `PortEnumerator` 결과를 비교해 Added/Removed 이벤트.

---

## 7. 워크플로 상세

### 7.1 적용 (ApplyWorkflow)

입력: `DeviceSnapshot before`, `BridgeConfig target`, 옵션(`SaveOnly`, `SendAllKeys`=true, `KeepPassword`). 출력: `ApplyResult`(단계별 결과, `DeviceSnapshot after`).

```
Idle ─▶ Validate ─▶ SendSet(i/n) ─▶ ReadBack(cfg 대조) ─▶ Save ─▶ [SaveOnly? Done]
      ─▶ Reboot ─▶ WaitBoot ─▶ VerifyWifi(stat 폴링) ─▶ Done / Failed
```

| 단계 | 동작 | 성공 판정 | 실패 처리 |
|---|---|---|---|
| Validate | `ConfigValidator` 오류 0개, 장치 연결됨, 변경 0개면 "변경 없음" 으로 종료 | | 폼으로 돌아감 |
| SendSet | 키 순서 고정: `agv ssid pass ip mask gw dns dns2 server server_port local_port rfid_dst rfid_port main_baud rfid_baud hb_ms`. `pass` 는 폼이 비어 있으면 **보내지 않음**(유지). `dns`/`dns2` 는 장치가 지원할 때만(`cfg` 에 `dns=` 줄 존재). 각 명령 timeout 2초 | 응답에 `ok` 로 시작 | `invalid key or value` → 그 키를 폼에서 강조하고 중단(저장 안 됨). timeout → 재시도 1회 후 중단 |
| ReadBack | `cfg` 재읽기. 보낸 키 전부가 target 과 같은지, `pass` 는 길이가 같은지, `* differs …` 줄이 있는지(변경이 있으면 있어야 정상) | 전부 일치 | 불일치 키 표시, 중단 |
| Save | `save` | `saved`(Serial/BLE) 또는 `saved (trial)…`(UDP) | `save failed` → 중단, 재시도 버튼 |
| Reboot | `reboot` 는 `SendRawAsync`. 1초 안에 `busy: mainboard programming` 이 오면 재부팅 거부 | | 거부 → "저장은 됐고 재부팅만 남음. 메인보드 작업 후 [재부팅] " 카드 |
| WaitBoot | `WaitReadyAsync(20초)`. 부팅 로그는 콘솔 창에만 | 프롬프트 복귀 | timeout → "보드 응답 없음: USB 재연결 후 [장치 읽기]" |
| VerifyWifi | 2초 간격 `stat`, 최대 30초. `wifi: UP` 이고 (고정 IP 면) `ip` 가 target 과 같으면 성공. 마지막에 `ota` 로 fw, `setup` 으로 새 이름 | | DOWN 유지 → `last_reason` 해석(8.3)과 함께 "저장은 완료, Wi-Fi 미연결" 로 종료(이력 결과 `wifi_down:201`) |
| Done | 이력 기록, 라이브러리에 SSID/비밀번호 저장(동의 시), 프로필 `lastUsed` 갱신 | | |

- 취소: SendSet · ReadBack 단계에서만. 취소하면 편집 사본에 일부 값이 남지만 `save` 전이라 무해("재부팅하면 사라짐" 안내). BR-3(`discard`) 지원 펌웨어면 `discard` 전송.
- UDP 채널일 때 Reboot 이후: 새 IP(target.ip, DHCP 면 이전 IP) 로 5초마다 `PING`, `INFO_REQ` 로 `cfg` 가 `saved` 가 될 때까지 최대 180초. 게이트웨이가 켜져 있으면 더 빨리 확정됨.
- 호기 번호가 바뀌면 BLE 이름도 바뀜(`AGVBR-07-…`). 결과 카드에 새 이름 표시.
- 비밀번호 값은 로그 · 콘솔 화면에 `***` 로.

### 7.2 굽기 (FlashWorkflow)

입력: `FirmwarePackage`, `FlashMode(A|B|C)`, 포트, 속도, `BackupRestore` 여부, `DeviceSnapshot? before`.

```
Plan ─▶ [Backup(cfg 읽기)] ─▶ Disconnect ─▶ RunEsptool(진행률) ─▶ Reconnect ─▶ WaitBoot ─▶ VerifyFw ─▶ [Restore(ApplyWorkflow)] ─▶ Done
```

| 단계 | 동작 · 판정 |
|---|---|
| Plan | 패키지 검증(파일 5개, 버전), esptool 찾기, 방식 추천과 사용자 선택 비교(다르면 경고만). A 방식이면 위험 확인창 |
| Backup | 연결돼 있고 A 방식이고 백업 켬: `cfg` → `BridgeConfig` 보관. 비밀번호는 라이브러리(SSID 일치) → 프로필 → 사용자 입력 순. 못 구하면 "복원 시 비밀번호 없이(오픈) 저장됨" 경고 후 선택 |
| Disconnect | 시리얼 닫기, 포트 해제 확인(200ms 대기) |
| RunEsptool | `esptool --chip esp32s3 --port COMx --baud N --before default_reset --after hard_reset write_flash <방식별 인자>`. 작업 디렉터리 = zip 풀어 둔 폴더. stdout/stderr 를 줄 단위로 읽어 진행률 파싱 · 로그. 종료 코드 0 + `Hash of data verified` |
| 실패 | stderr 끝 20줄 + 8.2 표의 안내. "낮은 속도로 재시도" 제안(F-FW7). 취소하면 프로세스 kill 후 "불완전 상태 가능 → 다시 굽기" |
| Reconnect · WaitBoot | 같은 포트로 열기(최대 10초 동안 1초마다 재시도), `WaitReadyAsync(20초)` |
| VerifyFw | `ota` 의 `fw` 가 패키지 버전과 같음. A 방식 뒤에는 `cfg` 의 `state=default` 가 정상(+ 설정 모드 자동 열림 로그) |
| Restore | 백업이 있으면 `ApplyWorkflow(target = 백업)` 실행. 결과를 굽기 결과 카드에 합쳐 표시 |
| Done | 이력(작업 `flash`, fw 전/후, 방식) |

### 7.3 연속 세팅 (BatchCoordinator)

상태: `Off → Armed(nextAgv) → WaitingBoard → BoardReady → Applying → Armed(nextAgv+1)`.

- Armed 에서 적용 성공 → `nextAgv++`, 포트 끊김 대기. 끊김 없이 사용자가 [적용] 을 다시 누르면 "같은 보드입니다" 경고.
- WaitingBoard: 포트 Removed 뒤 Added(같은 이름이든 다른 이름이든) → 자동 연결 · 읽기.
- BoardReady: `state=default` 면 폼에 `nextAgv` 채움 → (F-B3 켜짐이면 바로 Applying, 아니면 사용자 클릭). `state=saved` 면 경고 배너 + 채우지 않음.
- 중단: 토글 끔 또는 Esc. 세션 요약(F-B4)은 Armed 전환 때마다 누적.

### 7.4 장치 읽기 (ReadDevice)

연결 직후와 F5: `ota` → `stat` → `cfg` → (`setup`, `dsp` 는 `stat` 에 포함돼 있으므로 생략). 3개 중 하나라도 파싱 실패하면 "지원하지 않는 펌웨어 출력 — 콘솔에서 확인" 상태로 두되 연결은 유지. BR-1 반영 펌웨어: `cfg json` 응답이 `{` 로 시작하면 그것만 사용(구버전은 인자를 무시하고 텍스트를 내므로 자연히 폴백).

**기능 감지:** 연결 직후 `help` 를 한 번 보내 출력에 `discard` · `scan` · `nettest` · `log` 가 있는지로 선택 기능 버튼을 켜고 끈다. DNS 지원은 `cfg` 의 `dns=` 줄 유무로. 감지 결과는 `DeviceSnapshot.Capabilities` 에 보관한다.

### 7.5 최신 펌웨어 확인 (ReleaseLocator)

1. 캐시 `index.json` 에서 알고 있는 최신 표시(오프라인).
2. 온라인: `bridge.json` 매니페스트(BR-6, 있으면 버전 · 파일명 · sha256) → 없으면 `releases/latest` 의 리다이렉트 `Location` 에서 태그 → zip URL 조합 → 다운로드(`.partial` → 완료 후 이름 변경, sha256 있으면 대조) → `index.json` 갱신.
3. api.github.com 은 쓰지 않는다. 프록시 · 차단 시 "오프라인: 캐시된 0.5.5 사용" 으로 조용히 넘어간다(오류 창 금지, 상태줄 문구만).

---

## 8. 오류 · 메시지 표

문구 원칙: **무엇이 · 왜 · 다음 행동**. 예외 메시지 원문은 "자세히" 에만.

### 8.1 연결

| 상황 | 표시 | 다음 행동 버튼 |
|---|---|---|
| 포트 없음 | "USB-UART 포트가 없습니다. 데이터 케이블인지, 'UART' 쪽 USB 인지, CP210x/CH343 드라이버가 있는지 확인하세요." | [새로고침] |
| 포트 사용 중 (`UnauthorizedAccessException`) | "COM5 를 다른 프로그램이 쓰고 있습니다 (Arduino 시리얼 모니터, PuTTY 등). 닫고 다시 시도하세요." | [다시 시도] |
| 프롬프트 없음 | "COM5 에서 브리지 응답이 없습니다. 펌웨어가 없는 새 보드이거나 'USB' 쪽 포트일 수 있습니다." | [펌웨어 탭으로] [다른 포트] |
| 연결 중 끊김 | 상태 칩 "끊김 (COM5 사라짐)" + 연속 모드면 자동 대기 | [다시 연결] |

### 8.2 굽기

| esptool 출력 · 상황 | 표시 | 다음 행동 |
|---|---|---|
| `Failed to connect to ESP32-S3` | "보드가 다운로드 모드로 들어가지 않았습니다. BOOT 를 누른 채 RESET 을 눌렀다 떼고 BOOT 를 뗀 뒤 다시 굽기." | [다시 굽기] [속도 낮춰 다시] |
| `could not open port` / `PermissionError` | "COM5 가 사용 중입니다." | [다시 굽기] |
| esptool 없음 | "esptool 을 찾지 못했습니다. 설정에서 경로를 지정하거나 `pip install esptool` 을 실행하세요." | [설정] [명령 복사] |
| 타임아웃 · CRC 오류 | "전송 중 오류. 케이블이 길거나 허브를 거치면 속도를 낮추세요." | [460800 으로 다시] |
| 굽기 뒤 응답 없음 | "굽기는 끝났지만 콘솔 응답이 없습니다. RESET 을 눌러 보고, 계속 재부팅(`rst:0x…`)하면 플래시 4MB 미만 보드입니다." | [장치 읽기] |
| 플래시 크기 경고 (esptool 출력의 flash size 줄, 버전에 따라 `Detected flash size:` 또는 `Auto-detected Flash size:`) | 4MB 미만이면 "플래시 2MB 보드 — 이 펌웨어는 4MB 가 필요합니다." 로 중단(쓰기 전에 `flash_id` 로 먼저 확인해도 됨) | |

### 8.3 Wi-Fi 끊김 사유 (`last_reason`, ESP-IDF `wifi_err_reason_t`)

| 코드 | 이름 | 사용자 문구 · 조치 |
|---|---|---|
| 0 | — | 정상 |
| 2 | AUTH_EXPIRE | 인증 만료 — AP 쪽 설정 · 신호 확인 |
| 8 | ASSOC_LEAVE | 브리지가 스스로 끊음(재부팅 · 설정 변경) — 정상 과정 |
| 15 | 4WAY_HANDSHAKE_TIMEOUT | **비밀번호가 틀렸을 가능성** — 비밀번호 확인 |
| 200 | BEACON_TIMEOUT | 신호 끊김 — AP 거리 · RSSI 확인 |
| 201 | NO_AP_FOUND | **AP 를 찾지 못함** — SSID 철자, 2.4GHz 인지, 채널 1~13 인지, AP 전원 |
| 202 | AUTH_FAIL | 인증 실패 — 비밀번호 · 보안 방식 |
| 203 | ASSOC_FAIL | 연결 거부 — AP 의 최대 접속 수 · MAC 필터 |
| 204 | HANDSHAKE_TIMEOUT | **비밀번호 오류 가능성** |
| 205 | CONNECTION_FAIL | 연결 실패 — AP 상태 |
| 210 | NO_AP_FOUND_W_COMPATIBLE_SECURITY | 보안 방식 불일치 — AP 가 WPA2-PSK 를 지원하는지 (WPA3 전용 · 엔터프라이즈 불가) |
| 211 | NO_AP_FOUND_IN_AUTHMODE_THRESHOLD | 같은 SSID 가 있지만 보안 방식이 다름 — 비밀번호 없는 AP 면 `-` 로 설정 |
| 212 | NO_AP_FOUND_IN_RSSI_THRESHOLD | 신호가 너무 약함 |
| 그 외 | 코드 그대로 | "ESP-IDF 사유 코드 n" + 콘솔 확인 안내 |

### 8.4 적용 · 검증

| 상황 | 표시 |
|---|---|
| `invalid key or value` | "브리지가 `server` 값을 거부했습니다. (규칙: 0.0.0.0 이 아닌 IP)" — 폼 항목 강조 |
| ReadBack 불일치 | "보낸 값과 장치의 편집 값이 다릅니다 (ip). 콘솔 로그를 확인하고 다시 적용하세요." |
| `busy: mainboard programming` | "메인보드 펌웨어를 굽는 중이라 재부팅할 수 없습니다. 저장은 완료됐습니다. 끝난 뒤 [재부팅]." |
| 같은 서브넷 아님 (경고) | "IP 192.168.10.107 과 서버 192.168.20.10 이 같은 네트워크가 아닙니다 (마스크 255.255.255.0). 게이트웨이를 거칩니다." |
| 호기 중복 (경고) | "07호기는 2026-10-02 에 다른 보드(a1:b2:…)에 설정됐습니다. 교체라면 계속하세요." |
| DHCP 선택 | "게이트웨이는 ACS `agvOpCtrl` IP 와 브리지 IP 를 비교합니다. DHCP 를 쓰면 DHCP 서버에서 IP 를 예약하세요." |

---

## 9. 테스트 전략

### 9.1 단위 테스트 (xUnit, 하드웨어 없음) — CI 필수

| 대상 | 케이스 |
|---|---|
| `ConsoleParsers` | 부록 A 의 전문을 Fixtures 로. 0.5.5 `cfg`(dns 있음) · 0.5.2 `cfg`(dns 없음) · 공백 SSID(`ssid="AGV WIFI 2F"`) · `ip=dhcp` · `rfid_dst=off` · `state=trial` · `* differs` 줄 유무 · 로그 줄 섞임 · `stat` UP/DOWN · `mac=` 없는 구버전 `stat` · `ota` 3상태 · `setup` on/off · `dsp` `nopart` |
| `ConfigValidator` | 3.1 표의 경계값 전부(agv 0/1/99/100, ssid 32/33바이트 · 한글 바이트 계산, pass 7/8/64/65/`-`, IP 앞자리 0 · 256 · `dhcp`, mask 0.0.0.0, server 0.0.0.0, 포트 0/1/65535/65536, baud 1199/1200/1000000/1000001, hb 0/1/99/100/2000/2001) + 서브넷 경고 |
| `BridgeCommands` | Serial 인용 · 이스케이프(`"`, `\`, 공백), Udp/Ble 는 인용 없음, 160자 초과 거부 |
| `IpRule` | offset 계산, 범위 초과, dhcp/manual, 기준 네트워크 검증 |
| `ApplyWorkflow` + `FakeBridgeChannel` | 정상 경로, `invalid` 중단(저장 안 됨 검증), `save failed`, `busy` 재부팅 거부, Wi-Fi DOWN 201, timeout, 취소 시점별 상태, `pass` 미전송(유지), dns 미지원 펌웨어에서 dns 키 생략 |
| `FlashPlan` | 방식 추천 표, esptool 인자 조립, 진행률 파싱, 패키지 검증(파일 누락 · 버전 추출) |
| `ProfileStore` · `NetworkStore` · `HistoryStore` | 왕복 저장, 모르는 필드 보존, DPAPI 암복호(Windows 전용 테스트), 원자적 쓰기, 내보내기 비밀번호 포함/제외 |
| `ReleaseLocator` | `Location` 헤더에서 버전 추출(가짜 `HttpMessageHandler`), 매니페스트 우선, 오프라인 폴백 |

### 9.2 FakeBridgeChannel (시뮬레이터)

브리지 `cmd.c` · `console_cmd.c` 의 의미를 C# 으로 옮긴 가짜 장치. 실행 중 설정과 편집 사본을 따로 갖고, `set` 검증은 `ConfigValidator` 와 **별도 구현**(같은 코드를 쓰면 검증 테스트가 무의미). `reboot` 는 `WaitReadyAsync` 를 설정한 지연 뒤 풀고, Wi-Fi 결과(UP / DOWN+reason)는 시나리오로 주입. 펌웨어 버전 · dns 지원 여부 · `nopart` · `busy` 도 주입 가능. `--fake` 로 UI 에서도 사용(개발 · 데모 · 스크린샷).

### 9.3 실기 체크리스트 (단계별 "실기 체크" 에서 수행)

| # | 확인 | 기대 |
|---|---|---|
| 1 | 새 보드(펌웨어 없음) 연결 | "응답 없음 → 펌웨어 탭" 안내 |
| 2 | 전체 굽기 → 자동 재연결 | fw 표시, `state=default`, 콘솔에 설정 모드 열림 로그 |
| 3 | 프로필 + 호기 7 적용 | 16개 `set` 모두 `ok`, `saved`, 재부팅, `wifi: UP ip=…107` |
| 4 | 비밀번호 틀리게 적용 | `wifi: DOWN`, reason 15/204 해석 표시, 이력 실패 기록 |
| 5 | 공백 SSID | 따옴표 처리로 정상 저장, `cfg` 되읽기 일치 |
| 6 | 서버 IP 만 변경 (S3) | 비밀번호 유지(길이 동일), 변경 1건만 표시 |
| 7 | 연속 모드로 2대 | 호기 자동 +1, 교체 감지, 이미 설정된 보드 경고 |
| 8 | 0.5.1 보드 B 방식 굽기 | 설정 유지, `dsp` 가 `nopart` 아님 |
| 9 | A 방식 + 백업 · 복원 | 굽기 후 자동 복원, 최종 `wifi: UP` |
| 10 | 게이트웨이 PC 에서 UDP 원격(선택) | `saved (trial)` → PING 확정 → `cfg=saved` |
| 11 | 다크 모드, DPI 150% | 레이아웃 깨짐 없음 |
| 12 | 자동 업데이트 dev → release | 매니페스트 읽기, 닫을 때 교체, 되돌리기 |

---

## 10. 개발 단계

각 단계 끝에 **테스트 통과 · 커밋 · 푸시**. 한 단계는 되도록 PR 하나. 단계 안의 순서는 위에서 아래.

### Phase 0 — 저장소 뼈대 (반나절)

- `AgvCfgManager.sln`, `src/AgvCfgManager.Core`, `src/AgvCfgManager`(WPF 빈 창, Fluent `ThemeMode`), `tests/AgvCfgManager.Core.Tests`(샘플 테스트 1개), `Directory.Build.props`, `.editorconfig`, `.gitignore`.
- `.github/workflows/build-exe.yml`: windows-latest, `dotnet test`, `dotnet publish`(11장 인자), main 푸시 → `dev` prerelease. (정식 릴리스 단계는 Phase 6 에서 켬)
- `docs/DECISIONS.md` 생성.
- **완료 기준:** CI 녹색, dev prerelease 에 exe 가 올라감, 창이 뜸.

### Phase 1 — Core: 모델 · 파서 · 검증 · 가짜 장치 (1~2일)

- `BridgeConfig`, `DeviceSnapshot`, `ConsoleParsers`, `ConfigValidator`, `BridgeCommands`, `WifiReason`, `IpRule`, `FakeBridgeChannel`.
- Fixtures: 부록 A 를 파일로 넣고, 브리지 저장소 `docs/FLASH_GUIDE.md` 4장의 부팅 로그도 추가.
- **완료 기준:** 9.1 의 파서 · 검증 · 명령 · IpRule 테스트 전부 통과. `FakeBridgeChannel` 로 `set/save/reboot/cfg/stat` 왕복 테스트 통과.

### Phase 2 — 시리얼 연결 · 장치 읽기 · 콘솔 (1~2일)

- `PortEnumerator`(WMI 친숙 이름 + VID/PID), `PortWatcher`, `SerialBridgeChannel`(3.2 규약), `ReadDevice`.
- UI: 연결 바(F-C1~C4), 진단 탭의 콘솔(F-D1) 과 상태 카드(F-D2), 설정 탭의 "장치 현재값" 열(읽기 전용).
- **완료 기준:** 실기 체크 1 · 2(굽기 제외) · 콘솔에서 `stat` 입력 가능. `--fake` 로도 동일 동작. 포트 뽑으면 끊김 표시.

### Phase 3 — 설정 편집 · 적용 워크플로 (2일)

- `ApplyWorkflow`, 설정 탭 폼(F-E1~E3, E8), 진행 패널 · 결과 카드(F-A1~A2), 이력 기록(F-H1, `HistoryStore`).
- 비밀번호 마스킹, 키 순서, dns 지원 감지, `busy` 처리, Wi-Fi 확인 폴링.
- **완료 기준:** 9.1 의 ApplyWorkflow 테스트 통과, 실기 체크 3 · 4 · 5 · 6.

### Phase 4 — 프로필 · SSID 라이브러리 · 자동 채움 · 연속 세팅 · 이력 탭 (2일)

- `Profile`, `ProfileStore`, `NetworkStore`(DPAPI), 프로필 편집 대화상자(F-P1~P3, P5), 자동 채움(F-E4, E5, E6, E7), 라이브러리(F-W1~W3), 내보내기/가져오기(F-P4), `BatchCoordinator`(F-B1, B2), 이력 탭(F-H2, H3).
- **완료 기준:** 호기 번호만 바꿔 2대 연속 설정(실기 7). 프로필 내보내기 → 지우고 → 가져오기 왕복. 비밀번호가 평문으로 디스크에 남지 않음(파일 열어 확인).

### Phase 5 — 펌웨어 탭 (2일)

- `FirmwarePackage`, `EsptoolLocator`, `EsptoolRunner`, `FlashPlan`, `FlashWorkflow`, `BackupRestore`, `ReleaseLocator`, `FirmwareCache`, `tools/fetch-esptool.ps1` + CI 단계, 펌웨어 탭(F-FW1~FW7).
- **완료 기준:** 실기 체크 2 · 8 · 9. 인터넷 끊고도 캐시로 굽기. esptool 없는 PC 에서 안내 문구.

### Phase 6 — 마감: 설정 · 로그 · 자동 업데이트 · 정식 배포 (1일)

- `AppSettings`, 로그 파일, 정보 창, 설정 대화상자, 출장 준비(F-G4), `Updater.cs`(`dotnet-self-update` 스킬 절차 1~6), 테마 점검(라이트/다크, DPI), 키보드 점검.
- README 사용법(스크린샷은 `--fake` 로 캡처), 첫 정식 릴리스 `v0.1.0`(`wpf-build-release` 경로 A).
- **완료 기준:** 실기 체크 11 · 12. `field-app-ui-design` 체크리스트 전 항목 자가 점검 결과를 PR 본문에 기록.

### Phase 7 — (선택) UDP 원격 (1일)

- `BridgeProto`(BridgeTest.cs 이식), `UdpBridgeChannel`, 연결 종류 세그먼트, 원격 적용의 trial 확정(7.1), 벤치 모드(F-R3).
- **완료 기준:** 실기 체크 10.

### Phase 8 — (선택) BLE (1~2일, 리스크 큼)

- `BleBridgeChannel`(WinRT), 스캔 · 로그인 · 재연결. 실패해도 다른 기능에 영향 없도록 완전히 분리.
- **완료 기준:** 보드의 설정 모드를 열고(BOOT 3초) 노트북에서 `cfg` 읽기 · 적용 · 재연결 `stat`.

### 단계 공통 규칙

- UI 작업 전 `field-app-ui-design` 읽기. 화면당 주요 행동 1개 · 4상태(Loading/Empty/Error/Content) · 진행률 + 취소.
- 브리지 저장소는 **읽기만**. 브리지 수정이 필요하면 `docs/BRIDGE_CHANGE_REQUESTS.md` 에 추가하고 앱은 미반영 상태로도 동작하게 만든다.
- 비밀번호 · 사내 IP 는 테스트 Fixture · 스크린샷 · 로그에 넣지 않는다 (`192.168.10.x` 예시값만).
- 커밋 메시지: 한국어 요약 한 줄 + 본문. 모델 이름 · 세션 링크는 금지 항목이 아니지만 코드 주석에는 넣지 않는다.

---

## 11. 빌드 · 배포 · 자동 업데이트

`dotnet-self-update` 스킬의 **핵심 결정표를 그대로** 따릅니다. 바꿀 값만 적습니다.

| 항목 | 값 |
|---|---|
| Repo | `Leepy0/AGV_CFG_Manager` |
| AssetName | `AgvCfgManager.exe` (+ `AgvCfgManager.exe.sha256`) |
| ManifestName | `AgvCfgManager.json` → `https://github.com/Leepy0/AGV_CFG_Manager/releases/latest/download/AgvCfgManager.json` |
| 설정 · 업데이트 경로 | `%LOCALAPPDATA%\AgvCfgManager\settings.json`, `…\updates\` |
| User-Agent | `AgvCfgManager/<ver>` |
| 채널 | csproj `BuildChannel`: `release` / `dev` / `local` |
| publish 인자 | `-c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:EnableCompressionInSingleFile=true -p:Version=<ver> -p:SourceRevisionId=$GITHUB_SHA -p:BuildChannel=release\|dev` |
| CI | main 푸시 → `dev` prerelease 덮어쓰기(`AgvCfgManager-dev.exe`). 정식: 태그 `vX.Y.Z` 또는 `workflow_dispatch release=true`(csproj `Version` 과 일치 검사). 자산 3개 `--latest` |
| esptool 내장 | CI `tools/fetch-esptool.ps1`: espressif/esptool Releases 의 Windows 바이너리 zip(버전 · sha256 고정) → `Resources/esptool.exe` EmbeddedResource. 런타임 첫 사용 때 `%LOCALAPPDATA%\AgvCfgManager\tools\esptool\` 로 추출(sha256 대조). esptool 은 GPL-2.0 — 별도 프로세스로 실행하며 정보 창에 출처 · 라이선스 표기 |
| 참고 구현 | `Leepy0/AmrMapEditor` 의 `Updater.cs`, `MainWindow.AppUpdate.cs`, `build-exe.yml` |

릴리스 노트는 사용자 관점 한글 3~6줄. 테스트 빌드는 절대 Latest 로 올리지 않습니다.

---

## 12. 브리지 변경 요청 요약

전문과 제안 구현은 [BRIDGE_CHANGE_REQUESTS.md](BRIDGE_CHANGE_REQUESTS.md). 앱은 모두 **미반영 상태로 동작**하고, 반영되면 더 단순 · 견고한 경로를 씁니다.

| ID | 요청 | 앱에서의 효과 | 우선 |
|---|---|---|---|
| BR-1 | `cfg json` · `stat json` — 기계용 JSON 출력 | 장치 읽기 명령 3개 → 1~2개, 텍스트 파서 의존 제거, 버전 간 형식 변화에 강함 | 높음 |
| BR-2 | `cfg json` 에 `fw` · `mac` · `name`(BLE 이름) · `idf` 포함 | 핸드셰이크 한 번 | 높음(BR-1 에 포함) |
| BR-3 | `discard` — 편집 사본을 실행 중 설정으로 되돌림 | 적용 취소 · 실패 뒤 깨끗한 상태, 원격 · BLE 세션에 남은 편집 흔적 제거 | 중간 |
| BR-4 | `scan` — 보이는 AP 목록 JSON(ssid, rssi, ch, auth) | 현장에서 SSID 오타 · 2.4GHz 여부 · 보안 방식을 설정 **전에** 확인 | 중간 |
| BR-5 | `nettest` — 서버 IP 로 PING(ICMP) 또는 UDP 도달 확인, DNS 조회 | `wifi: UP` 인데 게이트웨이에 안 보일 때 원인 분리 | 낮음 |
| BR-6 | 릴리스에 `bridge.json` 매니페스트(version, zip, sha256, merged/app bin sha256) 첨부 | 최신 확인 · 무결성 검증 단순화(api.github.com 없이) | 중간 |
| BR-7 | README 의 버전 표기(0.5.2) 를 현재(0.5.5)로, DNS 키를 설정 키 표에 | 문서 불일치 제거 | 낮음 |

---

## 13. 리스크 · 결정이 필요한 사항

| # | 항목 | 현재 가정 | 결정이 바뀌면 |
|---|---|---|---|
| 1 | **플랫폼** | Windows WPF. 현장에서 USB 없이 휴대폰만으로 설정하려면 Android 앱(BLE)이 따로 필요 | Android 는 별도 저장소 · 계획. 이 문서의 3장 · 프로필 JSON 은 그대로 재사용 가능 |
| 2 | 비밀번호 보관 | DPAPI(사용자 계정 묶임). 다른 PC 로 옮길 때는 내보내기(평문 선택) | 팀 공유가 잦으면 내보내기 파일에 암호(AES-GCM + PBKDF2) 옵션 추가 |
| 3 | esptool 내장 | GPL-2.0 바이너리 동봉, 별도 프로세스 | 동봉이 곤란하면 첫 실행 때 github.com 에서 받아 캐시(오프라인 출장 전 "출장 준비" 로 미리 받음) |
| 4 | UDP 원격 범위 | 대상 IP 직접 입력. 게이트웨이 API 로 호기 목록을 받지 않음 | 게이트웨이에 호기 목록 API 가 있으면 F-R1 에 "게이트웨이에서 가져오기" 추가 |
| 5 | **본사 프로필 기본값** | DNS · 서브넷 · 서버 IP 실제 값을 모름 → 자리표시자(`192.168.10.x`) | 값을 받으면 Phase 4 에서 기본 프로필 생성에 반영. 코드에 하드코딩하지 않고 `profiles.json` 초기 템플릿으로 |
| 6 | BLE on Windows | 선택 기능. 노트북 BT 스택 편차 큼 | 실패하면 Phase 8 을 접고 Android 로 |
| 7 | 브리지 변경 요청 수용 | 미반영 전제 | 반영 시 3.2 · 7.4 의 JSON 경로로 전환, 텍스트 파서는 구버전용으로 유지 |
| 8 | 단일 exe 크기 | 약 60~70MB (esptool 포함 시 +10MB) | 사내 공유에 부담되면 esptool 분리(결정 3) |
| 9 | .NET 10 설치 여부 | self-contained 라 불필요 | |

---

## 부록 A. 콘솔 입출력 샘플 전문

앱이 보내는 바이트는 `→`, 받는 바이트는 그대로(줄 끝은 `\r\n`). dumb 모드라 보낸 글자가 echo 됩니다. 실제 스트림에는 프롬프트 뒤 공백과 ANSI 색 escape 가 섞여 있으므로(3.2) 아래는 ANSI 를 제거한 모습입니다. Fixture 파일은 **원본(escape 포함)** 과 제거본을 둘 다 둡니다.

### A-1. `cfg` (브리지 0.5.5, 저장된 설정, 편집 중 변경 있음)

```
→ cfg\n
bridge> cfg
agv=7 ssid="AGV_WIFI" pass=(10 chars)
ip=192.168.10.107 mask=255.255.255.0 gw=192.168.10.1
dns=192.168.10.1 dns2=8.8.8.8
server=192.168.10.10:50000 local_port=50000
rfid_dst=255.255.255.255:50001
main_baud=115200 rfid_baud=9600 hb_ms=1000
state=saved
* differs from running config - 'save' then 'reboot' to apply
bridge>
```

변형: 0.5.4 이하는 `dns=` 줄이 없음. DHCP 면 `ip=dhcp`. 보조 DNS 없으면 `dns2=auto`. RFID 안 보내면 `rfid_dst=off:50001`. 공백 SSID 는 `ssid="AGV WIFI 2F"`. 저장 없음이면 `state=default`, 원격 확인 대기면 `state=trial`.

### A-2. `stat` (Wi-Fi 연결됨)

```
→ stat\n
bridge> stat
wifi: UP mac=a1:b2:c3:d4:e5:f6 ip=192.168.10.107 rssi=-55 ch=6 bssid=11:22:33:44:55:66
net : tx=1234 tx_err=0 tx_offline=0 rx=56 rx_bad=0 rx_foreign=0 disc=0 last_reason=0
setup: off name=AGVBR-07-E5F6
dsp : app=1.0.3 boot=1.1.0 build=2026-10-01 state=idle staged=-(job 0) pan=?
main: rx_frm=120 rx_B=4800 tx_frm=118 tx_B=4720 ovf=0 err=0 long=0 no_etx=0 resync=0 tx_drop=0
rfid: rx_frm=3 rx_B=36 tx_frm=0 tx_B=0 ovf=0 err=0 long=0 no_etx=0 resync=0 tx_drop=0
heap: 212345
bridge>
```

Wi-Fi 미연결(AP 없음): `wifi: DOWN mac=a1:b2:c3:d4:e5:f6 ip=0.0.0.0 rssi=0 ch=0 bssid=00:00:00:00:00:00`, `net :` 줄의 `disc=3 last_reason=201`. 0.5.2 이하는 `mac=` 토큰이 없음. 구 파티션: `dsp : … state=nopart (USB reflash needed) …`. `dsp` 줄의 `app` 은 메인보드 앱 버전이며 응답이 없으면 `?`.

### A-3. `ota` · `setup` · `set` · `save` · `reboot`

```
→ ota\n
bridge> ota
fw=0.5.5 part=ota_0 state=valid
bridge>
→ setup\n
bridge> setup
setup: off name=AGVBR-07-E5F6
bridge>
→ set ssid "AGV WIFI 2F"\n
bridge> set ssid "AGV WIFI 2F"
ok - 'save' then 'reboot' to apply
bridge>
→ set agv 100\n
bridge> set agv 100
invalid key or value
bridge>
→ save\n
bridge> save
saved
bridge>
→ reboot\n
bridge> reboot
ESP-ROM:esp32s3-20210327
Build:Mar 27 2021
rst:0xc (RTC_SW_CPU_RST),boot:0x8 (SPI_FAST_FLASH_BOOT)
… (부트로더 · 앱 로그)
I (…) cfg: loaded config from NVS
agv=7 ssid="AGV WIFI 2F" pass=(10 chars)
ip=192.168.10.107 mask=255.255.255.0 gw=192.168.10.1
dns=192.168.10.1 dns2=8.8.8.8
server=192.168.10.10:50000 local_port=50000
rfid_dst=255.255.255.255:50001
main_baud=115200 rfid_baud=9600 hb_ms=1000
state=saved
I (…) net: connecting to "AGV WIFI 2F" ...
… (0.5초 뒤, 터미널 probe 무응답 안내 3줄 — ESP-IDF 문구, 내용은 무시)
bridge>
I (…) net: got ip 192.168.10.107
```

`reboot` 뒤 프롬프트 복귀 전의 모든 출력은 부팅 로그로 취급합니다. 프롬프트가 온 뒤에도 Wi-Fi 로그가 뒤따라올 수 있으므로 `stat` 폴링으로 확인합니다. 메인보드 굽는 중이면 `reboot` 에 `busy: mainboard programming - reboot refused, retry when done` 이 오고 프롬프트가 바로 돌아옵니다.

### A-4. 저장된 설정 없는 첫 부팅 (전체 굽기 직후)

```
I (…) cfg: no saved config, using defaults
agv=1 ssid="AGV_TEST" pass=(8 chars)
ip=dhcp mask=255.255.255.0 gw=0.0.0.0
dns=auto dns2=auto
server=192.168.0.10:50000 local_port=50000
rfid_dst=255.255.255.255:50001
main_baud=115200 rfid_baud=9600 hb_ms=1000
state=default
I (…) net: connecting to "AGV_TEST" ...
W (…) setup: setup mode on - "AGVBR-01-E5F6"
W (…) setup_ble: advertising as "AGVBR-01-E5F6"
bridge>
```

## 부록 B. 저장 파일 JSON 예시

### B-1. `profiles.json`

```json
{
  "version": 1,
  "lastProfileId": "hq-f1",
  "profiles": [
    {
      "id": "hq-f1",
      "name": "본사 1공장",
      "notes": "DNS 는 사내 공통",
      "createdAt": "2026-10-17T09:00:00Z",
      "updatedAt": "2026-10-17T09:00:00Z",
      "lastUsedAt": null,
      "wifi": { "ssid": "AGV_WIFI" },
      "ipRule": { "mode": "offset", "baseNetwork": "192.168.10.0", "offset": 100 },
      "mask": "255.255.255.0",
      "gw": "192.168.10.1",
      "dns": "192.168.10.1",
      "dns2": "8.8.8.8",
      "server": "192.168.10.10",
      "serverPort": 50000,
      "localPort": 50000,
      "rfidDst": "255.255.255.255",
      "rfidPort": 50001,
      "mainBaud": 115200,
      "rfidBaud": 9600,
      "hbMs": 1000,
      "blePassword": "agvsetup"
    }
  ]
}
```

`ipRule.mode`: `offset`(기준 네트워크의 마지막 옥텟 = offset + agv) · `dhcp` · `manual`. Wi-Fi 비밀번호는 프로필에 두지 않고 `networks.json` 에서 SSID 로 찾습니다(현장이 달라도 같은 SSID 면 같은 비밀번호).

### B-2. `networks.json`

```json
{
  "version": 1,
  "networks": [
    { "ssid": "AGV_WIFI", "passwordDpapi": "AQAAANCMnd8B…", "open": false, "lastUsedAt": "2026-10-17T10:12:00Z", "useCount": 12 },
    { "ssid": "GUEST_OPEN", "passwordDpapi": null, "open": true, "lastUsedAt": null, "useCount": 0 }
  ]
}
```

내보내기 파일(`*.agvcfg.json`)은 `profiles` + (선택) `networks` 를 담고, 비밀번호 포함 시 `"password": "평문"` 으로 쓰며 파일 머리에 `"containsPasswords": true` 를 둡니다.

### B-3. `history.jsonl` (1줄 1건)

```json
{"at":"2026-10-17T10:15:32Z","profileId":"hq-f1","profileName":"본사 1공장","agv":7,"mac":"a1:b2:c3:d4:e5:f6","deviceName":"AGVBR-07-E5F6","ip":"192.168.10.107","ssid":"AGV_WIFI","fwBefore":"0.5.5","fwAfter":"0.5.5","channel":"serial","action":"apply","result":"ok","rssi":-55,"note":""}
{"at":"2026-10-17T10:31:02Z","profileId":"hq-f1","profileName":"본사 1공장","agv":8,"mac":"a1:b2:c3:d4:e5:01","deviceName":"AGVBR-08-E501","ip":"192.168.10.108","ssid":"AGV_WIFI","fwBefore":null,"fwAfter":"0.5.5","channel":"serial","action":"flash:A","result":"ok","rssi":null,"note":"새 보드"}
```

`action`: `apply` · `flash:A|B|C` · `restore` · `defaults` · `erase`. `result`: `ok` · `wifi_down:<reason>` · `invalid:<key>` · `save_failed` · `reboot_refused` · `timeout` · `cancelled` · `esptool:<code>`.

## 부록 C. 용어

| 용어 | 뜻 |
|---|---|
| 브리지 | ESP32-S3 + RS232 보드. AGV 메인보드 J4 와 게이트웨이 서버 사이를 Wi-Fi/UDP 로 중계 |
| 호기 (agv) | AGV 번호 1~99. 브리지 설정 키 `agv`, BLE 이름 · 패킷 헤더에 쓰임 |
| 게이트웨이 | 서버 PC 프로그램(AGV_FOTA_GATEWAY). UDP 50000 으로 브리지와 통신, 호기 현황 · OTA 배포 |
| 편집 사본 | `set` 으로 바뀌는 브리지 내부 임시 설정. `save` 전까지 실행 중 설정과 별개 |
| trial | 원격(UDP) `save` 의 확인 대기 상태. 재부팅 뒤 180초 안에 서버 패킷이 없으면 이전 설정으로 복구 |
| 설정 모드 | BLE(선택: SoftAP) 로 설정을 받는 상태. 이름 `AGVBR-NN-XXXX` |
| nopart | 구 파티션 표인 채 0.5.x 앱을 OTA 로 받은 상태. USB 로 파티션 포함 굽기 필요 |
| 프로필 | 한 현장의 공통 설정 묶음 (이 앱의 개념) |
| IP 규칙 | 호기 번호에서 IP 를 계산하는 규칙 (이 앱의 개념) |
| 방식 A/B/C | 전체 이미지 / 파티션 포함 4개 파일 / 앱만 굽기 |
