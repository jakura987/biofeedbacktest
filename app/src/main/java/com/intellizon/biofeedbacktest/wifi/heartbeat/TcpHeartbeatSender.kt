package com.intellizon.biofeedbacktest.wifi.heartbeat

import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * TCP 心跳发送器
 *
 * 协议：
 * 5A A5 LEN CMD TYPE DATA... CS
 *
 * 心跳：
 * CMD  = 0x16
 * TYPE = 0x7F
 * LEN  = 0x19 = CMD(1) + TYPE(1) + DATA(23)
 *
 * DATA[0] = SEQ_HI
 * DATA[1] = SEQ_MID
 * DATA[2] = SEQ_LOW
 * DATA[3..22] = 预留
 */
class TcpHeartbeatSender(
    private val intervalSec: Long = 4L,
    private val peerProvider: () -> String?,
    private val sendToPeer: (peer: String, data: ByteArray) -> Boolean,
    private val broadcast: (data: ByteArray) -> Boolean,
) {

    companion object {
        private const val CMD_HEARTBEAT = 0x16
        private const val TYPE_HEARTBEAT_REQ = 0x7F
        private const val DATA_SIZE = 23
    }

    private val started = AtomicBoolean(false)
    private val seq = AtomicInteger(0)

    @Volatile
    private var disposable: Disposable? = null

    @Synchronized
    fun start() {
        if (!started.compareAndSet(false, true)) {
            Timber.v("Heartbeat already started")
            return
        }

        disposable = Flowable
            .interval(intervalSec, intervalSec, TimeUnit.SECONDS, Schedulers.io())
            .subscribe(
                {
                    sendOnce()
                },
                { e ->
                    Timber.w(e, "Heartbeat loop error")
                }
            )

        Timber.i("Heartbeat started, interval=%ds", intervalSec)
    }

    @Synchronized
    fun stop() {
        started.set(false)
        disposable?.dispose()
        disposable = null
        Timber.i("Heartbeat stopped")
    }

    private fun sendOnce() {
        val seq24 = seq.incrementAndGet() and 0xFFFFFF
        val packet = buildHeartbeatPacket(seq24)
        val ok = broadcast(packet)
        Timber.i("Heartbeat broadcast result=%s, packet=%s", ok, packet.toHexString())
    }

    private fun buildHeartbeatPacket(seq24: Int): ByteArray {
        val data = ByteArray(DATA_SIZE)
        data[0] = ((seq24 shr 16) and 0xFF).toByte()
        data[1] = ((seq24 shr 8) and 0xFF).toByte()
        data[2] = (seq24 and 0xFF).toByte()
        // data[3..22] 保留为 0

        return composeFrame(
            cmd = CMD_HEARTBEAT,
            type = TYPE_HEARTBEAT_REQ,
            data = data
        )
    }

    private fun composeFrame(cmd: Int, type: Int, data: ByteArray): ByteArray {
        val len = 2 + data.size // CMD + TYPE + DATA
        val frame = ByteArray(2 + 1 + len + 1)

        frame[0] = 0x5A
        frame[1] = 0xA5.toByte()
        frame[2] = len.toByte()
        frame[3] = cmd.toByte()
        frame[4] = type.toByte()

        if (data.isNotEmpty()) {
            System.arraycopy(data, 0, frame, 5, data.size)
        }

        var sum = 0
        for (i in 2 until frame.size - 1) {
            sum = (sum + (frame[i].toInt() and 0xFF)) and 0xFF
        }
        frame[frame.size - 1] = sum.toByte()

        return frame
    }

    private fun ByteArray.toHexString(): String =
        joinToString(" ") { "%02X".format(it) }
}




//    fun sendOnce(): Boolean {
//        val seq24 = seq.incrementAndGet() and 0xFFFFFF
//        val packet = buildHeartbeatPacket(seq24)
//
//        val peer = peerProvider()
//        val ok = if (!peer.isNullOrBlank()) {
//            sendToPeer(peer, packet)
//        } else {
//            broadcast(packet)
//        }
//
//        Timber.v(
//            "HEARTBEAT_SEND ok=%s peer=%s seq=%06X hex=%s",
//            ok,
//            peer ?: "ALL",
//            seq24,
//            packet.toHexString()
//        )
//        return ok
//    }