//package com.intellizon.biofeedbacktest.wifi
//
//import android.annotation.SuppressLint
//import com.intellizon.biofeedbacktest.domain.TherapyDetail
//import com.intellizon.biofeedbacktest.domain.TherapyParamMode
//import com.intellizon.biofeedbacktest.encode.AbsTherapyCoder
//import com.intellizon.biofeedbacktest.encode.TherapyCoderV1
//import io.reactivex.Completable
//import io.reactivex.Observable
//import io.reactivex.schedulers.Schedulers
//import timber.log.Timber
//import java.util.concurrent.TimeUnit
//import javax.inject.Inject
//
///**
// * WiFi/TCP 版 PostTherapy，下发治疗方案相关的 SDU。
// * 目前只实现 sendName（方案名），后续可以逐步补 sendBasicSetting/sendChannel 等。
// */
//class WifiPostTherapyOperation @Inject constructor(
//    //private val wifiTcpServerManager: WifiTcpServerManager,
//
//    ) {
//
//    private val coder: AbsTherapyCoder = TherapyCoderV1()
//    private val dataPerPage = 0x20 - 3
//    private val TAG = "postOperation"
//
//
//    // SDU 扩展：打印十六进制串（和原来 PostTherapyOperation 一样）
//    private fun SDU.toHex(): String =
//        toBytes().joinToString(" ") { b -> "%02X".format(b) }
//
//    // 通用 hex 工具：任何 ByteArray 都可以用
//    private fun ByteArray.toHex(): String =
//        joinToString(" ") { b -> "%02X".format(b) }
//
//
//
//
//    /** ⚡ 完全照抄原来的 buildNameObservable，只是挪到 WiFi 这边来复用  */
//    private fun buildNameObservable(allName: ByteArray): Observable<ByteArray> {
//        if (allName.isEmpty()) {
//            // 空名字占位： [0x01, 0x00, 0x00]
//            return Observable.just(byteArrayOf(0x01, 0x00, 0x00))
//        }
//        val size = allName[0]
//        if (size <= dataPerPage) {
//            val data = ByteArray(0x20)
//            data[0] = 0x01
//            data[1] = size
//            data[2] = 0x00
//
//            for (i in 3 until 0x20) {
//                data[i] = if (i - 2 < allName.size) {
//                    allName[i - 2]
//                } else {
//                    0x00
//                }
//            }
//            return Observable.just(data)
//        }
//
//        val pages = size / dataPerPage + if (size % dataPerPage != 0) 1 else 0
//        return Observable
//            .range(0, pages)
//            .map { index ->
//                val data = ByteArray(0x20)
//                data[0] = 0x01
//                data[1] = size
//                data[2] = index.toByte()
//
//                for (i in 3 until 0x20) {
//                    val curIndex = i - 2 + index * dataPerPage
//                    data[i] = if (curIndex < allName.size) {
//                        allName[curIndex]
//                    } else {
//                        0x00
//                    }
//                }
//                data
//            }
//    }
//
//    /**
//     * 合并一帧发送
//     */
//    @SuppressLint("CheckResult")
//    fun sendPresetAllInOne(detail: TherapyDetail): Completable {
//        val schemeNo: Byte = 0x01 // 你现有体系基本都写死 0x01，这里先沿用
//
//        // mode：直接用你现有的映射（0x04/0x41/0x42/0x51/0x52...）
//        val paramModeInt = TherapyParamMode.create(detail.mode, detail.subMode)
//        val modeByte = paramModeInt.toByte()
//
//        // 方案名页：固定 32B（byte0..31）
//        val namePage32 = buildNamePage32(detail, schemeNo)
//
//        // 通道 flag：按你旧逻辑（含 TPAS 特判，先保留）
//        val chFlag = buildChannelFlag(modeByte, detail)
//
//        // 通道块：按 A/B/C/D 收集，拼成 22B×N
//        val channelBytes = buildChannelBlocks(detail, paramModeInt)
//
//        if (channelBytes.isEmpty()) {
//            Timber.tag(TAG).d("sendPresetAllInOne: no channel enabled, skip")
//            return Completable.complete()
//        }
//
//        // payload = 32(name) + 2(mode+flag) + 22*N(channel blocks)
//        val payload = ByteArray(32 + 2 + channelBytes.size)
//        var off = 0
//        System.arraycopy(namePage32, 0, payload, off, 32); off += 32
//        payload[off++] = modeByte
//        payload[off++] = chFlag
//        System.arraycopy(channelBytes, 0, payload, off, channelBytes.size)
//
//        val sdu = SDU.create(
//            ICHospitalProtocol.THERAPY.CMD,       // 0x20
//            ICHospitalProtocol.THERAPY.TYPE_CHANNEL, // 0x12（现在代表合并写入）
//            payload
//        )
//
//        val bytes = sdu.toBytes()
//        Timber.tag(TAG).d("→ tcp sendPresetAllInOne payload(%d): %s", payload.size, payload.toHex())
//        Timber.tag(TAG).d("→ tcp sendPresetAllInOne SDU: %s", sdu.toHex())
//
//        Timber.tag(TAG).d("→ tcp sendPresetAllInOne BYTES(%d): %s", bytes.size, bytes.toHex())
//        Timber.tag(TAG).d("→ tcp sendPresetAllInOne HEADER: %s", bytes.take(5).toByteArray().toHex())
//
//        return wifiTcpServerManager
//            .send(bytes)
//            .flatMapCompletable { ok ->
//                if (!ok) {
//                    Completable.error(RuntimeException("tcp sendPresetAllInOne failed (send=false)"))
//                } else {
//                    // 继续沿用你的策略：ACK 后台等，不影响 UI
//                    waitResult(
//                        ICHospitalProtocol.THERAPY.CMD,
//                        ICHospitalProtocol.THERAPY.TYPE_CHANNEL
//                    )
//                        .subscribeOn(Schedulers.io())
//                        .subscribe(
//                            { Timber.tag(TAG).d("waitPresetAllInOneAck: complete") },
//                            { e -> Timber.tag(TAG).e(e, "waitPresetAllInOneAck: timeout/no ack") }
//                        )
//                    Completable.complete()
//                }
//            }
//            .doOnError { e ->
//                Timber.tag(TAG).e(e, "sendPresetAllInOne error: ${e.javaClass.simpleName} ${e.message}")
//            }
//            .subscribeOn(Schedulers.io())
//    }
//
//
//    private fun buildNamePage32(detail: TherapyDetail, schemeNo: Byte): ByteArray {
//        val encoded = coder.encodeName(detail) // [len][utf8...]
//        if (encoded.isEmpty()) {
//            // 空名：len=0
//            return ByteArray(32).apply {
//                this[0] = schemeNo
//                this[1] = 0x00
//                this[2] = 0x00
//            }
//        }
//
//        val len = encoded[0].toInt() and 0xFF
//        require(len <= 29) { "Name too long for all-in-one: $len (max 29 bytes)" }
//
//        val page = ByteArray(32) { 0x00 }
//        page[0] = schemeNo
//        page[1] = len.toByte()
//        page[2] = 0x00 // 分页号固定 0
//
//        if (len > 0) {
//            // encoded[1..len] -> page[3..3+len-1]
//            System.arraycopy(encoded, 1, page, 3, len)
//        }
//        return page
//    }
//
//    private fun buildChannelFlag(paramMode: Byte, detail: TherapyDetail): Byte {
//        // 你原先 TPAS 特判：固定 A+B
//        if (paramMode == TherapyParamMode.OTHER_TPAS.toByte()) {
//            return (0b0001 or 0b0010).toByte()
//        }
//
//        var flag = 0
//        if (detail.hasChannelA) flag = flag or 0b0001
//        if (detail.hasChannelB) flag = flag or 0b0010
//        if (detail.hasChannelC) flag = flag or 0b0100
//        if (detail.hasChannelD) flag = flag or 0b1000
//        return flag.toByte()
//    }
//
//
//
//
//
//
//    /** 通用：判断 data 是否为某个 cmd + type 的 ACK 帧 */
//    private fun isAckOf(data: ByteArray, cmd: Byte, type: Byte): Boolean {
//        if (data.size < 5) return false
//        // 帧头 5A A5
//        if (data[0] != 0x5A.toByte() || data[1] != 0xA5.toByte()) return false
//        // CMD
//        if (data[3] != cmd) return false
//        // TYPE
//        if (data[4] != type) return false
//        return true
//    }
//
//
//    /**
//     * 通用 waitResult：等待某个 cmd + type 的一帧 ACK，超时抛 TimeoutException
//     * （语义上就对应 BLE 的 communicator.waitResult(cmd, type, timeout)）
//     */
//    private fun waitResult(cmd: Byte, type: Byte, timeoutMs: Long = 2000L): Completable {
//        return wifiTcpServerManager
//            .incoming()
//            .filter { data ->
//                isAckOf(data, cmd, type)
//            }
//            .firstOrError()          // Single<ByteArray>
//            .doOnSuccess { ack ->
//                Timber.tag(TAG).d("← tcp ACK: %s", ack.toHex())
//            }
//            .ignoreElement()         // Completable
//            .timeout(timeoutMs, TimeUnit.MILLISECONDS)
//    }
//
//
//}
