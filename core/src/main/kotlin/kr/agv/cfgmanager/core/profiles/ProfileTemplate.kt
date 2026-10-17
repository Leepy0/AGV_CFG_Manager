package kr.agv.cfgmanager.core.profiles

import kotlinx.serialization.json.JsonObject

/**
 * 첫 실행 때 만드는 "본사" 기본 프로필의 초깃값 (docs/DEVELOPMENT_PLAN.md F-P2, 14장 5).
 *
 * 값은 두 종류다. 브리지 빌드 기본값(3.1, `AGV_TEST` · 마스크 · 포트 · 보드레이트 · BLE 비밀번호 `agvsetup`)은
 * 그대로 쓰고, 회사 고정값(게이트웨이 · 서버 · DNS)은 아직 모르므로 자리표시자 `192.168.10.x` 를 넣는다.
 * 회사 값은 앱의 프로필 편집 화면에서 "본사" 프로필에 넣는다. 그 값이 새 프로필의 시작값이 된다 ([newProfileFrom]).
 */
object ProfileTemplate {

    const val HEADQUARTERS_ID = "hq-default"

    /**
     * 새 프로필의 시작값. [base] 가 본사 프로필이면 그 값을 쓰고, 없으면 자리표시자 템플릿을 쓴다.
     * 이름 · 메모 · SSID · 이력 시각은 새로 만든다. BLE 비밀번호와 네트워크 값은 현장 공통이므로 가져온다.
     */
    fun newProfileFrom(base: Profile?, id: String, nowIso: String): Profile {
        val source = base ?: headquarters(nowIso)
        return source.copy(
            id = id,
            name = "새 프로필",
            notes = "",
            ssid = "",
            createdAt = nowIso,
            updatedAt = nowIso,
            lastUsedAt = null,
            extras = JsonObject(emptyMap()),
        )
    }

    fun headquarters(nowIso: String): Profile = Profile(
        id = HEADQUARTERS_ID,
        name = "본사",
        notes = "자리표시자 값입니다. 프로필 편집에서 회사 게이트웨이 · 서버 · DNS 로 고치세요. 저장한 값이 새 프로필의 시작값이 됩니다.",
        createdAt = nowIso,
        updatedAt = nowIso,
        lastUsedAt = null,
        ssid = "AGV_TEST",
        ipRule = IpRule.Offset(baseNetwork = "192.168.10.0", offset = 100),
        mask = "255.255.255.0",
        gw = "192.168.10.1",
        dns = "auto",
        dns2 = "auto",
        server = "192.168.10.10",
        serverPort = 50000,
        localPort = 50000,
        rfidDst = "255.255.255.255",
        rfidPort = 50001,
        mainBaud = 115200,
        rfidBaud = 9600,
        hbMs = 1000,
        blePassword = "agvsetup",
    )
}
