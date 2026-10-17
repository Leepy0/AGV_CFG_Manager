# AGV CFG Manager 개발 계획서 (Android)

ESP32-S3 AGV Wi-Fi 브리지([AGV_Wifi_Bridge](https://github.com/Leepy0/AGV_Wifi_Bridge))의 **호기 설정 · 확인 · 진단**을 휴대폰 한 화면에서 끝내는 Android 앱 계획서입니다. 현장에서 브리지에 접근하는 경로가 휴대폰 BLE 이므로, 이 앱은 BLE 터미널 앱에 명령을 한 줄씩 치던 일을 대신합니다.

- 문서 버전: 2.0 (2026-10-17, Android 기준으로 재작성) · 기준 브리지: **0.5.5** (`CMakeLists.txt` `PROJECT_VER`, DNS 키 포함) · 게이트웨이 0.3.0
- 독자: **이 앱을 구현할 개발 담당(AI 포함)**. 브리지 소스를 다시 분석하지 않아도 되도록 앱이 의존하는 브리지 사실을 3장에 모두 옮겨 적었습니다. 3장과 브리지 소스가 다르면 **소스가 맞습니다**. 그때는 이 문서를 고치세요.
- 함께 볼 문서: [BRIDGE_CHANGE_REQUESTS.md](BRIDGE_CHANGE_REQUESTS.md)(브리지에 요청할 변경), 저장소 루트의 `CLAUDE.md`(작업 규칙)
- 사용할 스킬: `field-app-ui-design`(모든 화면 작업 전, Android 절 포함)

## 목차

0. [읽는 순서와 작업 방식](#0-읽는-순서와-작업-방식)
1. [배경 · 목표 · 범위](#1-배경--목표--범위)
2. [사용 시나리오](#2-사용-시나리오)
3. [브리지 인터페이스 레퍼런스 (앱이 의존하는 사실)](#3-브리지-인터페이스-레퍼런스-앱이-의존하는-사실)
4. [기능 명세](#4-기능-명세)
5. [화면 설계](#5-화면-설계)
6. [아키텍처 · 데이터 모델](#6-아키텍처--데이터-모델)
7. [워크플로 상세](#7-워크플로-상세)
8. [Android BLE 구현 지침](#8-android-ble-구현-지침)
9. [오류 · 메시지 표](#9-오류--메시지-표)
10. [테스트 전략](#10-테스트-전략)
11. [개발 단계](#11-개발-단계)
12. [빌드 · 배포 · 앱 내 업데이트](#12-빌드--배포--앱-내-업데이트)
13. [브리지 변경 요청 요약](#13-브리지-변경-요청-요약)
14. [리스크 · 결정이 필요한 사항](#14-리스크--결정이-필요한-사항)
- [부록 A. BLE 입출력 샘플 전문](#부록-a-ble-입출력-샘플-전문)
- [부록 B. 저장 파일 JSON 예시](#부록-b-저장-파일-json-예시)
- [부록 C. 용어](#부록-c-용어)

---

## 0. 읽는 순서와 작업 방식

1. 1장 → 2장으로 "무엇을, 왜" 를 잡습니다.
2. 3장은 **구현 중 수시로 찾아보는 레퍼런스**입니다. 처음엔 3.1(설정 키)과 3.2(BLE 프로토콜)만 읽고 넘어갑니다.
3. 11장의 단계(Phase) 순서대로 구현합니다. 각 단계의 **완료 기준**을 모두 만족해야 다음 단계로 갑니다.
4. 화면을 만들기 전에 `field-app-ui-design` 스킬을 읽고, 5장의 화면 설계를 따릅니다.
5. 모호한 부분은 14장의 결정 사항을 먼저 확인하고, 거기에 없으면 **가장 단순한 쪽**을 고른 뒤 `docs/DECISIONS.md` 에 한 줄 기록합니다.
6. 하드웨어 없이도 전 기능을 돌릴 수 있도록 **가짜 브리지(FakeBridgeChannel)** 를 1단계에서 먼저 만듭니다. 로직은 Android 비의존 `:core` 모듈에 두어 JVM 에서 테스트합니다. 실기 확인은 각 단계의 "실기 체크" 항목으로 따로 합니다.
7. 라이브러리 버전(Kotlin, Compose BOM, AGP)은 작업 시점의 **stable** 을 확인해서 씁니다. Material 3 Expressive 가 alpha 면 쓰지 않습니다.

---

## 1. 배경 · 목표 · 범위

### 1.1 지금 상황

브리지 호기 설정은 세 경로(휴대폰 BLE · USB 콘솔 · 서버 원격 UDP)로 **같은 텍스트 명령**(`set agv 7` …)을 한 줄씩 입력합니다. 현장에서는 범용 BLE 터미널 앱(Serial Bluetooth Terminal 등)으로 `login agvsetup` 부터 10줄 안팎을 손으로 치고, 호기마다 반복합니다.

| 불편 | 구체적으로 |
|---|---|
| 반복 입력 | SSID · 비밀번호 · 마스크 · 게이트웨이 · DNS · 서버 IP · 포트 · 보드레이트는 **같은 현장이면 전부 같은 값**인데 호기마다 휴대폰 자판으로 다시 침 |
| 오타 · 누락 | IP 한 자리 틀림, `save` 안 하고 `reboot`, `mask` 빠짐 → 호기가 안 붙고 원인을 찾느라 시간 소모 |
| 비밀번호 관리 | Wi-Fi 비밀번호를 기억하거나 메모에서 복사. 브리지는 비밀번호를 되읽어 주지 않음(`pass=(8 chars)`) |
| 확인 | 재부팅 뒤 다시 스캔 · 연결 · `login` · `stat` 을 쳐서 `wifi: UP` 을 눈으로 확인 |
| 출장 | 해외 현장은 SSID · DNS · 서버 IP 가 다름. 어떤 값을 썼는지 기록이 흩어짐 |
| 터미널 앱 자체 | BLE 탭을 찾아 스캔해야 하고, 줄바꿈 설정 · notify 구독 같은 것을 알아야 함 |

### 1.2 목표

1. **호기 번호 하나만 입력하면 나머지는 자동으로 채워진다.** 현장 프로필(SSID · DNS · 서버 …)과 IP 규칙(`192.168.10.{100+호기}`)에서 계산. 휴대폰이 지금 붙어 있는 Wi-Fi 에서 게이트웨이 · DNS · 마스크를 읽어 새 프로필의 초깃값으로 쓴다.
2. **저장할 수 있는 값은 모두 저장하고 다시 쓴다.** SSID ↔ 비밀번호 라이브러리, 현장 프로필, 작업 이력(MAC ↔ 호기). 프로필은 QR · 파일로 다른 휴대폰에 옮긴다.
3. **실수를 앱이 막는다.** 브리지와 같은 검증 규칙, 호기 번호 중복 경고, `login` → `set` → `cfg` 대조 → `save` → `reboot` → 재연결 → `wifi: UP` 확인까지 자동.
4. **여러 대를 연속으로.** 첫 설치 보드는 모두 `AGVBR-01-…` 로 광고하므로, 가장 가까운(신호 센) 보드를 골라 호기 +1 로 이어서 설정한다.
5. **인터넷이 없어도 된다.** 프로필 · 라이브러리는 휴대폰 안에. 앱 업데이트만 github.com.

### 1.3 범위

| 포함 | 제외 (이번 계획에서) |
|---|---|
| BLE 로 설정 읽기 · 쓰기 · 재부팅 · 확인 | **펌웨어 굽기(USB · esptool)** — PC 에서 `flash.bat` 으로 (브리지 `docs/FLASH_GUIDE.md` 3장). 앱은 안내만 |
| 현장 프로필 · SSID 라이브러리 · IP 규칙 · 이력 | 브리지 OTA · 메인보드 OTA 조작 (게이트웨이 화면이 담당) |
| 휴대폰 Wi-Fi 정보 · 스캔을 이용한 자동 채움 | 서버 원격(UDP) — 브리지가 `server` IP 에서 온 패킷만 받으므로 휴대폰에서는 불가 |
| 진단 (콘솔, Wi-Fi 상태 해석, 게이트웨이 도달 확인) | iPhone (14장 결정 사항 1) |
| (선택) USB OTG 케이블로 시리얼 콘솔 연결 | |
| 사내망 앱 내 업데이트(APK) | |

### 1.4 전제와 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 플랫폼 | **Android, Kotlin, Jetpack Compose + Material 3 (stable)**, minSdk 26(Android 8.0), targetSdk 최신 stable | 현장 표준 경로가 휴대폰 BLE. 브리지 README 가 Android 터미널 앱을 1순위로 안내 |
| 기본 연결 경로 | **BLE(NUS)** 1순위 → USB OTG 시리얼(선택) | BLE 는 설정 모드가 열려 있어야 함(첫 설치는 자동, 이후 BOOT 3초). USB OTG 는 설정 모드 없이도 되지만 케이블 · 호스트 지원 휴대폰 필요 |
| 모듈 | `:core`(순수 Kotlin/JVM: 모델 · 파서 · 검증 · 워크플로 · 가짜 장치) + `:app`(Android: BLE · UI · 저장소) | JVM 단위 테스트가 빠르고, BLE 없이 로직을 검증 |
| 브리지 수정 | 앱은 **현재 브리지(0.5.5)와 수정 없이 동작**해야 함. 13장의 변경 요청이 반영되면 더 단순한 경로를 쓰되, 반영 전에도 텍스트 파싱 + 유휴 시간으로 응답 끝을 판정해 동작 | 호기마다 브리지 버전이 다를 수 있음 |
| 배포 | GitHub Releases 의 APK + 앱 내 업데이트(매니페스트는 github.com 주소). Play 스토어 미사용 | 사내망은 api.github.com 차단, github.com 허용 |
| 언어 | UI · 문서 · 주석 한국어, 식별자 영어 | 브리지 저장소와 동일 |
| 앱 이름 | 프로그램 `AGV CFG Manager`, 패키지 `kr.agv.cfgmanager`(14장 결정 사항 7), APK `AgvCfgManager.apk` | |

---

## 2. 사용 시나리오

각 시나리오의 **사용자가 손으로 하는 일**만 굵게 표시했습니다. 나머지는 앱이 합니다.

### S1. 새 보드 첫 세팅 (현장)

1. 보드는 PC 에서 미리 구워 둔 상태(설정 없음). 전원을 넣으면 설정 모드가 자동으로 열려 `AGVBR-01-A1B2` 로 광고한다.
2. 앱 홈에서 **[보드 찾기]**. 목록에 `AGVBR-01-A1B2 · 신호 강함 · 설정 없음(01)` 이 뜬다. **가장 가까운 것을 누른다.**
3. 앱이 연결 → notify 구독 → 환영 문구에서 이름 · fw 읽기 → `login agvsetup`(프로필의 BLE 비밀번호) → `cfg` · `stat` 으로 장치를 읽는다.
4. 설정 화면: 프로필 "본사 1공장" 이 이미 선택돼 있다. **호기 번호 7 입력** → IP `192.168.10.107`, SSID · 비밀번호 · 마스크 · GW · DNS · 서버 · 포트 · 보드레이트 전부 채워짐.
5. 하단 **[적용]** → `set …` 16줄 → `cfg` 로 되읽어 대조 → `save` → `reboot` → 연결 끊김 → 다시 광고되는 같은 보드(BLE 주소 동일, 이름은 `AGVBR-07-A1B2` 로 바뀜)에 재연결 → `login` → `stat` 폴링 → `wifi: UP ip=192.168.10.107`.
6. 완료 카드: "07호기 · AGVBR-07-A1B2 · 192.168.10.107 · Wi-Fi 연결됨 RSSI -52". 이력에 기록. **[설정 모드 닫기]**(= `setup off`) 또는 그냥 끊으면 Wi-Fi 연결 상태로 5분 뒤 자동으로 닫힌다.

### S2. 여러 대 연속 세팅

1. S1 의 6 에서 **[다음 호기]**. 호기 번호가 8 로 올라가고 보드 목록으로 돌아간다. 방금 끝낸 보드는 `AGVBR-07-…` 로 표시되고 "설정 완료" 표식이 붙는다.
2. **다음 보드(`AGVBR-01-…`, 신호 강한 것)를 누른다.** 연결 · 로그인 · 읽기 후 8호기 값이 채워진 채 **[적용]** 만 누른다.
3. 이미 설정된 보드(`cfg=saved`, 이름이 01 이 아님)를 누르면 경고 배너("이미 12호기로 설정된 보드입니다") 와 함께 자동 채움을 멈춘다.

### S3. 운용 중 호기 재설정 (서버 IP 변경 등)

1. AGV 를 세우고 브리지 보드의 **BOOT 버튼을 3초** 누른다 → 설정 모드가 열린다(또는 게이트웨이에서 `setup on`).
2. **[보드 찾기]** → `AGVBR-12-…` 선택 → 읽기 → 설정 화면에 장치의 현재값이 채워진다(비밀번호는 "(8자, 유지)").
3. **서버 IP 만 수정** → 변경 1건 표시 → **[적용]**. 비밀번호는 보내지 않으므로 그대로 유지된다. BLE 저장은 즉시 확정이라 원격(UDP)처럼 180초 대기가 없다.

### S4. 해외 현장

1. 출장 전: **[내보내기]** 로 프로필 · 라이브러리를 파일 또는 QR 로 저장(다른 휴대폰에도 옮김).
2. 현장: 휴대폰을 현장 Wi-Fi 에 붙인 뒤 **새 프로필 "OO공장"** → **[현재 Wi-Fi 에서 가져오기]** 로 SSID · 게이트웨이 · 마스크 · DNS 가 채워진다. 서버 IP · IP 규칙 · Wi-Fi 비밀번호만 입력. 한 번만.
3. 이후는 S1 · S2 와 같다. 이력이 프로필별로 남는다.

### S5. 문제 진단

1. "게이트웨이에 안 보여요": 진단 화면 → `stat` 해석 카드: `wifi: DOWN last_reason=201` → "AP 를 찾지 못함: SSID 철자, 2.4GHz, 채널 1~13 확인". 휴대폰 Wi-Fi 스캔 결과에 그 SSID 가 보이는지 함께 표시.
2. `wifi: UP` 인데 안 보이면: 앱이 휴대폰에서 게이트웨이 PC(`server`:8080)에 HTTP 로 닿는지 확인, `rx_foreign` 증가 → "브리지 `server` 가 게이트웨이 PC IP 와 다름" 안내. 콘솔 화면에서 직접 명령도 가능. **[게이트웨이 화면 열기]** 로 브라우저에서 호기 현황 확인.

### S6. (선택) USB OTG 로 설정

설정 모드를 열 수 없거나 BLE 가 불안정할 때, OTG 케이블로 휴대폰과 보드의 UART 쪽 USB 를 연결한다. 같은 설정 화면, 같은 적용 절차(콘솔 규약은 3.3).

---

## 3. 브리지 인터페이스 레퍼런스 (앱이 의존하는 사실)

출처: 브리지 저장소 `main/app_config.c`(검증 규칙), `main/cmd.c`(BLE · UDP 공용 명령), `main/setup_ble.c`(BLE 전송), `main/setup.c`(설정 모드 · 로그인), `main/console_cmd.c`(USB 콘솔), `main/net_bridge.c`(stat 줄), `docs/FLASH_GUIDE.md`(굽기 · 문제 해결). 이 환경에서는 `../AGV_Wifi_Bridge` 에 체크아웃돼 있습니다.

### 3.1 설정 키 (16개)

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

- IP 는 10진 dotted-quad 입니다. **브리지 `apply()` 는 `sscanf("%3u.…")` 로 읽어 앞자리 0 (`192.168.010.1`) 을 받아들입니다**(10진 해석이라 값이 바뀌지 않음). 앱 `ConfigValidator` 는 입력 실수를 막으려고 앞자리 0 을 거부합니다(앱 정책). 각 옥텟 0~255. 되읽기는 값으로 비교하므로 어긋나지 않습니다.
- `set` 은 **편집 사본**만 바꿉니다. `save` 로 NVS 에 쓰고 **재부팅해야 적용**됩니다. 편집 사본은 재부팅하면 사라집니다(저장 전이면 아무 영향 없음).
- 편집 사본을 되돌리는 명령은 **없습니다** (변경 요청 BR-3). 그때까지 앱은 "실패하면 그냥 `save` 를 보내지 않는다" 로 안전합니다.
- BLE 의 `save` 는 **즉시 확정**(현장 저장)이며, 서버 원격이 남긴 확인 대기(trial)도 취소합니다.

### 3.2 BLE (Nordic UART Service) 프로토콜 — 1순위

| 항목 | 사실 |
|---|---|
| 광고 조건 | **설정 모드가 열려 있을 때만** BT 컨트롤러를 켜고 광고. 닫히면 보이지 않음 |
| 설정 모드 열림 | 저장 설정 없이 부팅(첫 설치, `defaults` 뒤) · **BOOT 버튼(GPIO0) 3초** · USB 콘솔/서버 원격 `setup on` · **BLE 에서 `reboot` 한 직후**(재부팅 뒤 다시 열림) |
| 닫힘 | `setup off` · 휴대폰 연결 없이 **Wi-Fi 연결 상태로 5분** · 콘솔/전원 재부팅(저장 설정 있을 때). Wi-Fi 미연결이면 유일한 경로라 닫지 않음 |
| 광고 이름 | `AGVBR-<호기 2자리>-<STA MAC 끝 2바이트 4자리 대문자 hex>` 예 MAC `…:d4:a1:b2` 이면 `AGVBR-07-A1B2` (펌웨어 `setup_prepare_wifi()` 가 `mac[4]`,`mac[5]` 를 씀).  광고 패킷에 Flags + 이름, 스캔 응답에 NUS 서비스 UUID. 광고 간격 100~150ms |
| BLE 주소 | 공개 주소(public). 재부팅해도 같음 → **재연결은 이름이 아니라 주소로** (호기 번호가 바뀌면 이름이 바뀜) |
| 서비스 UUID | `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` |
| RX (앱 → ESP) | `6E400002-…` Write / Write Without Response. 수신 누적 버퍼 **256B**(넘치면 버림) |
| TX (ESP → 앱) | `6E400003-…` Notify. CCCD(`00002902-…`) 구독 필요. 청크 = **MTU − 3** 바이트, `\n` → `\r\n` 변환, 최대 1200B |
| 연결 | **1대만**(두 번째 휴대폰은 연결 불가). 페어링 · 본딩 없음(앱 수준 비밀번호). 연결 직후 브리지가 연결 간격 45~75ms · supervision 4초를 요청 |
| 환영 문구 | notify 구독이 감지되면 1회: `AGV Wi-Fi Bridge AGVBR-07-A1B2 (fw 0.5.5)\r\nlogin <password>\r\n` (빌드에 비밀번호가 없으면 둘째 줄 `type help`). **이름 · fw 는 여기서 파싱** |
| 로그인 | `login agvsetup`(빌드 기본값, 프로필에 보관) → `ok - type help`. 틀리면 **1초 뒤** `wrong password`. **같은 연결에서 5회 연속 실패 시** `wrong password - too many failures, disconnecting` 후 끊김(다시 연결하면 재시도 가능). 로그인 전 명령 → `login required: login <password>`. 연결마다 다시 로그인 |
| 줄 처리(입력) | CR · LF · CRLF 모두 줄 끝. 줄바꿈 없이 끝난 입력은 마지막 수신 **0.3초 뒤** 한 줄로 처리. 빈 줄은 무시. 한 줄은 **199바이트**까지 (브리지 `sble_take_line` 이 `CMD_LINE_MAX`(200) − NUL 로 잘라 넘치는 값은 조용히 깨짐). |
| 값 인용 | **따옴표 없음.** `set <key> <value>` 의 값은 **줄 끝까지**(공백 포함). 앞뒤 공백은 잘림 |
| 응답 | 명령마다 결과 텍스트 1덩어리(최대 600B)를 MTU 청크로 연속 송신. **응답 끝 표시가 없음** → 앱은 "첫 청크 뒤 250ms 동안 새 청크 없음" 또는 명령별 종료 규칙(3.2.1)으로 끝을 판정, 최대 3초. 변경 요청 BR-8 |
| 명령 집합 (`cmd.c`, `CMD_SRC_LOCAL`) | `cfg` · `set` · `save`(즉시 확정) · `defaults`(가능) · `reboot` · `stat` · `setup [on\|off]` · `dsp [ver\|apply]` · `help`. **`log` · `mon` · `ota` 는 콘솔 전용** |
| `reboot` | 응답 `rebooting` 을 보낸 뒤 약 **1초 후** 재시작, 연결 끊김. 재부팅 뒤 **설정 모드가 다시 열려** 재연결 가능(이름은 새 호기 번호). 메인보드 굽는 중이면 `busy: mainboard programming - reboot refused, retry when done` 이고 재시작 안 함 |
| 주의 | BLE 가 켜진 동안 Wi-Fi 와 무선부를 나눠 써 중계 지연 증가 → 운용 중 호기는 **AGV 정지 상태에서** |

#### 3.2.1 명령별 응답 형식 (BLE · UDP 공용, `cmd.c`)

| 명령 | 응답 (정확한 형식) | 끝 판정 보조 규칙 |
|---|---|---|
| `cfg` | 부록 A-2. `key=value` 토큰 7~8줄, 마지막 `state=saved\|default\|trial`, 편집 사본이 실행 중 설정과 다르면 `* differs from running config - 'save' then 'reboot' to apply` 한 줄 추가 | `state=` 줄 뒤 250ms 유휴 |
| `set <key> <value>` | `ok <key> - 'save' then 'reboot' to apply` / `invalid key or value: <key>` / 인자 없음 `usage: set <key> <value>` + 키 목록 2줄 | 한 줄(`\r\n`) |
| `save` | `saved - 'reboot' to apply` / `save failed: <ESP_ERR_…>` | 한 줄 |
| `defaults` | `saved config erased - 'reboot' to use build defaults` / `erase failed: …` | 한 줄 |
| `reboot` | `rebooting` / `busy: mainboard programming - reboot refused, retry when done` | 한 줄, 이후 끊김 |
| `stat` | 1줄 `fw=0.5.5 part=ota_0 ota=valid cfg=saved` + `wifi: UP\|DOWN mac=… ip=… rssi=… ch=… bssid=…` + `net : tx=… tx_err=… tx_offline=… rx=… rx_bad=… rx_foreign=… disc=… last_reason=…` + `setup: on name=… ble=connected` + `dsp : app=… boot=… build=… state=… staged=…(job n) pan=?` (콘솔과 달리 main/rfid/heap 줄 없음) | `dsp :` 줄 뒤 유휴 |
| `setup` / `setup on` / `setup off` | `setup: on name=AGVBR-07-A1B2 ble=connected` 꼴 한 줄 (`setup off` 뒤 곧 끊김). 닫힌 상태는 `setup: off name=…`, 열리는 중이면 `setup: opening name=…`, 빌드에서 뺐으면 `setup: unavailable` | 한 줄 |
| `dsp` | `dsp : …` 한 줄. `state=nopart (USB reflash needed)` 면 구 파티션 | 한 줄 |
| `help` | `cfg \| set <key> <value> \| save \| reboot \| stat \| setup [on\|off] \| dsp [ver\|apply] \| defaults` 한 줄 | 한 줄 |
| 모르는 명령 | `unknown command '<cmd>' - type help` | 한 줄 |

MAC 은 소문자 `aa:bb:cc:dd:ee:ff` 입니다(문서 그림은 대문자지만 코드는 소문자). 앱은 소문자로 정규화합니다. 0.5.2 이하 `stat` 에는 `mac=` 토큰이 없고, 그때는 BLE 이름의 끝 4자리만 알 수 있습니다.

### 3.3 USB 시리얼 콘솔 — 선택 기능(USB OTG)용

| 항목 | 값 |
|---|---|
| 포트 | DevKitC-1 의 **"UART" 라고 적힌 USB** (CP210x `VID 0x10C4 PID 0xEA60` 또는 CH343 `VID 0x1A86 PID 0x55D3`). "USB" 쪽(`VID 0x303A`)은 명령 입력 불가 |
| 속도 | 115200 8N1 |
| DTR/RTS | 열 때 둘 다 false. 열 때 리셋되는 경우가 있으니 부팅 로그가 보이면 프롬프트 복귀까지 기다림 |
| 프롬프트 | `bridge> ` — ESP-IDF REPL 이 뒤에 공백을 붙이고 ANSI 색 escape 가 앞뒤에 올 수 있음. **ANSI(`\x1b\[[0-9;]*[A-Za-z]`) 제거 후 `bridge>\s*$`** 로 판정 |
| 줄 끝 | 입력 `\n` 하나만(`\r\n` 은 빈 줄 하나 추가). 출력 `\r\n`. dumb 모드라 입력이 echo 됨 |
| 인용 | argv 토큰이므로 **공백 값은 큰따옴표**, `"` `\` 는 `\"` `\\` 로. 최대 160자 |
| 명령 | BLE 집합 + `ota`(`fw=… part=… state=…`) · `log` · `mon`. 콘솔 `stat` 에는 fw 줄이 없고 `main:` `rfid:` `heap:` 줄이 있음. `set` 성공은 `ok - 'save' then 'reboot' to apply`, 실패 `invalid key or value`, `save` 는 `saved` |
| 로그 섞임 | `^[IWEDV] \(\d+\) ` 줄은 결과에서 제외 |
| `reboot` | 출력 없이 즉시 재시작. 프롬프트 재등장까지 대기(3~6초). 콘솔 `reboot` 뒤에는 설정 모드가 다시 열리지 않음 |

### 3.4 서버 원격 설정 (UDP) — 참고만

브리지는 **설정된 `server` IP 에서 온 패킷만** 처리하므로 휴대폰에서는 쓸 수 없습니다. 게이트웨이 화면의 "통신 로그 → 수동 전송" 으로 같은 명령을 보낼 수 있고, 원격 `save` 는 확인 대기(trial)로 저장되어 재부팅 뒤 180초 안에 서버 패킷이 없으면 복구됩니다. 앱은 `cfg` 의 `state=trial` 을 보면 "서버 원격 설정 확인 대기 중 — BLE 에서 저장하면 즉시 확정됩니다" 로 안내만 합니다.

### 3.5 펌웨어 · 굽기 (앱 범위 밖, 안내용)

- 새 보드는 PC 에서 `flash.bat COM5`(전체 이미지, 설정 지워짐) 로 굽습니다. 이후 버전은 게이트웨이 배포 작업(OTA)으로 올립니다. 앱은 fw 가 오래되면 "게이트웨이 배포 작업으로 업데이트" 를 안내합니다.
- 0.5.0 미만 또는 `dsp` 상태 `nopart` 인 보드는 USB 로 파티션 포함 굽기가 필요합니다(FLASH_GUIDE 3-2). 앱은 `nopart` 를 보면 이 안내를 띄웁니다.
- 최신 버전 조회(선택): `https://github.com/Leepy0/AGV_Wifi_Bridge/releases/latest` 를 리다이렉트 따라가지 않고 요청 → `Location` 의 태그. api.github.com 은 쓰지 않습니다.

### 3.6 버전 호환 매트릭스

앱은 **기능 유무를 출력으로 감지**하고(예: `cfg` 에 `dns=` 줄이 있는지), 버전 문자열은 표시에만 씁니다.

| 브리지 | 특징 | 앱 대응 |
|---|---|---|
| < 0.5.0 | BLE 설정 없음(0.4.x 이하 일부는 SoftAP 웹 페이지) | BLE 로는 못 봄. USB OTG(선택) 또는 PC 콘솔 안내 |
| 0.5.0 ~ 0.5.1 | BLE 설정 있음. 로그인 실패 5회 제한 없음, 원격 저장 문구 다름 | 동일 동작 |
| 0.5.2 | 현재 응답 문구 확정 | |
| 0.5.3 | `stat` wifi 줄에 `mac=` | MAC 을 이력에 기록 가능 |
| 0.5.4 | 콘솔 `log` 명령 | USB OTG 에서만 의미 |
| **0.5.5** (현재) | `dns` · `dns2` 키, `cfg` 에 `dns=… dns2=…` 줄 | DNS 입력란 활성 |
| OTA 로 0.5.x 를 받은 구 파티션 보드 | `dsp` 상태 `nopart` | USB 굽기 안내 |

### 3.7 브리지 소스 참조 표

| 알고 싶은 것 | 파일 · 위치 |
|---|---|
| 키별 검증 규칙 | `main/app_config.c` `apply()` |
| `cfg` 출력 형식 | `main/app_config.c` `cfg_format()` |
| BLE · UDP 명령 · 응답 문자열 | `main/cmd.c` |
| BLE UUID · 청크 · 줄 처리 | `main/setup_ble.c` |
| 환영 문구 · 로그인 · 실패 제한 · 재부팅 후 재열기 | `main/setup.c` `ble_poll()` `setup_reboot()` |
| 설정 모드 열림 · 닫힘 · 유휴 종료 | `main/setup.c` `setup_task()`, `Kconfig.projbuild` `BR_SETUP_TIMEOUT_S`(300) |
| `stat` 의 wifi/net 줄 | `main/net_bridge.c` `net_format_status()` |
| 콘솔 명령 · 응답 | `main/console_cmd.c` |
| 굽기 · 문제 해결 | `docs/FLASH_GUIDE.md` 3 · 9장 |
| Wi-Fi 끊김 사유 코드 | FLASH_GUIDE 9장 + ESP-IDF `wifi_err_reason_t` (9.3 표) |
| 빌드 기본값 | `main/Kconfig.projbuild` |

---

## 4. 기능 명세

번호 규칙: `F-<영역><번호>`. 우선순위 **필수**(없으면 목표 미달) / **권장**(반복 제거 · 실수 방지에 효과 큼) / **선택**(여유 있을 때). 구현 단계는 11장.

### 4.1 보드 찾기 · 연결 (C)

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-C1 | 필수 | 권한 · 블루투스 준비 | Android 12+: `BLUETOOTH_SCAN`(neverForLocation) · `BLUETOOTH_CONNECT`. 11 이하: `BLUETOOTH` · `BLUETOOTH_ADMIN` · `ACCESS_FINE_LOCATION` + 위치 서비스 켜짐 확인. 블루투스 꺼짐 → 켜기 요청 화면. 거부 시 이유와 설정 열기 버튼(Empty/Error 상태) |
| F-C2 | 필수 | 스캔 목록 | NUS 서비스 UUID 필터 + 이름 `AGVBR-` 접두어. 항목: 이름 · 호기(이름에서) · 신호(강/중/약 + dBm) · "설정 없음(01)" / "이력에 07호기" 표식. RSSI 내림차순, 2초마다 갱신. 10초 뒤 자동 중지 + [다시 찾기]. 비어 있으면 "보이지 않을 때: BOOT 버튼 3초 → 설정 모드" 안내 |
| F-C3 | 필수 | 연결 · 로그인 · 읽기 | 연결 → MTU 요청 → 서비스 탐색 → TX notify 구독 → 환영 문구 파싱(이름 · fw) → `login <프로필 BLE 비밀번호>` → `cfg` · `stat`. 실패 사유 구분: 다른 휴대폰이 연결 중 / 비밀번호 틀림(남은 횟수) / 설정 모드 닫힘 / GATT 133 |
| F-C4 | 필수 | 연결 상태 상시 표시 | 상단 칩: 끊김 / 찾는 중 / 연결 중 / 연결됨(이름 · fw · cfg 상태) / 재부팅 대기. "마지막 읽기: n초 전". 연결 중 화면 꺼짐 방지 |
| F-C5 | 필수 | 재연결 | 끊기면 BLE **주소**로 30초 동안 재스캔 · 재연결 · 재로그인(사용자가 끊은 경우 제외). `reboot` 뒤 자동 |
| F-C6 | 권장 | 설정 모드 닫기 | 완료 후 [설정 모드 닫기](`setup off`) 버튼. 끊기만 하면 Wi-Fi 연결 상태에서 5분 뒤 자동으로 닫힌다는 안내 |
| F-C7 | 선택 | USB OTG 시리얼 | `usb-serial-for-android` 로 CP210x · CH34x. 같은 `IBridgeChannel`. 3.3 규약. 장치 연결 인텐트로 자동 전환 |

### 4.2 설정 편집 (E)

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-E1 | 필수 | 설정 폼 | 3.1 의 16개 키. 그룹: 호기 / Wi-Fi / 네트워크(IP·마스크·GW, 고급: DNS·DNS2) / 서버(IP·포트·수신 포트) / 고급(RFID 송신·포트, 보드레이트 2개, hb_ms). 고급 그룹은 접힘 기본. 숫자 · IP 란은 숫자 자판(`KeyboardType.Number`/`Decimal`), SSID · 비밀번호는 자동 대문자 · 자동 완성 끔 |
| F-E2 | 필수 | 장치값 ↔ 적용값 대조 | 장치에서 읽은 값과 폼의 값이 다르면 항목 옆에 "변경" 표식(아이콘 + 텍스트), 하단에 "변경 n개". 비밀번호는 장치값 "(8자)" 로 표시하고 폼이 비어 있으면 **유지** |
| F-E3 | 필수 | 입력 검증 | 3.1 규칙을 입력 즉시 검사해 항목 아래 한 줄 안내. 전부 통과해야 [적용] 활성. 추가 검사: IP · GW · 서버가 같은 서브넷인지(마스크 기준, 경고만), IP 가 GW · 서버와 같지 않은지, 마지막 옥텟 0/255 금지(경고) |
| F-E4 | 필수 | 프로필 자동 채움 | 프로필 선택 → 호기 번호 외 전부 채움. 호기 번호 바꾸면 IP 규칙으로 IP 재계산(사용자가 IP 를 직접 고친 뒤에는 재계산하지 않고 "규칙과 다름" 표시 + [규칙값으로]) |
| F-E5 | 필수 | 비밀번호 처리 | SSID 입력/선택 시 라이브러리에서 자동 채움. 표시/숨김 토글. 라이브러리에 없고 장치에도 없으면 필수 입력. `-`(오픈 AP) 선택지 |
| F-E6 | 권장 | 호기 번호 보조 | 프로필 내 이력의 다음 번호 제안(최대값+1). 이력에 같은 번호가 **다른 MAC/BLE 주소**로 있으면 경고 배너("07호기는 2026-10-02 에 다른 보드에 설정했습니다. 교체라면 계속") |
| F-E7 | 권장 | DHCP 모드 | IP 모드: 규칙 / DHCP / 직접 입력. DHCP 선택 시 "게이트웨이는 고정 IP 권장(ACS agvOpCtrl 과 일치 필요)" 안내 |
| F-E8 | 권장 | 장치값으로 되돌리기 | 폼 전체 또는 항목별 |
| F-E9 | 권장 | 휴대폰 Wi-Fi 에서 가져오기 | 휴대폰이 붙은 Wi-Fi 의 SSID · 게이트웨이 · prefix → 마스크 · DNS 를 폼/프로필에 채움(`ConnectivityManager.getLinkProperties`, SSID 는 위치 권한 필요). "브리지는 2.4GHz 만" 경고(휴대폰이 5GHz 에 붙어 있으면 같은 SSID 의 2.4GHz 가 있는지 확인하라고 안내) |
| F-E10 | 선택 | 휴대폰 Wi-Fi 스캔 | `WifiManager` 스캔 결과를 SSID 콤보에 "근처에서 보임 · 2.4GHz · WPA2" 로 표시(Android 스캔 횟수 제한: 포그라운드 2분에 4회) |

### 4.3 현장 프로필 (P)

프로필 = 한 현장(또는 한 Wi-Fi 망)에서 호기 공통으로 쓰는 값 묶음. **반복 제거의 핵심.**

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-P1 | 필수 | 프로필 CRUD | 이름, 메모, 3.1 의 `agv` 제외 전 항목 + IP 규칙 + BLE 비밀번호(기본 `agvsetup`). 편집 화면. 삭제는 확인(되돌릴 수 없음) |
| F-P2 | 필수 | 기본 프로필 | 첫 실행 시 "본사" 프로필을 **빌드 기본값 + 회사 고정값**으로 생성(실제 값은 14장 결정 사항 5 에서 확정, 그때까지 자리표시자). 마지막 사용 프로필 기억 |
| F-P3 | 필수 | IP 규칙 | `offset` 모드: 기준 네트워크 `192.168.10.0` + 오프셋 100 → 호기 7 = `.107`. 결과가 1~254 를 벗어나면 오류. `dhcp` / `manual` 모드. 편집 화면에 "호기 7 → 192.168.10.107" 예시 즉시 표시 |
| F-P4 | 권장 | 내보내기 · 가져오기 | JSON 파일(SAF `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`, 공유 시트). 비밀번호는 기본 제외, "비밀번호 포함(평문)" 체크 시 포함 + 경고. 같은 이름은 덮어쓸지 확인 |
| F-P5 | 권장 | QR 로 옮기기 | 프로필 1개를 QR 로 표시(비밀번호 제외 기본) / 카메라로 읽어 가져오기. 다른 휴대폰 · 출장지 공유용 |
| F-P6 | 권장 | 프로필 복제 | "본사" 를 복제해 "OO공장" 만들기 → SSID · 서버만 수정 |
| F-P7 | 선택 | 프로필별 요약 | 몇 대 설정했는지, 마지막 사용일 |

### 4.4 SSID 라이브러리 (W)

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-W1 | 필수 | SSID ↔ 비밀번호 저장 | 적용에 성공한 SSID/비밀번호를 자동 저장(체크 "이 비밀번호 저장", 기본 켬). Android Keystore AES-GCM 키로 암호화해 앱 내부 저장소에 보관(6.3) |
| F-W2 | 필수 | 자동 채움 | SSID 란은 입력 + 목록. 선택하거나 일치하는 SSID 를 입력하면 비밀번호 채움, 마지막 사용일 표시 |
| F-W3 | 권장 | 관리 | 목록 · 삭제 · 비밀번호 보기(토글, 기기 잠금 인증 `BiometricPrompt`/기기 자격 증명 요구 — 선택) |
| F-W4 | 선택 | 브리지 Wi-Fi 스캔 연동 | 변경 요청 BR-4(`scan`) 반영 시 **브리지가 보는** AP 목록을 표시(휴대폰 스캔과 다를 수 있음 — 브리지 위치 기준) |

### 4.5 적용 워크플로 (A) — 7.1 상세

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-A1 | 필수 | 적용 | `set` × n → `cfg` 대조 → `save` → `reboot` → 재연결 · 재로그인 → `stat` 확인. 진행 단계 표시 + 취소(`save` 전까지) |
| F-A2 | 필수 | 결과 카드 | 성공: 호기 · 장치 이름 · IP · RSSI · fw. 실패: 단계 · 원인 · 다음 행동(9장 표) |
| F-A3 | 필수 | 이력 기록 | 성공/실패 모두(4.7) |
| F-A4 | 권장 | 저장만 / 재부팅 없이 | "저장만(재부팅은 나중에)" — 메인보드 작업 중 `busy` 로 재부팅이 거부된 경우의 재시도 경로 |
| F-A5 | 권장 | 공장 초기화 | `defaults` + `reboot`. 위험 확인(대상 이름 표시, 동사 버튼 "07호기 설정 지우기") |
| F-A6 | 권장 | 적용 중 화면 유지 | `keepScreenOn`, 뒤로 가기 막음(취소 버튼으로만). 앱이 백그라운드로 가면 경고 알림(선택: 포그라운드 서비스 `connectedDevice` 로 보호) |

### 4.6 연속 세팅 (B)

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-B1 | 권장 | 연속 모드 | 토글. 적용 성공 후 호기 +1, 보드 목록으로 복귀. 목록에서 방금 끝낸 보드는 "완료" 표식, 설정 없는 보드(이름 01, 이력 없음)는 위로 |
| F-B2 | 권장 | 이미 설정된 보드 보호 | 읽은 보드가 `cfg=saved` 면 자동 채움 대신 경고 배너. "덮어쓰기" 를 눌러야 진행 |
| F-B3 | 선택 | 세션 요약 | 이번 연속 작업으로 설정한 호기 목록을 표로, 공유 시트로 내보내기 |

### 4.7 이력 (H)

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-H1 | 필수 | 기록 | 시각 · 프로필 · 호기 · MAC(0.5.3+) · BLE 주소 · 장치 이름 · IP · SSID · fw · 연결 종류 · 작업(적용/초기화) · 결과 · 메모. `history.jsonl` 에 추가만 |
| F-H2 | 권장 | 조회 | 목록(LazyColumn), 프로필 · 호기 · 이름 검색, 기간 필터. 항목 → 그때 값으로 폼 채우기 |
| F-H3 | 권장 | 내보내기 | CSV 공유(설치 보고용) |
| F-H4 | 선택 | 라벨 텍스트 | "AGV-07 · AGVBR-07-A1B2 · 192.168.10.107 · a1:b2:c3:d4:a1:b2" 클립보드 복사 |

### 4.8 진단 · 콘솔 (D)

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-D1 | 필수 | 콘솔 | 원문 입출력(고정폭), 입력줄 + 최근 명령, 끝 따라가기, 공유로 저장. 앱이 보내는 명령도 모두 표시(비밀번호 값은 `***`, `login` 의 비밀번호도 `***`) |
| F-D2 | 필수 | 상태 카드 | `stat` 해석: fw · 슬롯 · ota 상태 · cfg 상태(saved/default/trial) · Wi-Fi UP/DOWN · RSSI(좋음/보통/약함) · IP · 채널 · 끊김 사유 설명(9.3) · 송수신 카운터 · 설정 모드 · 메인보드 상태(`nopart` 면 USB 굽기 안내). [새로고침], 자동 5초 토글 |
| F-D3 | 권장 | 빠른 명령 버튼 | `stat` · `cfg` · `setup off` · `dsp` · `dsp ver` · `help` |
| F-D4 | 권장 | 게이트웨이 도달 확인 | 휴대폰이 같은 Wi-Fi 에 있을 때 `http://<server>:8080/` 에 HEAD/GET (3초) → "게이트웨이 PC 응답 있음/없음". [게이트웨이 화면 열기](브라우저). `wifi: UP` 인데 `rx=0` → "게이트웨이 화면에서 PING 을 보내 보세요", `rx_foreign` 증가 → server IP 불일치 안내 |
| F-D5 | 선택 | 부팅 확인 | `reboot` 뒤 재연결까지 걸린 시간, 첫 `stat` 결과를 이력 메모에 |

### 4.9 앱 공통 (G)

| ID | 우선 | 기능 | 세부 |
|---|---|---|---|
| F-G1 | 필수 | 설정 저장 | 마지막 프로필, 테마(라이트/다크/시스템), 자동 업데이트 확인 여부, 스캔 시간, 가짜 브리지 모드(디버그 빌드/개발자 옵션) |
| F-G2 | 필수 | 로그 파일 | 앱 내부 `files/logs/app-YYYYMMDD.log`, 7일 보관, 공유 시트로 내보내기. 비밀번호 절대 기록 금지 |
| F-G3 | 필수 | 앱 내 업데이트 | 12장. github.com 매니페스트 → APK 다운로드 → sha256 → 설치 화면 |
| F-G4 | 권장 | 출장 준비 | 한 번에: 프로필 · 라이브러리 내보내기 + 체크리스트(브리지 zip 은 PC 에서, BLE 비밀번호 확인) |
| F-G5 | 권장 | 정보 화면 | 버전 · 채널 · 지원 브리지 버전 범위 · 업데이트 확인 · 로그 내보내기 · 오픈소스 고지 |
| F-G6 | 권장 | 태블릿 레이아웃 | Window size class: Compact 는 하단 내비게이션, Medium 이상은 Navigation rail, Expanded 는 목록-상세 2패널(`NavigationSuiteScaffold`) |

---

## 5. 화면 설계

`field-app-ui-design` 스킬 규칙(Android 절)을 따릅니다: 화면당 주요 행동 1개, 상태 상시 표시, 주요 행동은 화면 하단 1/3, 48dp(장갑 현장 72dp) 타깃, 색은 상태에만, 라이트/다크 고정 색상표(dynamic color 끔), edge-to-edge 인셋 처리, `UiState`(Loading/Empty/Error/Content).

### 5.1 전체 구조

```
┌──────────────────────────────────────┐
│ AGV CFG Manager            ⓘ  ⚙      │  ← TopAppBar: 제목 + 보조 메뉴만
│ ● 연결됨 AGVBR-07-A1B2 · fw 0.5.5 · saved │  ← 연결 칩 (항상 표시, 탭 공통)
├──────────────────────────────────────┤
│                                      │
│              (탭 내용)               │
│                                      │
├──────────────────────────────────────┤
│  [적용 (저장 후 재부팅)]  ← 주요 행동, 하단 고정, 72dp │
├──────────────────────────────────────┤
│  보드   설정   진단   이력           │  ← NavigationBar 4개
└──────────────────────────────────────┘
```

- 탭 4개 고정: **보드**(찾기 · 연결) / **설정** / **진단** / **이력**. 프로필 · 라이브러리 · 정보 · 설정은 보조 화면(⚙ 또는 설정 탭의 프로필 행에서 진입).
- 하단 주요 행동 버튼은 탭마다 하나: 보드 탭 [보드 찾기], 설정 탭 [적용], 진단 탭 [새로고침], 이력 탭 없음(공유는 상단 메뉴).
- 제스처 바 · IME 인셋 처리: 주요 행동 버튼이 가려지지 않게 `Scaffold` innerPadding + `imePadding`.

### 5.2 보드 탭 (주요 행동: **보드 찾기**)

```
 찾는 중 … (8초)                          [중지]
 ─────────────────────────────────────────
 ▮▮▮ AGVBR-01-A1B2   설정 없음 · -48 dBm     ›   ← 신호 막대 + 텍스트
 ▮▮▯ AGVBR-01-C3D4   설정 없음 · -63 dBm     ›
 ▮▯▯ AGVBR-12-E5F6   이력: 12호기 (10-02)    ›
 ─────────────────────────────────────────
 안 보이면: 보드의 BOOT 버튼을 3초 누르면 설정 모드가 열립니다.
 설정 모드는 휴대폰 없이 Wi-Fi 연결 상태로 5분이 지나면 닫힙니다.
                     [ 보드 찾기 ]
```

- Empty: "보이는 보드가 없습니다" + 위 안내 + [다시 찾기]. Error: 권한/블루투스/위치 별 문구 + [설정 열기].
- 항목을 누르면 연결 시트: 연결 → 로그인 → 읽기 진행 표시(3단계), 실패 시 사유와 [다시 시도]. 성공하면 설정 탭으로 이동.
- 연속 모드가 켜져 있으면 상단에 "다음: 8호기 · 본사 1공장" 배너.

### 5.3 설정 탭 (주요 행동: **적용**)

```
 프로필  [본사 1공장 ▾]  [편집]
 호기 번호  [  7  ]  (−)(+)        ← 28sp, 숫자 자판, 포커스 기본
 ─────────────────────────────────────
 Wi-Fi
   SSID        [AGV_WIFI          ▾]   장치: AGV_WIFI
   비밀번호    [••••••••   ] 👁  ☑ 저장   장치: (8자) 유지
 네트워크
   IP 모드     (●) 규칙 ( ) DHCP ( ) 직접
   IP          [192.168.10.107]  규칙값     장치: 192.168.10.107
   마스크      [255.255.255.0 ]            장치: 255.255.255.0
   게이트웨이  [192.168.10.1  ]            장치: 192.168.10.1
   ▸ 고급  DNS 192.168.10.1 · 8.8.8.8      장치: auto · auto   ⚠ 변경
 서버
   서버 IP     [192.168.10.10 ]            장치: 192.168.0.10   ⚠ 변경
   포트 [50000]  수신 [50000]
 ▸ 고급  RFID 브로드캐스트:50001 · 115200 · 9600 · hb 1000
 ─────────────────────────────────────
 변경 3개 · 검증 통과        ☐ 연속 모드  ☐ 저장만
              [ 적용 (저장 후 재부팅) ]
```

- 연결 전에는 "장치:" 값이 "—" 이고 [적용] 비활성 + "보드 탭에서 연결하세요".
- 적용 중에는 폼이 비활성, 하단이 진행 패널: 단계 목록(설정 전송 12/16 → 되읽기 → 저장 → 재부팅 → 재연결 → Wi-Fi 확인) + 진행바 + [취소](저장 전까지만 활성).
- 완료 카드는 같은 자리: 성공은 중립색 + ✓, 실패는 알람색 + 원인 + [다시 시도] / [콘솔 보기]. 연속 모드면 [다음 호기 (8)] 가 기본 버튼, 아니면 [설정 모드 닫기].

### 5.4 진단 탭

상단 상태 카드(Wi-Fi · 네트워크 카운터 · 설정 모드 · 메인보드 · 설정 상태 · 게이트웨이 도달) + 아래 콘솔(고정폭, 입력줄, 빠른 명령 칩 한 줄). 카드의 끊김 사유는 코드 + 설명 + 할 일(9.3). RSSI 는 숫자 + 등급 텍스트 + 작은 막대. 주요 행동 [새로고침], 자동 5초 토글.

### 5.5 이력 탭

목록(시각 · 프로필 · 호기 · 장치 이름 · IP · 결과) + 검색/필터 + 상단 메뉴 [CSV 공유]. 항목 → 상세 시트(전체 값, [이 값으로 폼 채우기], [라벨 복사]).

### 5.6 보조 화면

| 화면 | 내용 |
|---|---|
| 프로필 편집 | 이름 · 메모 / [현재 Wi-Fi 에서 가져오기] / Wi-Fi(SSID, 비밀번호 저장 여부) / 네트워크(IP 규칙: 모드 · 기준 네트워크 · 오프셋 · 예시 계산, 마스크, GW, DNS, DNS2) / 서버 / 고급 / BLE 비밀번호. [복제] [QR 보기] [삭제] |
| 프로필 목록 | 목록 · 새로 만들기 · 가져오기(파일/QR) · 내보내기 |
| SSID 라이브러리 | 목록(SSID · 마지막 사용 · 사용 횟수) · 비밀번호 보기 · 삭제 |
| 정보 | 버전 · 채널 · 커밋 · 업데이트 상태 · [지금 확인] · [로그 내보내기] · 지원 브리지 버전 · 오픈소스 고지 |
| 설정 | 테마 · 자동 업데이트 · 스캔 시간 · 연속 모드 기본 · (디버그) 가짜 브리지 |
| 위험 확인 | 대상(장치 이름 · 호기) · 영향 · 복구 가능 여부, 동사 버튼, 취소 기본 |

### 5.7 테마

`ui/theme/` 에만 정의: `MaterialTheme(colorScheme, typography, shapes)`. dynamic color 끔, light/dark colorScheme 모두. 간격 4/8/12/16/24/32dp, M3 type scale, 중립 회색 + 강조 1색 + 상태 3색. 로그 · 숫자 열은 고정폭(`FontFamily.Monospace`). 화면 코드에 `Color(0xFF…)`, `16.sp` 하드코딩 금지.

---

## 6. 아키텍처 · 데이터 모델

### 6.1 프로젝트 구조

```
AGV_CFG_Manager/
├─ settings.gradle.kts  build.gradle.kts  gradle/libs.versions.toml   # 버전 카탈로그
├─ core/                                  # 순수 Kotlin/JVM 모듈 (Android 의존 없음)
│  └─ src/main/kotlin/…/core/
│     ├─ bridge/   IBridgeChannel.kt  BridgeCommands.kt  ResponseParsers.kt  BridgeConfig.kt  BridgeStatus.kt
│     │            DeviceSnapshot.kt  ConfigValidator.kt  WifiReason.kt  ResponseAssembler.kt(유휴/규칙 끝 판정)
│     ├─ profiles/ Profile.kt  IpRule.kt  ProfileCodec.kt(JSON)  ProfileExport.kt
│     ├─ workflows/ ApplyWorkflow.kt  BatchCoordinator.kt  WorkflowProgress.kt  ReadDevice.kt
│     ├─ history/  HistoryEntry.kt  HistoryCodec.kt(jsonl, CSV)
│     ├─ fake/     FakeBridgeChannel.kt  FakeDevice.kt    # 브리지 cmd.c 의미 그대로 흉내
│     └─ util/     Ipv4.kt  Masking.kt
│  └─ src/test/kotlin/…  (JUnit5/kotlin.test) + resources/fixtures/*.txt
├─ app/                                   # Android
│  └─ src/main/kotlin/…/
│     ├─ AgvApp.kt  MainActivity.kt  AppContainer.kt(수동 DI)
│     ├─ ble/      BleScanner.kt  BleBridgeChannel.kt  GattQueue.kt  BlePermissions.kt
│     ├─ usb/      UsbSerialBridgeChannel.kt (선택)
│     ├─ data/     ProfileStore.kt  NetworkStore.kt(Keystore 암호화)  HistoryStore.kt(jsonl 추가 기록, Phase 3)  SettingsStore.kt(DataStore)  KeystoreCipher.kt  AppLog.kt
│     ├─ session/  BridgeSession.kt(withExclusive · reboot · closeSetupMode)  ConsoleLog.kt  LoggedChannel.kt  BoardSource.kt  FakeBoards.kt  SessionState.kt
│     ├─ apply/    ApplyRunner.kt(진행 상태 · 결과 · 이력, 앱 수명 scope)  ApplyProgress.kt  ApplyMessages.kt(결과 카드 문구)
│     ├─ net/      PhoneWifiInfo.kt(LinkProperties)  GatewayProbe.kt  Updater.kt
│     ├─ ui/       theme/  navigation/  components/
│     │            boards/  config/(ConfigForm 순수 모델 · SettingsViewModel · 폼 · 진행 · 결과 카드)  diagnostics/  history/  profiles/  library/  about/  settings/
│     └─ qr/       QrEncode.kt  QrScanScreen.kt (선택)
│  └─ src/androidTest/…  (Compose UI 테스트, Fake 채널)
├─ .github/workflows/android.yml
└─ docs/  DEVELOPMENT_PLAN.md  BRIDGE_CHANGE_REQUESTS.md  DECISIONS.md
```

라이브러리(작업 시점 stable 확인): Kotlin, Coroutines, Compose BOM + Material 3, Navigation Compose, Lifecycle ViewModel Compose, `kotlinx.serialization`, DataStore(Preferences), OkHttp(업데이트 · 게이트웨이 도달), ZXing(`zxing-android-embedded`, QR 선택), `usb-serial-for-android`(선택), 테스트 JUnit5 · kotlin.test · Turbine(Flow). DI 프레임워크 없이 `AppContainer` 하나.

### 6.2 핵심 인터페이스

```kotlin
enum class ChannelKind { Ble, UsbSerial, Fake }

interface IBridgeChannel {
    val kind: ChannelKind
    val state: StateFlow<ChannelState>          // Disconnected / Connecting / Connected(deviceId, name, fw) / Rebooting
    val raw: SharedFlow<String>                 // 콘솔 화면용 원문 (마스킹은 호출 측)
    /** 명령 한 줄을 보내고 응답 텍스트(끝 판정 완료)를 돌려줌. 한 번에 하나만. */
    suspend fun exec(line: String, timeout: Duration = 3.seconds): String
    /** reboot 처럼 응답 뒤 끊기는 명령: 응답 한 줄만 받고 끊김을 허용. */
    suspend fun execThenDisconnect(line: String): String
    /** 재부팅 후 같은 장치가 다시 준비될 때까지(BLE: 주소로 재스캔·재연결·재로그인). */
    suspend fun waitReady(timeout: Duration): Boolean
    suspend fun disconnect()
}
```

- `BridgeCommands.set(key, value, kind)`: BLE/Usb 모두 `set key value`; UsbSerial 만 값에 공백 · `"` · `\` 가 있으면 따옴표 + 이스케이프. 길이 검사(BLE 200, 콘솔 160).
- `ResponseParsers.parseCfg(text) → BridgeConfig + state + differs`, `parseStat → BridgeStatus(fw, part, ota, cfg, wifi, net, setup, dsp)`, `parseBanner → (name, fw, needsLogin)`, `parseSetResult`, `parseSaveResult`. 토큰은 `key=value` 를 공백으로 나누되 `ssid="…"` 는 따옴표 안 공백을 보존. 모르는 토큰은 무시(상위 호환).
- `ResponseAssembler`: 수신 청크를 모아 (a) 명령별 종료 규칙(3.2.1) 또는 (b) 마지막 청크 뒤 250ms 유휴로 완료. 최대 3초. BLE 채널이 사용, Usb 채널은 프롬프트 규칙을 씀.
- `ConfigValidator.validate(config, context) → List<ValidationMessage>` (Error/Warning, 키, 문구).
- `DeviceSnapshot` 불변: `deviceId(BLE 주소)`, `name`, `fw`, `mac?`, `config`, `cfgState`, `status`, `capabilities(dnsKeys, …)`, `readAt`.

### 6.3 저장 위치와 파일

앱 내부 저장소 `filesDir/` (백업 제외: `android:allowBackup="false"` 또는 백업 규칙에서 `networks.json` 제외).

| 파일 | 내용 | 형식 |
|---|---|---|
| DataStore `settings` | 앱 설정(F-G1) | Preferences |
| `profiles.json` | 프로필 목록 + 마지막 프로필 (부록 B-1) | JSON |
| `networks.json` | SSID 라이브러리. 비밀번호는 **Android Keystore** AES-GCM 키(`KeyGenParameterSpec`, 앱 전용, 내보내기 불가)로 암호화한 base64 + IV (부록 B-2) | JSON |
| `history.jsonl` | 작업 이력 1줄 1건 (부록 B-3) | JSON Lines, 추가만 |
| `logs/app-YYYYMMDD.log` | 앱 로그 7일 | 텍스트 |
| `cache/updates/` | APK 다운로드 | |

모든 JSON 은 `version` 필드로 스키마 버전을 가지며, 읽을 때 모르는 필드는 보존합니다(`kotlinx.serialization` `ignoreUnknownKeys` + 원본 보관). 쓰기는 임시 파일 → `rename`(원자적). 내보내기 파일은 `*.agvcfg.json`.

### 6.4 스레딩 · 수명

- 모든 I/O 는 코루틴(`Dispatchers.IO`). UI 는 `StateFlow` 를 `collectAsStateWithLifecycle`.
- `BleBridgeChannel` 은 GATT 콜백을 `Channel`/`CompletableDeferred` 로 감싸 suspend API 로 노출. GATT 연산은 **한 번에 하나**(`GattQueue`). `exec` 도 `Mutex` 로 직렬화.
- 워크플로는 `Flow<WorkflowProgress>` 또는 `onProgress` 콜백으로 단계 · 퍼센트 · 메시지를 보고하고 취소는 코루틴 취소로. 워크플로 실행 중에는 ViewModel 이 유지되도록 Activity 재생성(회전)에도 살아남게 `viewModelScope` 사용, 화면 회전 시 진행 패널 복원.
- 연결은 앱 수준 `BridgeSession`(싱글턴) 이 소유 → 탭을 옮겨도 유지. 앱이 백그라운드로 가도 연결은 유지하되, 5분 이상 비활성이면 끊고 상태를 "끊김" 으로.

---

## 7. 워크플로 상세

### 7.1 적용 (ApplyWorkflow)

입력: `DeviceSnapshot before`, `BridgeConfig target`, 옵션(`saveOnly`, `sendAllKeys`=true, `keepPassword`), 로그인 비밀번호(프로필). 출력: `ApplyResult`(단계별 결과, `DeviceSnapshot after`).

```
Idle ─▶ Validate ─▶ SendSet(i/n) ─▶ ReadBack(cfg 대조) ─▶ Save ─▶ [saveOnly? Done]
      ─▶ Reboot ─▶ WaitReady(재스캔·재연결·재로그인) ─▶ VerifyWifi(stat 폴링) ─▶ Done / Failed
```

| 단계 | 동작 | 성공 판정 | 실패 처리 |
|---|---|---|---|
| Validate | `ConfigValidator` 오류 0개, 장치 연결 · 로그인됨, 변경 0개면 "변경 없음" 으로 종료 | | 폼으로 돌아감 |
| SendSet | 키 순서 고정: `agv ssid pass ip mask gw dns dns2 server server_port local_port rfid_dst rfid_port main_baud rfid_baud hb_ms`. `pass` 는 폼이 비어 있으면 **보내지 않음**(유지). `dns`/`dns2` 는 장치가 지원할 때만(`cfg` 에 `dns=` 줄 존재). 각 명령 timeout 3초 | 응답이 `ok ` 로 시작 | `invalid key or value: <key>` → 그 키를 폼에서 강조하고 중단(저장 안 됨). timeout → 재시도 1회 후 중단. 연결 끊김 → 재연결 후 **처음부터** 다시(편집 사본이 사라졌을 수 있음) |
| ReadBack | `cfg` 재읽기. 보낸 키 전부가 target 과 같은지, `pass` 는 길이가 같은지, `* differs …` 줄이 있는지(변경이 있으면 있어야 정상) | 전부 일치 | 불일치 키 표시, 중단 |
| Save | `save` | `saved - 'reboot' to apply` | `save failed` → 중단, 재시도 버튼 |
| Reboot | `execThenDisconnect("reboot")`. `busy: mainboard programming` 이면 재부팅 거부 | `rebooting` 후 끊김 | 거부 → "저장은 됐고 재부팅만 남음. 메인보드 작업 후 [재부팅]" 카드 |
| WaitReady | 1초 뒤부터 BLE **주소**로 재스캔(최대 30초) → 연결 → notify → 환영 문구(새 이름 · fw) → `login` | 로그인 `ok` | timeout → "보드가 다시 보이지 않습니다: 전원 · 거리 확인 후 [보드 찾기]" (저장은 완료됐음을 명시) |
| VerifyWifi | 2초 간격 `stat`, 최대 30초. `wifi: UP` 이고 (고정 IP 면) `ip` 가 target 과 같으면 성공 | | DOWN 유지 → `last_reason` 해석(9.3)과 함께 "저장은 완료, Wi-Fi 미연결" 로 종료(이력 결과 `wifi_down:201`) |
| Done | 이력 기록, 라이브러리에 SSID/비밀번호 저장(동의 시), 프로필 `lastUsedAt` 갱신 | | |

- 취소: SendSet · ReadBack 단계에서만. 취소하면 편집 사본에 일부 값이 남지만 `save` 전이라 무해("재부팅하면 사라짐" 안내). BR-3(`discard`) 지원 펌웨어면 `discard` 전송.
- 호기 번호가 바뀌면 BLE 이름도 바뀜(`AGVBR-07-…`). 재스캔은 주소로 하므로 영향 없음. 결과 카드에 새 이름 표시.
- 비밀번호 값은 로그 · 콘솔 화면에 `***` 로.
- 재부팅 뒤 설정 모드는 다시 열려 있음(BLE 에서 `reboot` 했으므로). 완료 후 [설정 모드 닫기] 를 권하되, 닫지 않아도 Wi-Fi 연결 상태로 5분 뒤 자동으로 닫힘.

### 7.2 장치 읽기 (ReadDevice)

연결 · 로그인 직후와 [새로고침]: `cfg` → `stat`. `stat` 첫 줄에서 fw/part/ota/cfg, `wifi:` 줄에서 mac(0.5.3+)/ip/rssi, `setup:` 줄에서 이름, `dsp :` 줄에서 메인보드 상태. 둘 중 하나라도 파싱 실패하면 "지원하지 않는 펌웨어 출력 — 콘솔에서 확인" 상태로 두되 연결은 유지.

**기능 감지:** `help` 응답에 `discard` · `scan` · `nettest` 가 있는지로 선택 기능을 켜고 끈다(BR-3 · BR-4 · BR-5). DNS 지원은 `cfg` 의 `dns=` 줄 유무로. BR-1 반영 펌웨어: `cfg json` 응답이 `{` 로 시작하면 JSON 경로(구버전은 인자를 무시하고 텍스트를 내므로 자연히 폴백). 감지 결과는 `DeviceSnapshot.capabilities`.

### 7.3 연속 세팅 (BatchCoordinator)

상태: `Off → Armed(nextAgv) → Picking(보드 목록) → Reading → Ready → Applying → Armed(nextAgv+1)`.

- Armed 에서 적용 성공 → `nextAgv++`, 보드 목록으로. 방금 끝낸 장치(주소)는 "완료" 표식.
- Picking: 사용자가 보드를 고름 → Reading(연결 · 로그인 · 읽기).
- Ready: `cfgState == default` 면 폼에 `nextAgv` + 프로필 값 채움 → 사용자 [적용]. `saved` 면 경고 배너 + 채우지 않음(덮어쓰기 선택 가능).
- 중단: 토글 끔. 세션 요약(F-B3)은 Armed 전환 때마다 누적.

### 7.4 휴대폰 Wi-Fi 에서 가져오기 (PhoneWifiInfo)

1. `ConnectivityManager.activeNetwork` 가 Wi-Fi 인지 확인(`NetworkCapabilities.TRANSPORT_WIFI`). 아니면 "휴대폰을 현장 Wi-Fi 에 먼저 연결하세요".
2. `getLinkProperties(network)`: IPv4 `LinkAddress` 의 prefix → 마스크, `routes` 의 기본 경로 gateway → GW, `dnsServers` 의 IPv4 → dns/dns2.
3. SSID: `WifiManager.connectionInfo.ssid`(Android 10+ 위치 권한 + 위치 켜짐 필요, 따옴표 제거). 얻지 못하면 SSID 란은 비워 두고 나머지만 채움.
4. 주파수 `WifiInfo.frequency` 가 5GHz 대역이면 "브리지는 2.4GHz 만 — 같은 SSID 의 2.4GHz 가 있는지 확인" 경고.
5. 서버 IP 는 알 수 없음 → 비워 두고 포커스. IP 규칙 기준 네트워크는 휴대폰 IP 의 네트워크 주소로 제안.

### 7.5 앱 내 업데이트 (Updater)

1. 시작 3초 뒤 1회(설정으로 끔) + 정보 화면 수동: `https://github.com/Leepy0/AGV_CFG_Manager/releases/latest/download/AgvCfgManager.json` (12장 형식). 실패하면 `releases/latest` 리다이렉트의 태그로 폴백. api.github.com 은 쓰지 않음.
2. `versionCode` 가 현재보다 크면 상태 칩 "업데이트 있음 x.y.z" → [받기]: `releases/download/v<ver>/AgvCfgManager.apk` 를 `cache/updates/AgvCfgManager.apk.partial` 로 받고 sha256 대조 후 이름 변경.
3. [설치]: `FileProvider` URI 로 `ACTION_VIEW`(`application/vnd.android.package-archive`, `FLAG_GRANT_READ_URI_PERMISSION`). 처음이면 "알 수 없는 앱 설치 허용" 설정으로 안내(`ACTION_MANAGE_UNKNOWN_APP_SOURCES`).
4. 같은 서명 키여야 덮어 설치됨(12장). dev 빌드는 정식 업데이트 대상 아님.

---

## 8. Android BLE 구현 지침

Android BLE 는 기기 편차가 커서 아래 순서 · 규칙을 그대로 지킵니다.

### 8.1 권한 · 준비

| Android | 스캔 · 연결에 필요한 것 |
|---|---|
| 12+ (API 31) | `BLUETOOTH_SCAN`(manifest 에 `android:usesPermissionFlags="neverForLocation"`), `BLUETOOTH_CONNECT` — 런타임 요청. 위치 불필요 |
| 8~11 (API 26~30) | `BLUETOOTH`, `BLUETOOTH_ADMIN`(설치 시), `ACCESS_FINE_LOCATION`(런타임) + **위치 서비스 켜짐** 아니면 스캔 결과가 비어 있음 |
| 공통 | `BluetoothAdapter.isEnabled` 아니면 `ACTION_REQUEST_ENABLE`. `uses-feature android.hardware.bluetooth_le required=true` |

### 8.2 스캔

- `BluetoothLeScanner.startScan(filters, settings, callback)`: 필터 `ScanFilter.Builder().setServiceUuid(NUS)`(브리지는 스캔 응답에 UUID 를 넣으므로 `SCAN_MODE_LOW_LATENCY` 로 액티브 스캔), 이름 `AGVBR-` 접두어는 앱에서 추가 확인(`ScanRecord.deviceName` 또는 `device.name`).
- 10초 뒤 자동 중지. Android 는 30초에 5회 넘게 스캔을 시작하면 막으므로 자동 재스캔 간격 ≥ 6초.
- 결과는 주소별로 최신 RSSI 유지, 5초 이상 안 보이면 흐리게.

### 8.3 연결 순서 (GattQueue 로 한 번에 하나)

1. `device.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE)` — `autoConnect=false`.
2. `onConnectionStateChange(STATE_CONNECTED)` → 200ms 대기 → `requestMtu(247)`.
3. `onMtuChanged(mtu)` → 청크 크기 = `mtu - 3` 보관 → `discoverServices()`.
4. `onServicesDiscovered` → NUS 서비스 · RX · TX 특성 찾기. 없으면 "브리지가 아님".
5. `setCharacteristicNotification(TX, true)` + CCCD `ENABLE_NOTIFICATION_VALUE` 쓰기 → `onDescriptorWrite`.
6. 환영 문구 수신(`onCharacteristicChanged`) → 파싱 → 준비 완료. 2초 안에 안 오면 `help` 를 보내 응답으로 대체(구버전 대비).
7. `login <pw>` → `ok - type help`. `wrong password` 면 남은 횟수(5회) 표시, 사용자가 다시 입력. 5회째는 브리지가 끊음.

### 8.4 쓰기 · 읽기

- 쓰기: 줄 + `\n` 의 UTF-8 바이트를 `mtu-3` 씩 나눠 `WRITE_TYPE_NO_RESPONSE` 로, 각 청크는 `onCharacteristicWrite` 콜백 뒤 다음 청크(큐). API 33+ 는 `writeCharacteristic(ch, bytes, type)` 사용, 이하는 `setValue` 후 `writeCharacteristic(ch)`.
- 한 줄 ≤ 199B(브리지가 200바이트 버퍼에서 NUL 을 빼고 199바이트까지 받음. 값이 잘리면 안 되므로 앱은 넘기지 않음). 명령은 한 번에 하나(`Mutex`), 응답 끝 판정은 `ResponseAssembler`(3.2.1 규칙 → 아니면 250ms 유휴, 최대 3초).
- 읽기: `onCharacteristicChanged` 의 바이트를 UTF-8 로 누적. 멀티바이트 문자가 청크 경계에 걸릴 수 있으니 바이트 단위로 누적 후 디코드.

### 8.5 끊김 · 오류

- 상태 133(`GATT_ERROR`) · 연결 실패: `gatt.close()` 후 500ms 뒤 재시도, 최대 3회. **항상 `close()`** (`disconnect()` 만 하면 핸들 누수).
- 재부팅 뒤 재연결: 주소로 스캔하되 Android 캐시 때문에 `getRemoteDevice(address)` 직접 연결도 병행(스캔에서 먼저 보이면 그쪽).
- 백그라운드: 포그라운드에서만 동작하는 것을 전제. 적용 중에는 `keepScreenOn`. (선택) `connectedDevice` 포그라운드 서비스.
- 다른 휴대폰이 연결 중이면 광고가 멈춰 목록에서 사라짐 → "다른 휴대폰이 연결돼 있을 수 있음" 안내.

### 8.6 USB OTG (선택)

- `usb-serial-for-android`: `UsbManager` 장치 목록에서 CP210x/CH34x 드라이버 매칭, 권한 요청(`requestPermission`), 115200 8N1, DTR/RTS false. `device_filter.xml` 로 연결 인텐트 수신.
- 3.3 의 프롬프트 규약으로 `UsbSerialBridgeChannel` 구현. 로그인 없음. `reboot` 뒤 프롬프트 재등장 대기. 휴대폰이 보드에 전원을 공급하므로 전류 주의.

---

## 9. 오류 · 메시지 표

문구 원칙: **무엇이 · 왜 · 다음 행동**. 예외 메시지 원문은 "자세히" 에만.

### 9.1 찾기 · 연결

| 상황 | 표시 | 다음 행동 |
|---|---|---|
| 권한 거부 | "근처 기기(블루투스) 권한이 없어 보드를 찾을 수 없습니다." | [권한 허용] [설정 열기] |
| 블루투스 꺼짐 | "블루투스를 켜 주세요." | [켜기] |
| 위치 꺼짐(Android 11 이하) | "Android 11 이하는 위치 서비스가 켜져 있어야 BLE 스캔이 됩니다." | [위치 설정] |
| 보드 없음 | "보이는 보드가 없습니다. 설정 모드가 닫혀 있으면 보드의 BOOT 버튼을 3초 누르세요. 첫 설치 보드는 전원만 넣으면 열립니다." | [다시 찾기] |
| 연결 실패(133 반복) | "보드에 연결하지 못했습니다. 다른 휴대폰이 연결돼 있거나 거리가 멀 수 있습니다." | [다시 시도] |
| NUS 없음 | "AGV 브리지가 아닌 기기입니다." | [목록으로] |
| `wrong password` | "BLE 비밀번호가 틀렸습니다 (남은 시도 n회). 프로필의 BLE 비밀번호를 확인하세요." | [다시 입력] |
| `too many failures` | "5회 연속 실패로 보드가 연결을 끊었습니다. 다시 연결하면 재시도할 수 있습니다." | [다시 연결] |
| 연결 중 끊김 | 칩 "끊김 — 다시 연결 중 (n/30초)" | [취소] |

### 9.2 적용 · 검증

| 상황 | 표시 |
|---|---|
| `invalid key or value: server` | "브리지가 `server` 값을 거부했습니다. (규칙: 0.0.0.0 이 아닌 IP)" — 폼 항목 강조 |
| ReadBack 불일치 | "보낸 값과 장치의 편집 값이 다릅니다 (ip). 콘솔을 확인하고 다시 적용하세요." |
| `busy: mainboard programming` | "메인보드 펌웨어를 굽는 중이라 재부팅할 수 없습니다. 저장은 완료됐습니다. 끝난 뒤 [재부팅]." |
| 재부팅 뒤 안 보임 | "저장은 완료됐지만 보드가 다시 보이지 않습니다. 전원 · 거리를 확인하고 [보드 찾기]. Wi-Fi 에 붙었다면 게이트웨이 화면에서 확인할 수 있습니다." |
| `state=trial` 읽힘 | "서버 원격 설정이 확인 대기 중입니다. 여기서 저장하면 즉시 확정됩니다." |
| `dsp` `nopart` | "구 파티션 보드입니다. 메인보드 OTA 를 쓰려면 PC 에서 USB 로 파티션 포함 굽기가 필요합니다 (FLASH_GUIDE 3-2)." |
| 같은 서브넷 아님 (경고) | "IP 192.168.10.107 과 서버 192.168.20.10 이 같은 네트워크가 아닙니다 (마스크 255.255.255.0). 게이트웨이를 거칩니다." |
| 호기 중복 (경고) | "07호기는 2026-10-02 에 다른 보드에 설정됐습니다. 교체라면 계속하세요." |
| DHCP 선택 | "게이트웨이는 ACS `agvOpCtrl` IP 와 브리지 IP 를 비교합니다. DHCP 를 쓰면 DHCP 서버에서 IP 를 예약하세요." |
| 휴대폰이 5GHz | "휴대폰은 5GHz 에 연결돼 있습니다. 브리지는 2.4GHz 만 됩니다. 같은 SSID 의 2.4GHz 가 있는지 확인하세요." |

### 9.3 Wi-Fi 끊김 사유 (`last_reason`, ESP-IDF `wifi_err_reason_t`)

| 코드 | 이름 | 사용자 문구 · 조치 |
|---|---|---|
| 0 | — | 정상 |
| 2 | AUTH_EXPIRE | 인증 만료 — AP 쪽 설정 · 신호 확인 |
| 8 | ASSOC_LEAVE | 브리지가 스스로 끊음(재부팅 · 설정 변경) — 정상 과정 |
| 15 | 4WAY_HANDSHAKE_TIMEOUT | **비밀번호가 틀렸을 가능성** — 비밀번호 확인 |
| 200 | BEACON_TIMEOUT | 신호 끊김 — AP 거리 · RSSI 확인 |
| 201 | NO_AP_FOUND | **AP 를 찾지 못함** — SSID 철자, 2.4GHz 인지, 채널 1~13 인지, AP 전원. 휴대폰 스캔에 보이는지 함께 표시 |
| 202 | AUTH_FAIL | 인증 실패 — 비밀번호 · 보안 방식 |
| 203 | ASSOC_FAIL | 연결 거부 — AP 의 최대 접속 수 · MAC 필터 |
| 204 | HANDSHAKE_TIMEOUT | **비밀번호 오류 가능성** |
| 205 | CONNECTION_FAIL | 연결 실패 — AP 상태 |
| 210 | NO_AP_FOUND_W_COMPATIBLE_SECURITY | 보안 방식 불일치 — AP 가 WPA2-PSK 를 지원하는지 (WPA3 전용 · 엔터프라이즈 불가) |
| 211 | NO_AP_FOUND_IN_AUTHMODE_THRESHOLD | 같은 SSID 가 있지만 보안 방식이 다름 — 비밀번호 없는 AP 면 `-` 로 설정 |
| 212 | NO_AP_FOUND_IN_RSSI_THRESHOLD | 신호가 너무 약함 |
| 그 외 | 코드 그대로 | "ESP-IDF 사유 코드 n" + 콘솔 확인 안내 |

---

## 10. 테스트 전략

### 10.1 JVM 단위 테스트 (`:core`, 하드웨어 없음) — CI 필수

| 대상 | 케이스 |
|---|---|
| `ResponseParsers` | 부록 A 의 전문을 fixtures 로. 환영 문구(비밀번호 있음/없음) · 0.5.5 `cfg`(dns 있음) · 0.5.2 `cfg`(dns 없음) · 공백 SSID(`ssid="AGV WIFI 2F"`) · `ip=dhcp` · `rfid_dst=off` · `state=trial` · `* differs` 줄 유무 · BLE `stat`(fw 줄 있음, UP/DOWN) · 콘솔 `stat`(fw 줄 없음, main/rfid/heap) · `mac=` 없는 구버전 · `setup` on/off · `dsp` `nopart` · `set` 성공/실패 문구 · 청크 경계에서 잘린 UTF-8 |
| `ResponseAssembler` | 규칙 종료(cfg 의 `state=`, 한 줄 명령) · 유휴 종료 · 최대 시간 · 청크가 줄 중간에서 끊김 |
| `ConfigValidator` | 3.1 표의 경계값 전부(agv 0/1/99/100, ssid 32/33바이트 · 한글 바이트 계산, pass 7/8/64/65/`-`, IP 앞자리 0 · 256 · `dhcp`, mask 0.0.0.0, server 0.0.0.0, 포트 0/1/65535/65536, baud 1199/1200/1000000/1000001, hb 0/1/99/100/2000/2001) + 서브넷 경고 |
| `BridgeCommands` | BLE 는 따옴표 없음 · 199B 초과 거부, Usb 는 인용 · 이스케이프 · 160바이트 |
| `IpRule` | offset 계산, 범위 초과, dhcp/manual, 기준 네트워크 검증 |
| `ApplyWorkflow` + `FakeBridgeChannel` | 정상 경로, `invalid` 중단(저장 안 됨 검증), `save failed`, `busy` 재부팅 거부, 재부팅 뒤 재연결 timeout, Wi-Fi DOWN 201, 취소 시점별 상태, `pass` 미전송(유지), dns 미지원 펌웨어에서 dns 키 생략, 중간 끊김 후 처음부터 재시도 |
| `ProfileCodec` · `HistoryCodec` · `ProfileExport` | 왕복, 모르는 필드 보존, 비밀번호 포함/제외, CSV |
| `PhoneWifiInfo` 변환 | prefix → 마스크, 5GHz 판정(순수 함수 부분만) |

### 10.2 FakeBridgeChannel (시뮬레이터)

브리지 `cmd.c` · `setup.c ble_poll()` 의 의미를 Kotlin 으로 옮긴 가짜 장치. 환영 문구 · 로그인 · 실패 5회 끊김 · 실행 중 설정과 편집 사본 분리 · `set` 검증(`ConfigValidator` 와 **별도 구현**) · `reboot` 뒤 지연 후 "다시 광고"(`waitReady` 성공) · Wi-Fi 결과(UP / DOWN+reason) 시나리오 주입 · fw · dns 지원 · `nopart` · `busy` 주입. 응답은 MTU 청크로 잘라 유휴 시간까지 흉내. 디버그 빌드의 개발자 옵션 "가짜 브리지" 로 UI 에서도 사용(개발 · 데모 · 스크린샷).

### 10.3 Android 계측 테스트 (`:app`, 에뮬레이터)

Compose UI 테스트: 보드 목록 4상태, 설정 폼 검증 문구, 적용 진행 패널(Fake 채널), 프로필 편집 · IP 규칙 예시, 다크 테마 스냅샷(선택).

### 10.4 실기 체크리스트 (단계별 "실기 체크" 에서 수행)

| # | 확인 | 기대 |
|---|---|---|
| 1 | 첫 설치 보드(전원만) 찾기 | `AGVBR-01-…` 가 "설정 없음" 으로 보임 |
| 2 | 연결 · 로그인 · 읽기 | 환영 문구에서 fw, `cfg` 가 `state=default` |
| 3 | 프로필 + 호기 7 적용 | 16개 `set` 모두 `ok`, `saved`, `rebooting`, 재연결(이름 `AGVBR-07-…`), `wifi: UP ip=…107` |
| 4 | 비밀번호 틀리게 적용 | `wifi: DOWN`, reason 15/204 해석 표시, 이력 실패 기록 |
| 5 | 공백 SSID | 따옴표 없이 줄 끝까지 전송, `cfg` 되읽기 일치 |
| 6 | 서버 IP 만 변경 (S3, BOOT 3초로 열기) | 비밀번호 유지(길이 동일), 변경 1건만 |
| 7 | 연속 모드로 2대 | 호기 자동 +1, 완료 보드 표식, 이미 설정된 보드 경고 |
| 8 | BLE 비밀번호 5회 오류 | 브리지가 끊음, 앱이 사유 표시, 재연결 가능 |
| 9 | 적용 중 휴대폰을 멀리 두어 끊기 | 재연결 후 처음부터 재시도 또는 명확한 실패 카드 |
| 10 | 휴대폰 Wi-Fi 에서 가져오기 | GW · 마스크 · DNS 채워짐, 5GHz 경고 |
| 11 | Android 8/10/12/14 기기, 다크 모드, 회전 | 권한 흐름 · 레이아웃 · 진행 패널 복원 |
| 12 | 앱 내 업데이트 dev → release | 매니페스트 읽기, APK 설치, 되돌림 없음(이전 APK 수동) |

---

## 11. 개발 단계

각 단계 끝에 **테스트 통과 · 커밋 · 푸시**. 한 단계는 되도록 PR 하나.

### Phase 0 — 저장소 뼈대 (반나절)

- Gradle 멀티모듈(`:core` JVM, `:app` Android), 버전 카탈로그, Compose + M3 빈 화면(테마 light/dark, dynamic color 끔, edge-to-edge), `:core` 샘플 테스트, `.editorconfig`, `.gitignore`.
- `.github/workflows/android.yml`: ubuntu, JDK 17, `./gradlew :core:test :app:assembleDebug`, main 푸시 → `dev` prerelease 에 debug APK. (서명 · 정식 릴리스는 Phase 6)
- `docs/DECISIONS.md` 생성.
- **완료 기준:** CI 녹색, dev prerelease 에 APK, 기기에서 빈 화면이 뜸.

### Phase 1 — Core: 모델 · 파서 · 검증 · 가짜 장치 (1~2일)

- `BridgeConfig`, `DeviceSnapshot`, `ResponseParsers`, `ResponseAssembler`, `ConfigValidator`, `BridgeCommands`, `WifiReason`, `IpRule`, `FakeBridgeChannel`.
- Fixtures: 부록 A 를 파일로.
- **완료 기준:** 10.1 의 파서 · 어셈블러 · 검증 · 명령 · IpRule 테스트 전부 통과. `FakeBridgeChannel` 로 `login/set/save/reboot/cfg/stat` 왕복 테스트 통과.

### Phase 2 — BLE 연결 · 장치 읽기 · 콘솔 (2일)

- `BlePermissions`, `BleScanner`, `GattQueue`, `BleBridgeChannel`(8장), `BridgeSession`, `ReadDevice`.
- UI: 보드 탭(F-C1~C5), 연결 칩, 진단 탭의 콘솔(F-D1)과 상태 카드(F-D2), 설정 탭의 "장치:" 값(읽기 전용).
- **완료 기준:** 실기 체크 1 · 2 · 8, 콘솔에서 `stat` 입력. 가짜 브리지 모드로도 동일 동작. 회전 · 백그라운드 복귀에 연결 유지.

### Phase 3 — 설정 편집 · 적용 워크플로 (2일)

- `ApplyWorkflow`, 설정 탭 폼(F-E1~E3, E8), 진행 패널 · 결과 카드(F-A1~A2, A6), 이력 기록(F-H1, `HistoryStore`).
- 비밀번호 마스킹, 키 순서, dns 지원 감지, `busy` 처리, 재부팅 뒤 주소 재연결, Wi-Fi 확인 폴링.
- **완료 기준:** 10.1 ApplyWorkflow 테스트 통과, 실기 체크 3 · 4 · 5 · 6 · 9.

### Phase 4 — 프로필 · 라이브러리 · 자동 채움 · 연속 세팅 · 이력 탭 (2일)

- `Profile`, `ProfileStore`, `NetworkStore`(`KeystoreCipher`), 프로필 편집 · 목록 화면(F-P1~P3, P6), 자동 채움(F-E4~E7, E9), 라이브러리(F-W1~W3), 내보내기/가져오기(F-P4), `BatchCoordinator`(F-B1, B2), 이력 탭(F-H2, H3).
- **완료 기준:** 호기 번호만 바꿔 2대 연속 설정(실기 7). 프로필 내보내기 → 지우고 → 가져오기 왕복. 비밀번호가 평문으로 저장소에 남지 않음(파일 열어 확인). 실기 10.

### Phase 5 — 진단 보강 · QR · 공통 (1~2일)

- 게이트웨이 도달 확인 · 화면 열기(F-D4), 빠른 명령(F-D3), QR 내보내기/가져오기(F-P5), 설정 · 로그 · 정보 화면(F-G1, G2, G5), 출장 준비(F-G4), 태블릿 레이아웃(F-G6), 휴대폰 Wi-Fi 스캔(F-E10, 선택).
- **완료 기준:** 실기 11. `field-app-ui-design` 체크리스트 자가 점검 결과를 PR 본문에 기록.

### Phase 6 — 앱 내 업데이트 · 서명 · 정식 배포 (1일)

- `Updater`(7.5), 정보 화면 연동, 릴리스 서명 키(GitHub Secrets), `android.yml` 정식 릴리스 경로(12장), README 사용법(스크린샷은 가짜 브리지 모드).
- 첫 정식 릴리스 `v0.1.0`.
- **완료 기준:** 실기 12. `curl -sL …/releases/latest/download/AgvCfgManager.json` 에 버전 · sha 가 있고 APK 크기가 매니페스트와 같음.

### Phase 7 — (선택) USB OTG 시리얼 (1~2일)

- `UsbSerialBridgeChannel`(3.3 · 8.6), 보드 탭에 "USB 연결됨" 항목, 같은 설정 · 적용 흐름(로그인 없음, 콘솔 응답 문구 차이는 파서가 흡수).
- **완료 기준:** OTG 케이블로 `cfg` 읽기 · 적용 · 재부팅 후 `stat` 확인.

### 단계 공통 규칙

- UI 작업 전 `field-app-ui-design` 읽기. 화면당 주요 행동 1개 · 하단 1/3 · 4상태 · 진행률 + 취소 · 48dp(현장 72dp).
- 브리지 저장소는 **읽기만**. 브리지 수정이 필요하면 `docs/BRIDGE_CHANGE_REQUESTS.md` 에 추가하고 앱은 미반영 상태로도 동작하게 만든다.
- 비밀번호 · 사내 IP 는 테스트 fixture · 스크린샷 · 로그에 넣지 않는다 (`192.168.10.x` 예시값만).
- 커밋 메시지: 한국어 요약 한 줄 + 본문. 코드 주석 · 커밋에 모델 이름을 넣지 않는다.

### 현황 (Phase 6 완료 시점)

- **구현 완료:** Phase 0~6 (앱 내 업데이트 · 릴리스 서명 · 정식 릴리스 CI · README 포함). Phase 7(USB OTG)은 미착수.
- **미구현 (선택 항목):** F-E10 휴대폰 Wi-Fi 스캔(Phase 5 에서 건너뜀), F-P7 프로필별 요약, F-W3 의 생체 인증 비밀번호 보기(`BiometricPrompt`), F-W4 브리지 Wi-Fi 스캔(브리지 BR-4 반영 뒤). F-D5(부팅 확인 시간 기록)는 이번 단계에서 구현 여부를 확인하지 않았다.
- **미검증 (실기 필요):** 10.4 실기 체크 1~12 전부. 이 환경에는 에뮬레이터와 보드가 없어, JVM 테스트 · 디버그 · 릴리스 빌드(서명 · 미서명) · 매니페스트 생성 스크립트만 확인했다. 특히 12(앱 내 업데이트 `dev` → 정식 설치)는 실제 `v0.1.0` 릴리스 뒤에 수행한다.
- **정식 릴리스:** `v0.1.0` 은 아직 만들지 않았다. 서명 secrets 등록 뒤 README 의 "릴리스" 절차로 실행한다.
- **결정 필요 (14장):** 패키지명 `kr.agv.cfgmanager` 는 임시다(14장 7). 정식 릴리스 전에 확정해야 하며, 바꾸면 기존 설치본 업데이트가 깨진다. 본사 프로필 값은 여전히 `192.168.10.x` 자리표시자다(14장 5).
- **계획과 다르게 한 것** (이유는 docs/DECISIONS.md 의 Phase 6 항목):
  - 업데이트 로직은 6.1 의 `net/Updater.kt` 대신 `:core` 의 `update/`(순수 로직, JVM 테스트)와 `:app` 의 `update/`(HttpURLConnection · 설치 인텐트)로 나눴다. OkHttp 는 추가하지 않았다.
  - 자동 확인 알림은 상단 칩 대신 스낵바 한 줄이다.
  - dev 프리릴리스는 서명 secrets 가 없으면 디버그 키 APK 를 올린다(12장의 `assembleRelease` 대신).
  - 릴리스 노트는 커밋 제목 목록으로 자동 생성한다. 사용자 관점 한글 3~6줄 요약은 릴리스 본문에서 손으로 고친다.

---

## 12. 빌드 · 배포 · 앱 내 업데이트

사내망은 `api.github.com` 이 막히고 `github.com` 만 열리므로, 데스크톱 프로그램의 매니페스트 방식(`dotnet-self-update` 스킬의 핵심 결정)을 APK 에 맞춰 그대로 씁니다.

| 항목 | 값 |
|---|---|
| Repo | `Leepy0/AGV_CFG_Manager` |
| 정식 자산 3개 | `AgvCfgManager.apk`, `AgvCfgManager.apk.sha256`, `AgvCfgManager.json` — `--latest` 로 올림 |
| 매니페스트 | `{ "version": "0.1.0", "versionCode": 10, "file": "AgvCfgManager.apk", "sha256": "<64 hex>", "size": 12345678, "minSdk": 26, "published": "…Z", "page": "https://github.com/Leepy0/AGV_CFG_Manager/releases/tag/v0.1.0", "notes": "…" }` |
| 항상 최신 주소 | `https://github.com/Leepy0/AGV_CFG_Manager/releases/latest/download/AgvCfgManager.apk` (README · 정보 화면의 브라우저 링크) |
| 버전 | `versionName` = 태그(`vX.Y.Z` → `X.Y.Z`), `versionCode` = `X*10000 + Y*100 + Z` 로 계산(CI 가 `-PversionName=… -PversionCode=…`). 태그와 `build.gradle.kts` 의 기본 버전이 다르면 빌드 실패 |
| 채널 | `BuildConfig.CHANNEL`: `release` / `dev` / `local`. dev 는 정식만 받고 자기 자신은 업데이트 대상 아님 |
| 서명 | 릴리스 keystore 를 GitHub Secrets(`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)에. **같은 키로 계속 서명해야 덮어 설치됨** — 키 분실 시 전 기기 재설치(14장 결정 사항 3) |
| CI (`android.yml`) | main 푸시 → `:core:test` + `assembleRelease`(dev 채널) → `dev` prerelease 덮어쓰기(`AgvCfgManager-dev.apk`). 정식: 태그 `vX.Y.Z` 푸시 또는 `workflow_dispatch release=true` → release 채널 빌드 → sha256 · 매니페스트 생성 → Release 생성(릴리스 노트: 이전 태그 이후 커밋 메시지, `Co-Authored-By` · `Claude-Session` 줄 제거) |
| 설치 안내 | 처음 한 번 "알 수 없는 앱 설치 허용". 이후는 앱 안에서 [받기] → [설치]. Play 스토어 미사용 |
| 오픈소스 고지 | ZXing(Apache-2.0), usb-serial-for-android(MIT) 등 정보 화면에 |

릴리스 노트는 사용자 관점 한글 3~6줄. 테스트 빌드는 절대 Latest 로 올리지 않습니다.

---

## 13. 브리지 변경 요청 요약

전문과 제안 구현은 [BRIDGE_CHANGE_REQUESTS.md](BRIDGE_CHANGE_REQUESTS.md). 앱은 모두 **미반영 상태로 동작**하고, 반영되면 더 단순 · 견고한 경로를 씁니다.

| ID | 요청 | 앱에서의 효과 | 우선 |
|---|---|---|---|
| BR-1 | `cfg json` · `stat json` — 기계용 JSON 한 줄 | 장치 읽기 명령 단순화, 텍스트 파서 의존 제거, **한 줄이라 응답 끝 판정도 해결** | 높음 |
| BR-8 | **BLE · UDP 응답 끝 표시** — 응답 마지막에 구분 줄(예: `>` 한 줄) 또는 길이 머리말 | 유휴 시간(250ms) 대기 없이 즉시 다음 명령 → 적용 시간 단축, 오판 제거 | 높음 |
| BR-3 | `discard` — 편집 사본을 실행 중 설정으로 되돌림 | 적용 취소 · 실패 뒤 깨끗한 상태 | 중간 |
| BR-4 | `scan` — 브리지가 보는 AP 목록 JSON | 브리지 위치 기준 SSID · 2.4GHz · 보안 방식 확인 | 중간 |
| BR-9 | 환영 문구에 `mac` · `cfg` 상태 포함 | 로그인 전에 "설정 없음/있음" 과 MAC 을 알 수 있어 목록 · 이력 매칭이 정확 | 중간 |
| BR-5 | `nettest` — 서버 IP 로 ping · DNS 조회 | `wifi: UP` 인데 게이트웨이에 안 보일 때 원인 분리 | 낮음 |
| BR-6 | 릴리스에 `bridge.json` 매니페스트 | 최신 버전 안내(api.github.com 없이) | 낮음 |
| BR-7 | README 버전 표기 · DNS 키 문서 | 문서 불일치 제거 | 낮음 |

---

## 14. 리스크 · 결정이 필요한 사항

| # | 항목 | 현재 가정 | 결정이 바뀌면 |
|---|---|---|---|
| 1 | **플랫폼** | Android 만. iPhone 사용자는 기존 Bluefruit Connect 수동 입력 | iOS 가 필요하면 Kotlin Multiplatform(공유 `:core`) 또는 별도 Swift 앱 |
| 2 | **굽기** | 앱 범위 밖. PC 에서 `flash.bat`. | 휴대폰 OTG 굽기를 원하면 esptool 직렬 부트로더 프로토콜의 Kotlin 이식이 필요(큰 작업, 별도 계획) |
| 3 | 서명 키 보관 | GitHub Secrets + 담당자 오프라인 보관 | 분실 시 패키지명 변경 · 전 기기 재설치 |
| 4 | 비밀번호 보관 | Android Keystore 로 기기 묶임. 다른 휴대폰으로 옮길 때는 내보내기(평문 선택) 또는 QR | 팀 공유가 잦으면 내보내기 파일에 암호(AES-GCM + PBKDF2) 옵션 추가 |
| 5 | **본사 프로필 기본값** | DNS · 서브넷 · 서버 IP 실제 값을 모름 → 자리표시자(`192.168.10.x`) | 값을 받으면 Phase 4 에서 기본 프로필 템플릿에 반영(코드에 하드코딩하지 않음) |
| 6 | 응답 끝 판정 | 유휴 250ms + 명령별 규칙. 느린 폰에서 청크 간격이 250ms 를 넘으면 오판 가능 | BR-8/BR-1 반영 시 해결. 그때까지 유휴 시간을 설정에서 조정 가능하게(기본 250, 범위 150~800ms) |
| 7 | 패키지명 | `kr.agv.cfgmanager`(임시) | 회사 도메인에 맞춰 Phase 0 에서 확정. 나중에 바꾸면 업데이트 호환이 깨짐 |
| 8 | minSdk | 26 | 현장 휴대폰이 더 낮으면 BLE API 차이로 공수 증가. 더 높이면(31+) 권한 흐름이 단순해짐 |
| 9 | USB OTG | 선택. 호스트 지원 휴대폰 · 케이블 필요 | 쓰지 않으면 Phase 7 생략 |
| 10 | 브리지 변경 요청 수용 | 미반영 전제 | 반영 시 JSON 경로로 전환, 텍스트 파서는 구버전용으로 유지 |

---

## 부록 A. BLE 입출력 샘플 전문

앱이 보내는 바이트는 `→`, 받는 바이트는 그대로(줄 끝은 `\r\n`). BLE 는 echo 가 없고 프롬프트도 없습니다. 실제로는 MTU−3 바이트 청크로 잘려 도착합니다(fixture 는 청크 경계 변형도 둡니다).

### A-1. 연결 직후 환영 문구와 로그인

```
(notify 구독 직후)
AGV Wi-Fi Bridge AGVBR-07-A1B2 (fw 0.5.5)
login <password>
→ login agvsetup\n
ok - type help
→ stat\n   (로그인 전에 보냈다면)
login required: login <password>
→ login wrong\n
(1초 뒤) wrong password
(5회째) wrong password - too many failures, disconnecting
(연결 끊김)
```

비밀번호 없는 빌드면 둘째 줄이 `type help` 이고 로그인 없이 바로 명령을 받습니다.

### A-2. `cfg` (브리지 0.5.5, 저장된 설정, 편집 중 변경 있음)

```
→ cfg\n
agv=7 ssid="AGV_WIFI" pass=(10 chars)
ip=192.168.10.107 mask=255.255.255.0 gw=192.168.10.1
dns=192.168.10.1 dns2=8.8.8.8
server=192.168.10.10:50000 local_port=50000
rfid_dst=255.255.255.255:50001
main_baud=115200 rfid_baud=9600 hb_ms=1000
state=saved
* differs from running config - 'save' then 'reboot' to apply
```

변형: 0.5.4 이하는 `dns=` 줄이 없음. DHCP 면 `ip=dhcp`. 보조 DNS 없으면 `dns2=auto`. RFID 안 보내면 `rfid_dst=off:50001`. 공백 SSID 는 `ssid="AGV WIFI 2F"`. 저장 없음이면 `state=default`, 원격 확인 대기면 `state=trial`.

### A-3. `stat` (BLE, Wi-Fi 연결됨)

```
→ stat\n
fw=0.5.5 part=ota_0 ota=valid cfg=saved
wifi: UP mac=a1:b2:c3:d4:a1:b2 ip=192.168.10.107 rssi=-55 ch=6 bssid=11:22:33:44:55:66
net : tx=1234 tx_err=0 tx_offline=0 rx=56 rx_bad=0 rx_foreign=0 disc=0 last_reason=0
setup: on name=AGVBR-07-A1B2 ble=connected
dsp : app=1.0.3 boot=1.1.0 build=2026-10-01 state=idle staged=-(job 0) pan=?
```

Wi-Fi 미연결(AP 없음): `wifi: DOWN mac=… ip=0.0.0.0 rssi=0 ch=0 bssid=00:00:00:00:00:00`, `net :` 줄의 `disc=3 last_reason=201`. 0.5.2 이하는 `mac=` 토큰이 없음. 구 파티션: `dsp : … state=nopart (USB reflash needed) …`. `app` 은 메인보드 앱 버전이며 응답이 없으면 `?`. USB 콘솔의 `stat` 은 첫 줄(fw)이 없고 `main:` `rfid:` `heap:` 줄이 더 있음.

### A-4. `set` · `save` · `reboot` · 재연결

```
→ set ssid AGV WIFI 2F\n
ok ssid - 'save' then 'reboot' to apply
→ set agv 100\n
invalid key or value: agv
→ set pass -\n
ok pass - 'save' then 'reboot' to apply
→ save\n
saved - 'reboot' to apply
→ reboot\n
rebooting
(약 1초 뒤 연결 끊김 · 재부팅 · 설정 모드 다시 열림 · 같은 주소로 AGVBR-07-A1B2 광고)
(재연결 · notify 구독)
AGV Wi-Fi Bridge AGVBR-07-A1B2 (fw 0.5.5)
login <password>
→ login agvsetup\n
ok - type help
→ stat\n
fw=0.5.5 part=ota_0 ota=valid cfg=saved
wifi: UP mac=a1:b2:c3:d4:a1:b2 ip=192.168.10.107 rssi=-52 ch=6 bssid=11:22:33:44:55:66
…
```

메인보드 굽는 중이면 `reboot` 에 `busy: mainboard programming - reboot refused, retry when done` 이 오고 연결은 유지됩니다.

### A-5. `setup` · `help` · 모르는 명령

```
→ setup\n
setup: on name=AGVBR-07-A1B2 ble=connected
→ setup off\n
setup: on name=AGVBR-07-A1B2 ble=connected (closing)
(곧 연결 끊김, 광고 중지)
→ help\n
cfg | set <key> <value> | save | reboot | stat | setup [on|off] | dsp [ver|apply] | defaults
→ foo\n
unknown command 'foo' - type help
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
    { "ssid": "AGV_WIFI", "passwordEnc": { "iv": "base64", "data": "base64" }, "open": false, "lastUsedAt": "2026-10-17T10:12:00Z", "useCount": 12 },
    { "ssid": "GUEST_OPEN", "passwordEnc": null, "open": true, "lastUsedAt": null, "useCount": 0 }
  ]
}
```

`passwordEnc` 는 Android Keystore 의 앱 전용 AES-GCM 키로 암호화. 내보내기 파일(`*.agvcfg.json`)은 `profiles` + (선택) `networks` 를 담고, 비밀번호 포함 시 `"password": "평문"` 으로 쓰며 파일 머리에 `"containsPasswords": true` 를 둡니다. QR 은 프로필 1개(비밀번호 제외 기본)를 같은 JSON 으로 담습니다.

### B-3. `history.jsonl` (1줄 1건)

```json
{"at":"2026-10-17T10:15:32Z","profileId":"hq-f1","profileName":"본사 1공장","agv":7,"bleAddress":"AA:BB:CC:DD:EE:F8","mac":"a1:b2:c3:d4:a1:b2","deviceName":"AGVBR-07-A1B2","ip":"192.168.10.107","ssid":"AGV_WIFI","fw":"0.5.5","channel":"ble","action":"apply","result":"ok","rssi":-55,"note":""}
{"at":"2026-10-17T10:31:02Z","profileId":"hq-f1","profileName":"본사 1공장","agv":8,"bleAddress":"AA:BB:CC:DD:EE:03","mac":null,"deviceName":"AGVBR-08-E501","ip":"192.168.10.108","ssid":"AGV_WIFI","fw":"0.5.2","channel":"ble","action":"apply","result":"wifi_down:201","rssi":null,"note":"AP 안 보임, 현장 확인 필요"}
```

`action`: `apply` · `defaults`. `result`: `ok` · `wifi_down:<reason>` · `invalid:<key>` · `save_failed` · `reboot_refused` · `reconnect_timeout` · `cancelled` · `disconnected`.

## 부록 C. 용어

| 용어 | 뜻 |
|---|---|
| 브리지 | ESP32-S3 + RS232 보드. AGV 메인보드 J4 와 게이트웨이 서버 사이를 Wi-Fi/UDP 로 중계 |
| 호기 (agv) | AGV 번호 1~99. 브리지 설정 키 `agv`, BLE 이름 · 패킷 헤더에 쓰임 |
| 게이트웨이 | 서버 PC 프로그램(AGV_FOTA_GATEWAY). UDP 50000 으로 브리지와 통신, 호기 현황 · OTA 배포. 웹 화면 기본 포트 8080 |
| 설정 모드 | 브리지가 BLE(NUS)로 설정을 받는 상태. 이름 `AGVBR-NN-XXXX`. 열려 있을 때만 광고 |
| NUS | Nordic UART Service. BLE 로 텍스트 줄을 주고받는 관례적 서비스(UUID 6E400001-…) |
| 편집 사본 | `set` 으로 바뀌는 브리지 내부 임시 설정. `save` 전까지 실행 중 설정과 별개 |
| trial | 서버 원격(UDP) `save` 의 확인 대기 상태. BLE 에서 저장하면 즉시 확정 |
| nopart | 구 파티션 표인 채 0.5.x 앱을 OTA 로 받은 상태. PC 에서 USB 로 파티션 포함 굽기 필요 |
| 프로필 | 한 현장의 공통 설정 묶음 (이 앱의 개념) |
| IP 규칙 | 호기 번호에서 IP 를 계산하는 규칙 (이 앱의 개념) |
| OTG | 휴대폰을 USB 호스트로 쓰는 케이블. 보드의 UART 쪽 USB 와 연결해 시리얼 콘솔을 씀 |
