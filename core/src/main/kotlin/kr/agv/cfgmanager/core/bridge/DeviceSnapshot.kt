package kr.agv.cfgmanager.core.bridge

/**
 * 펌웨어가 지원하는 선택 기능. `help` 와 `cfg` 출력으로 감지한다 (3.6, BR-3 · BR-4 · BR-5).
 */
data class Capabilities(
    val dnsKeys: Boolean = false,
    val discard: Boolean = false,
    val scan: Boolean = false,
    val nettest: Boolean = false,
)

/**
 * 장치를 읽은 결과(불변). [readAt] 은 호출 측이 넣은 epoch 밀리초.
 * 읽기 중 일부가 파싱되지 않으면 해당 필드를 null 로 두고 [issues] 에 이유를 남긴다. 연결은 유지한다.
 */
data class DeviceSnapshot(
    val deviceId: String,
    val name: String?,
    val fw: String?,
    val mac: String?,
    val config: BridgeConfig?,
    val cfgState: CfgState?,
    val status: BridgeStatus?,
    val capabilities: Capabilities,
    val readAt: Long,
    val issues: List<String> = emptyList(),
)
