package com.intellizon.biofeedbacktest.wifi.manager

import com.intellizon.biofeedbacktest.domain.TcpPacket
import com.intellizon.biofeedbacktest.wifi.connect.SimpleTcpServer
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class WifiTcpServerManager @Inject constructor() {

    private var server: SimpleTcpServer? = null

    private val _running = BehaviorProcessor.createDefault(false)
    fun running(): Flowable<Boolean> = _running.onBackpressureLatest()

    // 收到的数据（原样透传）
    private val _incoming = PublishProcessor.create<TcpPacket>()
    fun incoming(): Flowable<TcpPacket> = _incoming.onBackpressureBuffer()

    @Synchronized
    fun startIfNeeded(port: Int = 8883): Completable =
        Completable.fromAction {
            if (server?.isRunning == true) return@fromAction

            Timber.i("Starting TCP server on 0.0.0.0:%d", port)

            server = SimpleTcpServer(port) { peer, data, len ->
                // listener 回调在 socket 线程池里：这里尽量轻量
                val copy = data.copyOf(len)
                _incoming.onNext(TcpPacket(peer, copy))
            }.also { it.start() }

            _running.onNext(true)
        }.subscribeOn(Schedulers.io())

    @Synchronized
    fun stop(): Completable =
        Completable.fromAction {
            server?.stop()
            server = null
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