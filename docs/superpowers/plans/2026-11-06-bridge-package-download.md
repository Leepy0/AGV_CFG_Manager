# 브리지 보관 패키지 내려받기 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 휴대폰을 브리지에 BLE 또는 USB OTG 로 연결해, 브리지에 보관된 메인보드 패키지에서 `.out`·`.map` 을 내려받아 폰 폴더에 저장한다.

**Architecture:** 브리지에 읽기 전용 명령 `dsp pkg json`, `dsp pkg read` 를 추가한다(0.9.1). CFG 매니저는 기존 `IBridgeChannel` 로 512 B 씩 받아 임시 파일에 이어 붙이고, sha256 과 패키지 규칙을 검증한 뒤 `.out`·`.map` 을 풀어 저장한다. BLE 와 USB 는 같은 코드를 쓴다.

**Tech Stack:** ESP-IDF C, Kotlin, Jetpack Compose, Storage Access Framework, `java.util.zip`.

**Spec:** 이 문서의 "설계" 절. 패키지 규격은 `AGV_Wifi_Bridge/docs/DSP_OTA_DESIGN.md` 3-1절과 6절을 따른다.

## Global Constraints

- 브리지 저장소는 CFG 매니저 `CLAUDE.md` 상 읽기 전용이다. 이 계획의 Task 1·2 는 사용자 승인이 있어야 진행한다.
- 브리지 쓰기 동작을 추가하지 않는다. 새 명령은 flash 를 읽기만 한다.
- 새 명령은 `CMD_SRC_LOCAL`(BLE)과 USB 콘솔에서만 받는다. UDP 서버 경로는 거부한다.
- 메인보드 굽기·스테이징 중에는 새 명령을 거부한다.
- 로직은 `:core`, Android 의존 코드는 `:app` 에 둔다. `:core` 는 `allWarningsAsErrors` 다.
- 새 의존성을 추가하지 않는다. SAF 는 `DocumentsContract` 를 직접 쓴다.
- UI 는 단어 위주, 설명 문장 금지, 위험 확인은 팝업, `ui/theme` 토큰만 사용한다.
- 코드 주석·커밋에 모델 이름을 넣지 않는다. 주석과 문서는 한국어, 식별자는 영어다.

## Review Focus

1. 받는 중 BLE 가 끊기거나 보드가 재부팅되면, 받은 부분을 지키고 다시 받기에서 이어받아야 한다. 테스트는 Task 4.
2. 브리지가 0.9.0 이하이면, 앱이 죽지 않고 "브리지 0.9.1 이상 필요" 를 보여야 한다. 테스트는 Task 3.
3. 받는 중 서버 스테이징이 슬롯을 덮어쓰면, 두 패키지가 섞인 파일을 저장하지 않아야 한다. 테스트는 Task 4.
4. USB 콘솔 응답에 ESP 로그 줄이 끼어들면, 재시도로 복구해야 한다. 테스트는 Task 3·4.
5. 저장 폴더 권한이 없거나 공간이 부족하면, 캐시 파일을 지키고 다시 저장할 수 있어야 한다. 테스트는 Task 7.

---

## 설계

### 경로 선택

| 방식 | 판단 |
|---|---|
| 브리지 명령 + 기존 채널 | **채택.** BLE·USB 공용, 재부팅 없음, UART 중계 유지 |
| 폰에서 esptool 프로토콜 재구현 | 제외. 보드 리셋으로 AGV 통신이 끊기고, USB 만 되며, 플래셔 스텁을 APK 에 넣어야 한다 |
| Wi-Fi HTTP | 제외. SoftAP 는 기본 꺼짐이고 WIPS 현장에서 차단된다. 공장망 노출은 인증 설계가 추가로 필요하다 |

### 명령 규격

```
dsp pkg json
{"slots":[{"i":0,"st":"APPLIED","id":"<16자>","ver":"1.2.3","size":123,"seq":2,"job":0,"sha256":"<64자>"},{"i":1,"st":"empty"}]}
{"err":"nopart"}  {"err":"busy"}  {"err":"local_only"}

dsp pkg read <slot> <off> <len>
P <id> <off> <n> <crc32 소문자 8자리> <base64>
E nopart | E busy | E local_only | E slot | E range | E flash
```

- `len` 은 1~512 다. `n = min(len, size - off)` 이다.
- `off >= size`, `len` 범위 밖은 `E range` 다. 빈 슬롯은 `E slot` 이다.
- `off` 는 패키지 시작 기준이다. 슬롯 머리 4KB 는 포함하지 않는다.
- CRC 는 raw 바이트의 IEEE CRC32 다. 펌웨어는 `dspimg_crc32_*`, 앱은 `java.util.zip.CRC32` 를 쓴다.
- 0.9.0 은 `dsp pkg json` 에 텍스트를 돌려준다. 이전 버전은 `dsp` 상태 줄을 돌려준다. `{` 로 시작하지 않으면 미지원이다.

### 앱 흐름

1. 더보기 → 메인보드 원본 → 슬롯 목록(상태·버전·크기).
2. 받기 → "AGV 정지" 확인 팝업. BLE 와 Wi-Fi 가 무선부를 나눠 쓰고 전송이 길어서다.
3. `cacheDir/pkg/<id>.part` 에 이어 받는다. 전송은 앱 수명 컨트롤러가 돌려서 화면을 나가도 계속된다. 받는 동안 화면은 켜 둔다.
4. 크기·sha256 확인 → `cacheDir/pkg/<id>/` 에 풀기 → 패키지 ID·manifest 해시 확인.
5. 폴더 선택 → `AGV<호기>_<ver>_<id 8자>/` 하위 폴더에 `.out`·`.map` 저장 → 캐시 삭제.

---

### Task 1: 브리지 패키지 읽기 함수

**Repo:** `AGV_Wifi_Bridge`

**Files:**
- Modify: `main/bridge_util.h`, `main/dsp_pkg.h`, `main/dsp_pkg.c`
- Test: `tests/bridge_util/test_bridge_util.c`, `tests/dsp_ota_host/test_dsp_ota_flow.c`

**Interfaces:**
- Produces: `static inline size_t bu_base64(const uint8_t *src, size_t n, char *dst, size_t cap)` — NUL 제외 길이 반환, 공간 부족이면 0.
- Produces: `#define DSP_PKG_READ_MAX 512`
- Produces: `size_t dsp_pkg_format_json(char *buf, size_t cap)` — `slots` 또는 `{"err":"nopart"}`.
- Produces: `size_t dsp_pkg_read_line(int slot, uint32_t off, uint32_t len, char *out, size_t cap)` — `P ...` 또는 `E ...` 한 줄, `\n` 포함.

- [ ] **Step 1: 실패하는 시험 작성**

`bu_base64` 는 RFC 4648 벡터 `""`, `"f"`, `"fo"`, `"foo"`, `"foobar"` → `""`, `"Zg=="`, `"Zm8="`, `"Zm9v"`, `"Zm9vYmFy"` 와 공간 부족 0 을 검사한다. `dsp_ota_flow` 에서 `dspimg_crc32_bytes` 의 `"123456789"` 결과가 `0xCBF43926` 인지 먼저 고정한다. 패키지를 슬롯에 쓴 뒤 앞·중간·끝 청크 복원, 끝을 넘는 `len` 축소, `E range`(`off == size`, `len == 0`, `len == 513`), 빈 슬롯 `E slot`, JSON 의 id·ver·size·sha256 값을 검사한다.

- [ ] **Step 2: 시험 실패 확인**

Run: `make -C tests/bridge_util && sh tests/dsp_ota_host/run.sh`
Expected: 선언 없음으로 빌드 실패.

- [ ] **Step 3: 구현**

`dsp_pkg_read_line` 은 `s_slot[slot]` 복사본을 쓴다. 지역 버퍼 `uint8_t raw[DSP_PKG_READ_MAX]` 에 `esp_partition_read(s_part, slot_base(slot) + DSP_PKG_HDR_SECTOR + off, ...)` 로 읽는다. 읽기 실패는 `E flash` 다.

- [ ] **Step 4: 시험 통과 확인**

Run: `make -C tests/bridge_util && sh tests/dsp_ota_host/run.sh`
Expected: 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add main/bridge_util.h main/dsp_pkg.h main/dsp_pkg.c tests/bridge_util/test_bridge_util.c tests/dsp_ota_host/test_dsp_ota_flow.c
git commit -m "feat: 보관 패키지 조각 읽기와 슬롯 JSON 추가"
```

### Task 2: 브리지 명령 연결과 0.9.1

**Repo:** `AGV_Wifi_Bridge`

**Files:**
- Modify: `main/cmd.c`, `main/cmd.h`, `main/console_cmd.c`, `CMakeLists.txt`, `docs/DSP_OTA_DESIGN.md`
- Test: `tests/bridge_util/test_cmd.c`

**Interfaces:**
- Consumes: Task 1 의 `dsp_pkg_format_json`, `dsp_pkg_read_line`.
- Produces: `size_t cmd_pkg_json(char *out, size_t cap)`, `size_t cmd_pkg_read(const char *slot, const char *off, const char *len, char *out, size_t cap)` — busy 검사와 숫자 파싱 포함.

- [ ] **Step 1: 실패하는 시험 작성**

`test_cmd.c` 에 스텁을 추가한다. `CMD_SRC_LOCAL` 로 `dsp pkg json`, `dsp pkg read 0 0 512` 가 스텁을 호출하는지 검사한다. `CMD_SRC_SERVER` 는 `{"err":"local_only"}`, `E local_only` 다. `s_dsp_prog`, `s_dsp_stage` 가 켜지면 `{"err":"busy"}`, `E busy` 다. 인자 누락과 숫자 아닌 인자는 `E range` 다. 기존 `dsp pkg` 는 텍스트 그대로다.

- [ ] **Step 2: 시험 실패 확인**

Run: `make -C tests/bridge_util`
Expected: 새 검사 실패.

- [ ] **Step 3: 구현**

`cmd_exec` 의 `dsp pkg` 분기에서 다음 토큰을 본다. `console_cmd.c` 의 `cmd_dsp` 는 `argc >= 3 && argv[1] == "pkg"` 일 때 같은 함수를 부르고, 파일 정적 `char big[1024]` 를 쓴다. `help` 문구, `PROJECT_VER` 0.9.1, 설계 문서의 패키지 보관 절을 함께 고친다.

- [ ] **Step 4: 시험 통과 확인**

Run: `make -C tests/bridge_util && sh tests/dsp_ota_host/run.sh`
Expected: 전부 통과. CI 의 ESP-IDF 빌드도 통과.

- [ ] **Step 5: 커밋**

```bash
git add main/cmd.c main/cmd.h main/console_cmd.c CMakeLists.txt docs/DSP_OTA_DESIGN.md tests/bridge_util/test_cmd.c
git commit -m "feat: 현장 연결에서 보관 패키지를 읽는 명령 추가 (0.9.1)"
```

### Task 3: 앱 명령과 응답 파서

**Repo:** `AGV_CFG_Manager`

**Files:**
- Modify: `core/src/main/kotlin/kr/agv/cfgmanager/core/bridge/BridgeCommands.kt`, `core/src/main/kotlin/kr/agv/cfgmanager/core/fake/FakeDevice.kt`
- Create: `core/src/main/kotlin/kr/agv/cfgmanager/core/pkg/PkgProtocol.kt`
- Test: `core/src/test/kotlin/kr/agv/cfgmanager/core/pkg/PkgProtocolTest.kt`, `core/src/test/kotlin/kr/agv/cfgmanager/core/fake/FakeDeviceTest.kt`

**Interfaces:**
- Produces: `BridgeCommands.pkgJson(): String`, `pkgRead(slot: Int, off: Long, len: Int): String`, `isBulkRead(line: String): Boolean`
- Produces: `enum class PkgState { Staged, Applied, Old }`
- Produces: `data class PkgSlot(index: Int, state: PkgState, id: String, ver: String, size: Long, seq: Long, sha256: String)`
- Produces: `sealed interface PkgList { Ok(slots: List<PkgSlot>); Unsupported; NoPartition; Busy }`
- Produces: `fun parsePkgList(text: String): PkgList`
- Produces: `fun defaultPkgSlot(slots: List<PkgSlot>): PkgSlot?` — APPLIED, 없으면 seq 최대 STAGED.
- Produces: `sealed interface PkgChunk { Data(id: String, off: Long, bytes: ByteArray); Refused(code: String); Bad }`
- Produces: `fun parsePkgChunk(text: String): PkgChunk` — CRC·길이 불일치와 형식 오류는 `Bad`.
- Produces: `FakeDeviceOptions.pkg: ByteArray?`, 가짜 장치의 두 명령.

- [ ] **Step 1: 실패하는 시험 작성**

구버전 텍스트 응답 `pkg slot0: empty`, `dsp : app=...` 는 `Unsupported` 다. JSON 오류 세 종류, 슬롯 2개, 빈 슬롯 제외, `defaultPkgSlot` 우선순위를 검사한다. 청크는 정상, CRC 불일치 `Bad`, `n` 불일치 `Bad`, 앞뒤에 다른 줄이 있어도 `P ` 줄을 찾는 경우, `E busy` 를 검사한다. `isBulkRead` 는 `dsp pkg read 0 0 512` 만 참이다.

- [ ] **Step 2: 시험 실패 확인**

Run: `./gradlew :core:test --tests "*PkgProtocolTest*"`
Expected: 컴파일 실패.

- [ ] **Step 3: 구현**

`Base64`, `CRC32` 는 `java.util` 을 쓴다. JSON 은 `kotlinx.serialization.json` 의 `JsonObject` 를 직접 읽는다. 알 수 없는 슬롯 상태는 목록에서 뺀다.

- [ ] **Step 4: 시험 통과 확인**

Run: `./gradlew :core:test`
Expected: 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add core
git commit -m "브리지 패키지 명령과 응답 파서를 추가한다"
```

### Task 4: 이어받기 워크플로

**Repo:** `AGV_CFG_Manager`

**Files:**
- Create: `core/src/main/kotlin/kr/agv/cfgmanager/core/pkg/PkgDownloadWorkflow.kt`
- Test: `core/src/test/kotlin/kr/agv/cfgmanager/core/pkg/PkgDownloadWorkflowTest.kt`

**Interfaces:**
- Consumes: Task 3 전체.
- Produces: `sealed interface PkgDownloadOutcome { Done(file: File); Disconnected; Busy; SlotChanged; Corrupt; Failed(reason: String) }`
- Produces: `object PkgDownloadWorkflow { suspend fun run(channel: IBridgeChannel, slot: PkgSlot, part: File, onProgress: (done: Long, total: Long) -> Unit): PkgDownloadOutcome }`

- [ ] **Step 1: 실패하는 시험 작성**

`FakeBridgeChannel` 으로 다음을 검사한다.

- 처음부터 끝까지 받기, `Done`, 파일 내용 일치, 진행 콜백 마지막 값 `total`.
- 중간 끊김 → `Disconnected`, 파일 유지. 다시 실행하면 남은 오프셋부터 요청하고 `Done`.
- 다음 청크의 `id` 변경 → `SlotChanged`, 파일 삭제.
- `Bad` 2회 뒤 정상 → 성공. `Bad` 3회 → `Failed`, 파일 유지.
- `Timeout` 도 재시도 대상이다.
- `E busy` → `Busy`, 파일 유지.
- 받은 내용의 sha256 불일치 → `Corrupt`, 파일 삭제.
- 시작 때 `part` 가 `size` 보다 길면 비우고 처음부터 받는다.
- 취소하면 `CancellationException` 이 나가고 파일이 남는다.

- [ ] **Step 2: 시험 실패 확인**

Run: `./gradlew :core:test --tests "*PkgDownloadWorkflowTest*"`
Expected: 컴파일 실패.

- [ ] **Step 3: 구현**

청크 512 B, 청크 시간 제한 5초, 연속 실패 3회다. 파일은 청크마다 append 한다. 응답의 `off` 가 요청과 다르면 `Bad` 로 친다. 끝나면 파일 전체로 sha256 을 계산한다.

- [ ] **Step 4: 시험 통과 확인**

Run: `./gradlew :core:test`
Expected: 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add core
git commit -m "끊겨도 이어받는 패키지 내려받기를 추가한다"
```

### Task 5: 패키지 풀기와 검증

**Repo:** `AGV_CFG_Manager`

**Files:**
- Create: `core/src/main/kotlin/kr/agv/cfgmanager/core/pkg/DsppkgExtractor.kt`
- Test: `core/src/test/kotlin/kr/agv/cfgmanager/core/pkg/DsppkgExtractorTest.kt`

**Interfaces:**
- Produces: `class PkgFormatException(message: String) : Exception(message)`
- Produces: `data class PkgFiles(val out: File, val map: File)`
- Produces: `object DsppkgExtractor { fun extract(pkg: File, id: String, ver: String, dir: File): PkgFiles }`

- [ ] **Step 1: 실패하는 시험 작성**

`ZipOutputStream` 으로 규격 패키지를 만든다. 통과 시 두 파일 내용과 이름을 검사한다. 거부 사례는 항목 5개, 항목 누락, 중복 이름, `../x.out`, 하위 폴더, 허용되지 않는 확장자, manifest `format` 불일치, `ver` 불일치, 파일 sha256 불일치, 패키지 ID 불일치, `.out` 64 MiB 초과(선언값을 속인 경우 포함)다. 거부 뒤 `dir` 에 파일이 남지 않아야 한다.

- [ ] **Step 2: 시험 실패 확인**

Run: `./gradlew :core:test --tests "*DsppkgExtractorTest*"`
Expected: 컴파일 실패.

- [ ] **Step 3: 구현**

규격 값: 이름 `^[A-Za-z0-9][A-Za-z0-9._-]{0,59}$`, `.dspfw` ≤ 393,152 B, `.out` ≤ 64 MiB, `.map` ≤ 16 MiB, manifest ≤ 64 KiB, 패키지 ID = `sha256(manifest 바이트)` 소문자 hex 앞 16자. 크기는 ZIP 선언값이 아니라 읽으며 센다. 링크 시각 재확인은 하지 않는다. 패키지를 만들 때와 게이트웨이 수신 때 이미 확인한다.

- [ ] **Step 4: 시험 통과 확인**

Run: `./gradlew :core:test`
Expected: 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add core
git commit -m "내려받은 패키지를 규격대로 검증해 푼다"
```

### Task 6: 대량 읽기가 콘솔을 덮지 않게 하기

**Repo:** `AGV_CFG_Manager`

**Files:**
- Modify: `app/src/main/kotlin/kr/agv/cfgmanager/ble/BleBridgeChannel.kt:271`, `app/src/main/kotlin/kr/agv/cfgmanager/usb/UsbSerialBridgeChannel.kt` `sendLine`, `app/src/main/kotlin/kr/agv/cfgmanager/session/LoggedChannel.kt`
- Test: `app/src/test/kotlin/kr/agv/cfgmanager/session/LoggedChannelTest.kt`

**Interfaces:**
- Consumes: `BridgeCommands.isBulkRead`.

- [ ] **Step 1: 실패하는 시험 작성**

`LoggedChannel` 로 `dsp pkg read 0 0 512` 를 보내도 `ConsoleLog.snapshot()` 이 그대로이고, `stat` 은 기록되는지 검사한다.

- [ ] **Step 2: 시험 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "*LoggedChannelTest*"`
Expected: 첫 검사 실패.

- [ ] **Step 3: 구현**

세 곳 모두 `isBulkRead(line)` 이면 기록과 `rawFlow.tryEmit` 을 건너뛴다.

- [ ] **Step 4: 시험 통과 확인**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add app
git commit -m "패키지 조각이 콘솔 기록을 밀어내지 않게 한다"
```

### Task 7: 메인보드 원본 화면과 저장

**Repo:** `AGV_CFG_Manager`

**Files:**
- Create: `app/src/main/kotlin/kr/agv/cfgmanager/pkg/PkgController.kt`, `app/src/main/kotlin/kr/agv/cfgmanager/pkg/PkgSaver.kt`, `app/src/main/kotlin/kr/agv/cfgmanager/ui/pkg/PkgScreen.kt`
- Modify: `app/src/main/kotlin/kr/agv/cfgmanager/AppContainer.kt`, `app/src/main/kotlin/kr/agv/cfgmanager/ui/more/MoreScreen.kt`, `app/src/main/kotlin/kr/agv/cfgmanager/ui/navigation/AppRoot.kt`
- Test: `app/src/test/kotlin/kr/agv/cfgmanager/pkg/PkgControllerTest.kt`

**Interfaces:**
- Consumes: Task 3~5 의 공개 API.
- Produces: `sealed interface PkgUi { NotConnected; Loading; Unsupported; NoPartition; Empty; Slots(slots: List<PkgSlot>, preferred: Int?); Downloading(slot: PkgSlot, done: Long, total: Long); ReadyToSave(files: PkgFiles, folder: String); Saved(folder: String); Error(text: String, canRetry: Boolean) }`
- Produces: `class PkgController(exclusive: suspend (suspend (IBridgeChannel) -> Unit) -> Unit, agvNumber: () -> Int?, cacheDir: File, scope: CoroutineScope)` — `val ui: StateFlow<PkgUi>`, `fun refresh()`, `fun download(slot: PkgSlot)`, `fun cancel()`, `fun onSaved()`, `fun onSaveFailed(text: String)`.
- Produces: `object PkgSaver { fun save(resolver: ContentResolver, tree: Uri, folder: String, files: PkgFiles) }` — 실패 시 `IOException`.

- [ ] **Step 1: 실패하는 시험 작성**

`FactoryResetRunnerTest` 처럼 `exclusive` 람다에 `FakeBridgeChannel` 을 넣어 검사한다. 미연결 → `NotConnected`. 구버전 → `Unsupported`. 받기 → `Downloading` → `ReadyToSave`. 끊김 → `Error(canRetry=true)` 이고 다시 받기가 이어받는다. `onSaveFailed` → `ReadyToSave` 유지, 캐시 유지. `onSaved` → `Saved`, `cacheDir/pkg` 의 해당 파일 삭제. 폴더 이름은 `AGV07_1.2.3_ab12cd34` 다.

- [ ] **Step 2: 시험 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "*PkgControllerTest*"`
Expected: 컴파일 실패.

- [ ] **Step 3: 구현**

- 컨트롤러는 `BatchController` 처럼 `AppContainer` 에서 `appScope` 로 만든다.
- 더보기 행: 제목 `메인보드 원본`, 설명 `.out · .map 받기`. 라우트 `mainboard_pkg`.
- 슬롯 행: 상태 · 버전 · 크기 · `받기`. 추천 슬롯을 위에 둔다.
- `받기` 는 `AlertDialog` 로 `AGV 정지` 확인 후 시작한다.
- 진행: 막대, 퍼센트, `취소`. `LocalView.current.keepScreenOn` 을 진행 중에만 켠다.
- `ActivityResultContracts.OpenDocumentTree` 로 폴더를 고른다. 권한은 영구 보관하지 않는다.
- `DocumentsContract.createDocument` 로 하위 폴더를 만들고, 두 파일은 `application/octet-stream` 으로 만든다. IO 는 `Dispatchers.IO` 에서 한다.
- 긴 작업 진입·결과만 `AppLog` 에 남긴다.

- [ ] **Step 4: 시험 통과 확인**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug`
Expected: 전부 통과, 새 경고 없음.

- [ ] **Step 5: 커밋**

```bash
git add app
git commit -m "브리지에서 메인보드 원본을 받아 저장하는 화면을 추가한다"
```

### Task 8: 문서와 실기 확인

**Files:**
- Modify: `docs/BRIDGE_CHANGE_REQUESTS.md`(BR-10), `docs/DECISIONS.md`, `docs/DEVELOPMENT_PLAN.md`, `README.md`, `app/build.gradle.kts`(1.2.0)

- [ ] **Step 1: 문서 갱신과 버전 올리기**

경로 선택 표를 `DECISIONS.md` 에 한 줄로 남긴다.

- [ ] **Step 2: 실기 확인 (사용자 입회)**

| 항목 | 통과 기준 |
|---|---|
| BLE 전체 받기 | `.out`·`.map` 이 PC 원본과 sha256 일치 |
| USB OTG 전체 받기 | 같음 |
| BLE 전송 속도 | 실측값 기록. 1 MB 패키지 10분 초과면 연결 우선순위 조정 검토 |
| 중간에 BLE 끊기 | 재연결 후 이어받아 일치 |
| 주행 중 중계 영향 | 받는 동안 UDP 지연·손실 기록 |

- [ ] **Step 3: 커밋**

```bash
git add README.md docs app/build.gradle.kts
git commit -m "메인보드 원본 내려받기 문서를 정리하고 1.2.0 으로 올린다"
```
