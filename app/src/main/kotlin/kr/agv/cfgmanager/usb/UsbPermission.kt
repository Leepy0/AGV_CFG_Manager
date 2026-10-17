package kr.agv.cfgmanager.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

private const val ACTION_USB_PERMISSION = "kr.agv.cfgmanager.USB_PERMISSION"

/**
 * 장치 권한이 없으면 시스템 대화상자를 띄우고 응답을 기다린다. 허용되면 true, 거부 · 취소면 false.
 * 응답은 한 번만 받는다. 기다리는 쪽이 취소되면 수신기를 떼고 false 로 끝낸다.
 */
internal suspend fun requestUsbPermission(context: Context, usb: UsbManager, device: UsbDevice): Boolean {
    if (usb.hasPermission(device)) return true
    return suspendCancellableCoroutine { cont ->
        val done = AtomicBoolean(false)
        lateinit var receiver: BroadcastReceiver
        fun finish(granted: Boolean) {
            if (!done.compareAndSet(false, true)) return
            runCatching { context.unregisterReceiver(receiver) }
            cont.resume(granted)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACTION_USB_PERMISSION) return
                finish(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        cont.invokeOnCancellation { finish(false) }
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        usb.requestPermission(device, PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE))
    }
}
