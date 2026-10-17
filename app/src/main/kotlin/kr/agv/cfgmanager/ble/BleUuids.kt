package kr.agv.cfgmanager.ble

import java.util.UUID

/** Nordic UART Service (docs/DEVELOPMENT_PLAN.md 3.2). */
object NusUuid {
    val SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

    /** 앱 → 장치 (Write / Write Without Response). */
    val RX: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

    /** 장치 → 앱 (Notify). */
    val TX: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    /** Client Characteristic Configuration (notify 구독). */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
