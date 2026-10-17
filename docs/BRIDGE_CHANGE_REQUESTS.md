# 브리지(AGV_Wifi_Bridge) 변경 요청

AGV CFG Manager 가 더 단순하고 견고하게 동작하기 위해 브리지 펌웨어에 요청하는 변경입니다. 기준 브리지 **0.5.5**.

- 앱은 **모든 항목이 미반영이어도 동작**합니다(텍스트 파싱). 반영되면 앱이 출력 형식을 보고 새 경로를 씁니다.
- 각 항목은 브리지 저장소의 이슈로 그대로 옮길 수 있게 썼습니다. 제안 구현은 참고용이며 브리지 쪽 관례(`cmd.c` 공용 명령, 호스트 시험 `tests/bridge_util`)를 따릅니다.
- 요청 우선순위: 높음(BR-1) → 중간(BR-3 · BR-4 · BR-6) → 낮음(BR-5 · BR-7).

## BR-1. `cfg json` · `stat json` — 기계용 JSON 출력 (높음)

**문제.** 앱이 장치를 읽으려면 `ota`(fw) · `stat`(mac, ip, wifi, 설정 모드 이름) · `cfg`(설정) 세 명령의 텍스트를 각각 파싱해야 합니다. 형식이 버전마다 조금씩 바뀌고(0.5.3 `mac=` 추가, 0.5.5 `dns=` 줄 추가), 로그 줄이 섞이면 파싱이 깨질 수 있습니다.

**요청.** 콘솔 · BLE · UDP 공용으로 `cfg json` 과 `stat json` 인자를 받아 한 줄 JSON 을 출력합니다. 기존 텍스트 출력은 그대로 둡니다.

`cfg json` (편집 사본 + 식별 정보):

```json
{"fw":"0.5.5","idf":"v6.1","mac":"a1:b2:c3:d4:e5:f6","name":"AGVBR-07-E5F6","part":"ota_0","ota":"valid",
 "state":"saved","differs":true,
 "cfg":{"agv":7,"ssid":"AGV WIFI 2F","pass_len":10,"ip":"192.168.10.107","mask":"255.255.255.0","gw":"192.168.10.1",
        "dns":"192.168.10.1","dns2":"auto","server":"192.168.10.10","server_port":50000,"local_port":50000,
        "rfid_dst":"255.255.255.255","rfid_port":50001,"main_baud":115200,"rfid_baud":9600,"hb_ms":1000},
 "keys":["agv","ssid","pass","ip","mask","gw","dns","dns2","server","server_port","local_port","rfid_dst","rfid_port","main_baud","rfid_baud","hb_ms"]}
```

- `ip` 는 DHCP 면 `"dhcp"`, `rfid_dst` 는 끔이면 `"off"`, DNS 는 자동이면 `"auto"` — 텍스트 출력과 같은 표기.
- `keys` 는 이 펌웨어가 받는 `set` 키 목록 → 앱이 버전별 키 차이를 하드코딩하지 않아도 됨.
- 비밀번호는 지금처럼 길이만.
- SSID 의 `"` · `\` · 제어문자는 JSON 이스케이프(`dsp_ota.c` 의 `dap_json_escape` 재사용).

`stat json`:

```json
{"fw":"0.5.5","part":"ota_0","ota":"valid","cfg":"saved","up":1234,
 "wifi":{"up":true,"mac":"a1:b2:c3:d4:e5:f6","ip":"192.168.10.107","rssi":-55,"ch":6,"bssid":"11:22:33:44:55:66","disc":0,"reason":0},
 "net":{"tx":1234,"tx_err":0,"tx_offline":0,"rx":56,"rx_bad":0,"rx_foreign":0},
 "setup":{"on":false,"name":"AGVBR-07-E5F6"},
 "dsp":{"app":"1.0.3","boot":"1.1.0","build":"2026-10-01","st":"idle","staged":null},
 "uart":{"main":{"rx_frm":120,"tx_frm":118,"err":0,"tx_drop":0},"rfid":{"rx_frm":3,"err":0}},
 "heap":212345}
```

**제안 구현.** `app_config.c` 에 `cfg_format_json()`, `net_bridge.c` 에 `net_format_status_json()` 추가. `cmd.c` 와 `console_cmd.c` 의 `cfg` · `stat` 에서 첫 인자가 `json` 이면 호출. 출력 버퍼는 UDP 응답 한도(`BR_PAYLOAD_MAX` 512B) 안에 들어가야 하므로 `stat json` 의 `uart` 는 필드를 줄이거나 생략 가능. 호스트 시험 `tests/bridge_util/test_cmd.c` 에 JSON 유효성 · 이스케이프 케이스 추가.

**앱 측 사용.** `cfg json` 응답이 `{` 로 시작하면 JSON 경로, 아니면(구버전 `unknown command` 또는 텍스트) 텍스트 파서로 폴백.

## BR-2. (BR-1 에 포함) 식별 정보 한 번에

`fw` · `mac` · `name` 이 `cfg json` 에 들어가면 장치 읽기가 명령 1개로 끝납니다. 별도 항목으로 두지 않고 BR-1 수용 여부로 결정합니다.

## BR-3. `discard` — 편집 사본을 실행 중 설정으로 되돌리기 (중간)

**문제.** 여러 `set` 을 보내다 하나가 거부되거나 사용자가 취소하면 편집 사본에 일부 값이 남습니다. `save` 를 안 보내면 무해하지만, 같은 세션에서 뒤이어 다른 도구(게이트웨이 원격 `cfg … save`, BLE)가 `save` 하면 **의도치 않은 값이 함께 저장**될 수 있습니다. 되돌리는 명령이 없어 재부팅만이 해법입니다.

**요청.** 공용 명령 `discard` → 편집 사본 = 실행 중 설정(`s_edit = g_cfg`), 응답 `discarded - edit copy now equals running config`.

**제안 구현.** `app_config.c` 에 `cfg_discard()`(잠금 안에서 복사), `cmd.c` · `console_cmd.c` 에 등록, `help` 에 추가.

## BR-4. `scan` — 보이는 AP 목록 (중간)

**문제.** SSID 오타 · 5GHz 전용 AP · WPA3 전용 등은 저장 · 재부팅 뒤 `last_reason` 으로만 알 수 있습니다. 현장(특히 처음 가는 해외 현장)에서는 **설정 전에** 브리지 위치에서 어떤 AP 가 어떤 세기로 보이는지 알면 시행착오가 줄어듭니다.

**요청.** `scan` → 2.4GHz 전 채널 스캔(약 2~3초) 뒤 RSSI 내림차순 최대 15개를 JSON 배열로:

```json
[{"ssid":"AGV_WIFI","rssi":-48,"ch":6,"auth":"wpa2"},{"ssid":"GUEST","rssi":-70,"ch":1,"auth":"open"}]
```

`auth`: `open` · `wep` · `wpa` · `wpa2` · `wpa3` · `wpa2_wpa3` · `ent`. 응답은 스캔이 끝난 뒤 한 번에(콘솔은 블로킹 허용, UDP 는 `CFG_RESULT` 1개로, 512B 를 넘으면 상위 항목만).

**제약.** 메인보드 굽는 중(`dsp_ota_busy()`)이나 스테이징 중에는 `busy` 로 거부. STA 연결 중 스캔은 중계 지연을 잠깐 늘리므로 **AGV 정지 상태에서만** 쓰라고 응답 첫 줄 또는 문서에 명시. 설정 AP(SoftAP) 모드에서도 동작.

**제안 구현.** `net_bridge.c` 에 `net_scan_json(buf, cap)`: `esp_wifi_scan_start(NULL, true)` → `esp_wifi_scan_get_ap_records`. `cmd.c` · `console_cmd.c` 에 `scan` 등록.

## BR-5. `nettest` — 서버 도달 · DNS 확인 (낮음)

**문제.** `wifi: UP` 인데 게이트웨이에 안 보이면 `server` 오타 · 라우팅 · 방화벽 중 무엇인지 브리지 쪽에서는 구분이 안 됩니다. 콘솔 `stat` 의 `rx_foreign` 은 게이트웨이가 PING 을 보내 줘야 늘어납니다.

**요청.** `nettest [host]` → 설정된 `server`(또는 인자) 로 ICMP ping 3회 결과(`ping 192.168.10.10: 3/3 ok, avg 4 ms` 또는 `0/3`), 인자가 호스트 이름이면 DNS 조회 결과(`dns: gateway.local -> 192.168.10.10` / `dns: fail`) 를 함께. ESP-IDF `ping/ping_sock.h` 사용. 메인보드 작업 중에는 거부.

## BR-6. 릴리스에 `bridge.json` 매니페스트 첨부 (중간)

**문제.** 사내망은 `api.github.com` 이 막혀 있어 앱이 "최신 버전이 몇인지" 를 API 로 물을 수 없습니다. `releases/latest` 리다이렉트로 태그를 알아낼 수는 있지만 zip 이름 · 체크섬을 알 방법이 없습니다.

**요청.** `.github/workflows/build.yml` 의 Release 단계에서 아래 파일을 자산으로 추가합니다(고정 이름 → `https://github.com/Leepy0/AGV_Wifi_Bridge/releases/latest/download/bridge.json`).

```json
{
  "version": "0.5.5",
  "published": "2026-10-14T08:36:00Z",
  "page": "https://github.com/Leepy0/AGV_Wifi_Bridge/releases/tag/v0.5.5",
  "zip":    { "file": "agv_wifi_bridge_0.5.5.zip",   "sha256": "…", "size": 1234567 },
  "app":    { "file": "agv_wifi_bridge.bin",         "sha256": "…", "size": 1234567, "offset": "0x20000" },
  "merged": { "file": "agv_wifi_bridge_merged.bin",  "sha256": "…", "size": 4194304, "offset": "0x0" },
  "minPartitionVersion": "0.5.0",
  "notes": "릴리스 노트 본문"
}
```

이 형식은 `dotnet-self-update` 스킬의 앱 매니페스트와 같은 방식이라 팀 내 관례와 일치합니다. 제안 구현: Package 단계 뒤 `sha256sum` 으로 생성, `files:` 에 `dist/bridge.json` 추가.

## BR-7. 문서 정리 (낮음)

- README 첫 줄의 "현재 버전 v0.5.2" → 0.5.5, FLASH_GUIDE 의 기준 버전 표기도 함께.
- FLASH_GUIDE 5장 설정 키 표에 `dns` · `dns2` 추가(README 에는 이미 있음).
- `cfg` 텍스트 출력 형식이 **외부 도구(이 앱)가 파싱하는 인터페이스**임을 `app_config.c` `cfg_format()` 주석에 남겨, 형식을 바꿀 때 버전 · 호환 표를 갱신하도록 함.

## 앱이 요청 없이 감수하는 것 (참고)

| 브리지 특성 | 앱의 대응 |
|---|---|
| 비밀번호 되읽기 불가 | SSID 라이브러리에 저장, 전체 굽기 백업 · 복원 때 라이브러리에서 공급 |
| 콘솔 `set` 은 argv 토큰(공백 값은 따옴표) | 문자열 값을 항상 따옴표 + 이스케이프 |
| 콘솔 최대 160자 | 명령 길이 검사 |
| 로그가 명령 출력에 섞임 | `^[IWEDV] \(\d+\) ` 줄 분리, 연결 직후 `log warn`(0.5.4+) 선택 전송 |
| `reboot` 은 프롬프트 없이 재시작 | 프롬프트 재등장 대기(20초) 후 `stat` 폴링 |
| UDP 는 `server` IP 에서 온 패킷만 | 원격 모드는 게이트웨이 PC 전용으로 안내 |
