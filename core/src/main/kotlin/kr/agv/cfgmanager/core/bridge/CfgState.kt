package kr.agv.cfgmanager.core.bridge

/** 브리지 설정 상태. `cfg` 의 `state=` 값. */
enum class CfgState(val wire: String) {
    /** NVS 에 저장된 설정으로 동작 중. */
    SAVED("saved"),

    /** 저장된 설정이 없어 빌드 기본값으로 동작 중 (첫 설치). */
    DEFAULT("default"),

    /** 서버 원격 저장 후 확인 대기 중 (180초 안에 서버 통신이 없으면 복구). */
    TRIAL("trial");

    companion object {
        fun fromWire(wire: String): CfgState? = entries.firstOrNull { it.wire == wire }
    }
}
