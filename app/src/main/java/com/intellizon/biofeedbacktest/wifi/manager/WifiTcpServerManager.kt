package com.intellizon.biofeedbacktest.wifi.manager

import android.os.SystemClock
import com.intellizon.biofeedbacktest.domain.TcpPacket
import com.intellizon.biofeedbacktest.wifi.connect.SimpleTcpServer
import com.intellizon.biofeedbacktest.wifi.connect.TcpServerController
import com.intellizon.biofeedbacktest.wifi.heartbeat.TcpHeartbeatSender
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class WifiTcpServerManager @Inject constructor() : TcpServerController {

    private val startStopDisposables = CompositeDisposable()
    //记录每个 peer 最近一次收到 ACK 的时间
    private val lastHeartbeatAckAt = ConcurrentHashMap<String, Long>()
    //watchdog，每秒扫一次
    private var heartbeatWatchdog: Disposable? = null
    @Volatile
    var selectedPeer: String? = null

    private var server: SimpleTcpServer? = null

    private val heartbeatSender by lazy {
        TcpHeartbeatSender(
            intervalSec = 4L,
            peerProvider = { selectedPeer },
            sendToPeer = { peer, data ->
                server?.sendTo(peer, data) ?: false
            },
            broadcast = { data ->
                server?.send(data) ?: false
            }
        )
    }

    private val _running = BehaviorProcessor.createDefault(false)
    fun running(): Flowable<Boolean> = _running.onBackpressureLatest()

    private val ctrlPendingMap = ConcurrentHashMap<String, ByteArray>()
    private fun Byte.u8(): Int = toInt() and 0xFF


    // 收到的数据（原样透传）
    private val _incoming = PublishProcessor.create<TcpPacket>()
    fun incoming(): Flowable<TcpPacket> = _incoming.onBackpressureBuffer()



   override fun ensureStarted(port: Int) {
        // ✅幂等：已经 running 就别重复发起
        if (server?.isRunning() == true) return

        // ✅清掉上一次可能残留的订阅（一般不会残留，但保险）
        startStopDisposables.clear()

        startStopDisposables.add(
            startIfNeeded(port) // 你原来的 Completable 版本
                .subscribeOn(Schedulers.io())
                .subscribe(
                    {
                        Timber.i("TCP server ready on port=%d", port)
                        heartbeatSender.start()
                        ensureHeartbeatWatchdogStarted() //计时 todo
                    },
                    { e ->
                        Timber.w(e, "TCP server start failed port=%d", port)
                    }
                )
        )
    }

    private var lastTcpInAtMs = 0L
    private var lastTcpOutAtMs = 0L

    @Synchronized
    fun startIfNeeded(port: Int = 8883): Completable =
        Completable.fromAction {
            if (server?.isRunning == true) return@fromAction

            Timber.i("Starting TCP server on 0.0.0.0:%d", port)

            server = SimpleTcpServer(port) { peer, data, len ->
                // listener 回调在 socket 线程池里：这里尽量轻量
                val copy = data.copyOf(len)

                //这一次 socket 数据块到达并进入回调的时刻
                val nowIn = SystemClock.elapsedRealtime()
                if (lastTcpInAtMs != 0L) {
                    val dtIn = nowIn - lastTcpInAtMs
                    if (dtIn > 1000L) {
                        Timber.e(
                            "TCP_IN_GAP dt=%dms len=%d head=%s",
                            dtIn,
                            len,
                            copy.take(12).joinToString(" ") { "%02X".format(it) }
                        )
                    }
                }
                lastTcpInAtMs = nowIn

                val filtered = stripControlAndEmit(peer, copy)

                if (filtered.isNotEmpty()) {
                    val nowOut = SystemClock.elapsedRealtime()
                    if (lastTcpOutAtMs != 0L) {
                        val dtOut = nowOut - lastTcpOutAtMs
                        if (dtOut > 1000L) {
                            Timber.e(
                                "TCP_OUT_GAP dt=%dms in=%d out=%d head=%s",
                                dtOut,
                                len,
                                filtered.size,
                                filtered.take(12).joinToString(" ") { "%02X".format(it) }
                            )
                        }
                    }
                    lastTcpOutAtMs = nowOut

                    _incoming.onNext(TcpPacket(peer, filtered))
                }

            }.also { it.start() }

            _running.onNext(true)
        }.subscribeOn(Schedulers.io())


    private fun stripControlAndEmit(peer: String, chunk: ByteArray): ByteArray {
        var pending = ctrlPendingMap[peer] ?: ByteArray(0)
        pending += chunk
        val out = ArrayList<Byte>(chunk.size + 2)


        try {
            while (true) {
                // 至少先得有帧头两个字节
                if (pending.size < 2) {
                    Timber.w("CTRL_BREAK size<2 peer=%s pending=%d", peer, pending.size)
                    break
                }

                // 找帧头 5A A5
                if (!(pending[0] == 0x5A.toByte() && pending[1] == 0xA5.toByte())) {
                    Timber.w(
                        "CTRL_DROP peer=%s one byte=%02X pendingHead=%s",
                        peer,
                        pending[0].u8(),
                        pending.take(12).joinToString(" ") { "%02X".format(it) }
                    )
                    out.add(pending[0])
                    pending = pending.copyOfRange(1, pending.size)
                    continue
                }

                // 至少要有：5A A5 LEN CMD TYPE
                if (pending.size < 5) {
                    Timber.w("CTRL_BREAK size<5 peer=%s pending=%d", peer, pending.size)
                    break
                }

                val len = pending[2].u8()
                // 新协议：LEN = CMD + TYPE + DATA[5]
                val total = 2 + 1 + len + 1   // 头2 + LEN1 + 内容len + CS1

                // 一帧还没收全，等下个 chunk
                if (pending.size < total) {
                    break
                }

                val frame = pending.copyOfRange(0, total)
                pending = pending.copyOfRange(total, pending.size)

                if (!isValidCs(frame)) {
                    Timber.w(
                        "CTRL_BAD_CS peer=%s frame=%s",
                        peer,
                        frame.joinToString(" ") { "%02X".format(it) }
                    )
                    continue
                }

                try {
                    val cmd = frame[3].u8()
                    val typ = frame[4].u8()

                    // LEN 包含 CMD + TYPE，所以 dataLen = len - 2
                    val dataLen = (len - 2).coerceAtLeast(0)
                    val data = if (dataLen > 0 && frame.size >= 5 + dataLen + 1) {
                        frame.copyOfRange(5, 5 + dataLen)
                    } else {
                        ByteArray(0)
                    }

                    val cs = frame.last().u8()

                    //心跳包
                    if (cmd == 0x16) {
                        Timber.v("cmd receive 0x16 heartbeat... ")
                        when (typ) {
                            0x7F -> {
                                // 心跳应答：LEN = CMD + TYPE + DATA(23) = 0x19
                                if (len != 0x19 || data.size != 23) {
                                    Timber.w(
                                        "CTRL_SKIP invalid heartbeat ack peer=%s typ=%02X len=%02X dataLen=%d frame=%s",
                                        peer,
                                        typ,
                                        len,
                                        data.size,
                                        frame.joinToString(" ") { "%02X".format(it) }
                                    )
                                    continue
                                }
                                lastHeartbeatAckAt[peer] = SystemClock.elapsedRealtime()
                                Timber.i("heartbeat ack ok, peer=%s", peer)
                                continue
                            }

                            else -> {
                                Timber.w(
                                    "CTRL_SKIP unknown heartbeat type peer=%s typ=%02X len=%02X frame=%s",
                                    peer,
                                    typ,
                                    len,
                                    frame.joinToString(" ") { "%02X".format(it) }
                                )
                                continue
                            }
                        }
                    }


                    // 不是控制帧，原样吐回
                    frame.forEach { out.add(it) }
                } catch (t: Throwable) {
                    Timber.e(
                        t,
                        "CTRL_PARSE_ERR peer=%s frame=%s",
                        peer,
                        frame.joinToString(" ") { "%02X".format(it) })
                }
            }
        } finally {
            if (pending.isEmpty()) ctrlPendingMap.remove(peer)
            else ctrlPendingMap[peer] = pending
        }

        return out.toByteArray()
    }


    private fun ensureHeartbeatWatchdogStarted() {
        if (heartbeatWatchdog?.isDisposed == false) return

        heartbeatWatchdog = Flowable
            .interval(1, 1, TimeUnit.SECONDS, Schedulers.io())
            .subscribe({
                val now = SystemClock.elapsedRealtime()
                val peers = connectedPeers()

                peers.forEach { peer ->
                    val last = lastHeartbeatAckAt.putIfAbsent(peer, now) ?: now
                    val diff = now - last
                    if (diff > 10000L) {
                        Timber.w("heartbeat timeout, peer=%s diff=%d -> disconnect", peer, diff)
                        server?.closePeer(peer)
                        lastHeartbeatAckAt.remove(peer)

                        if (selectedPeer == peer) {
                            selectedPeer = null
                        }
                    }
                }

                // 顺手清理已经不存在的 peer 的时间戳
                val peerSet = peers.toSet()
                lastHeartbeatAckAt.keys.removeAll { it !in peerSet }
            }, { e ->
                Timber.w(e, "heartbeat watchdog error")
            })
    }



    override fun ensureStopped() {
        heartbeatSender.stop()
        startStopDisposables.clear() // ✅停止前先取消可能在跑的 start 链

        startStopDisposables.add(
            stop()
                .subscribeOn(Schedulers.io())
                .subscribe(
                    { Timber.i("TCP server stopped") },
                    { e -> Timber.w(e, "TCP server stop failed") }
                )
        )
    }

    /**
     * 校验CS
     */
    private fun isValidCs(frame: ByteArray): Boolean {
        if (frame.size < 6) return false
        if (frame[0] != 0x5A.toByte() || frame[1] != 0xA5.toByte()) return false

        val len = frame[2].toInt() and 0xFF
        val total = 2 + 1 + len + 1
        if (frame.size != total) return false

        var sum = 0
        for (i in 2 until frame.size - 1) {
            sum = (sum + (frame[i].toInt() and 0xFF)) and 0xFF
        }

        val actual = frame.last().toInt() and 0xFF
        return sum == actual
    }


    @Synchronized
    fun stop(): Completable =
        Completable.fromAction {
            heartbeatSender.stop()
            heartbeatWatchdog?.dispose()
            heartbeatWatchdog = null
            server?.stop()
            server = null
            ctrlPendingMap.clear()
            lastTcpInAtMs = 0L
            lastTcpOutAtMs = 0L
            _running.onNext(false)
            Timber.i("TCP server stopped")
        }.subscribeOn(Schedulers.io())

    fun send(data: ByteArray): Single<Boolean> =
        Single.fromCallable {
            server?.send(data) ?: false
        }.subscribeOn(Schedulers.io())

    fun sendToPeer(peer: String, data: ByteArray): Single<Boolean> =
        Single.fromCallable { server?.sendTo(peer, data) ?: false }
            .subscribeOn(Schedulers.io())

    /** 给 UI 用：已连接客户端列表（IP:port） */
    fun connectedPeers(): List<String> = server?.getClientPeers() ?: emptyList()

    fun connectedCount(): Int = server?.getClientCount() ?: 0
}