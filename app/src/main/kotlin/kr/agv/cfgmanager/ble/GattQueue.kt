package kr.agv.cfgmanager.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import java.util.concurrent.atomic.AtomicReference

/** GATT 콜백 결과. */
sealed interface GattEvent {
    data class Connection(val connected: Boolean, val status: Int) : GattEvent
    data class Mtu(val mtu: Int, val status: Int) : GattEvent
    data class Services(val status: Int) : GattEvent
    data class Write(val status: Int) : GattEvent

    /** 링크가 닫혀 진행 중인 연산이 끝났다. */
    data object Closed : GattEvent
}

/** 연산을 시작하지 못했거나 시간 안에 끝나지 않았다. */
class GattException(message: String) : Exception(message)

/**
 * GATT 연산을 한 번에 하나씩 진행한다 (docs/DEVELOPMENT_PLAN.md 6.4, 8.3).
 * [run] 이 연산을 시작하고, 콜백 스레드가 [deliver] 로 결과를 넘기면 끝난다.
 */
class GattQueue {
    private val mutex = Mutex()
    private val waiter = AtomicReference<CompletableDeferred<GattEvent>?>(null)

    suspend fun run(timeout: Duration, start: () -> Boolean): GattEvent = mutex.withLock {
        val deferred = CompletableDeferred<GattEvent>()
        waiter.set(deferred)
        try {
            if (!start()) throw GattException("GATT 연산을 시작하지 못했습니다")
            withTimeout(timeout) { deferred.await() }
        } catch (timeoutError: TimeoutCancellationException) {
            throw GattException("GATT 응답 시간 초과")
        } finally {
            waiter.compareAndSet(deferred, null)
        }
    }

    /** 콜백 스레드에서 부른다. 기다리는 연산이 없으면 버린다. */
    fun deliver(event: GattEvent) {
        waiter.get()?.complete(event)
    }
}
