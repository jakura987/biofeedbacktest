package com.intellizon.app.biofeedback.domain.communication.protocol.v1

import com.intellizon.biofeedbacktest.encode.IChannelCoderV1
import com.intellizon.biofeedbacktest.domain.ChannelDetail
import kotlin.math.roundToInt

/**
 * Classname: InterferenceChannelCoderV1 </p>
 * Description: 中频-调制中频电刺激 </p>
 * Created by Nick on 2025/12/1.
 */
class ModulatedChannelCoderV1 : IChannelCoderV1 {

    override fun encodeChannel(channelDetail: ChannelDetail): ByteArray {

        val delaySec = channelDetail.delayTime
        val sustainSec = channelDetail.sustainTime
        val restSec = channelDetail.restTime

        // 脉宽：0~1_000_000 us，3 字节
        val safeWidthUs = (channelDetail.width ?: 0).coerceIn(0, 1_000_000)
        val widthHi = ((safeWidthUs ushr 16) and 0xFF).toByte()
        val widthMid = ((safeWidthUs ushr 8) and 0xFF).toByte()
        val widthLo = (safeWidthUs and 0xFF).toByte()

        // 强度（你现在字段就这个，先沿用）
        val intensity = channelDetail.presetIntensity ?: 0

        // 调幅深度：目前你也复用 presetIntensity，就先复用（后续有新字段再替换）
        val depth = intensity.coerceIn(0, 100)

        // 调制频率 0~150Hz
        val modFreqRaw = (channelDetail.modulationFreq ?: 0).coerceIn(0, 150)

        // 载波频率 tiLoadFreq：kHz，步长 0.5k -> raw=kHz*2
        val carrierKHz = (channelDetail.tiLoadFreq ?: 4.0)
        val carrierRaw = (carrierKHz * 2.0).roundToInt().coerceIn(2, 20)
        val carrierByte = carrierRaw.toByte()


        val out = ByteArray(34) { 0x00 }

        val riseTick = encodeRiseFallHalfTick(channelDetail.riseTime)
        val fallTick = encodeRiseFallHalfTick(channelDetail.fallTime)

        // ===== 0..21 通用块（按低频布局）=====
        out[0] = (0x10 or (0x01 shl (channelDetail.channelName - 1))).and(0xFF).toByte()
        out[1] = channelDetail.waveform.and(0xFF).toByte()            // 刺激波形(载波波形)
        out[2] = 0x00                         // 电流强度
        //临时乘以2
        out[3] = (((channelDetail.amplitude ?: 0) * 2).and(0xFF)).toByte()

        out[4] = widthHi
        out[5] = widthMid
        out[6] = widthLo

        out[7] = encodeDelayTime(delaySec)
        out[8] = channelDetail.frequencyType.and(0xFF).toByte()

        // 中频-调制：这两个频率min/max（低频扫频）通常不用 -> 保持 0
        out[9] = 0x00
        out[10] = 0x00
        out[11] = 0x00
        out[12] = 0x00

//        out[13] = ((rise ushr 8) and 0xFF).toByte()
//        out[14] = ( rise         and 0xFF).toByte()
//        out[15] = ((fall ushr 8) and 0xFF).toByte()
//        out[16] = ( fall         and 0xFF).toByte()

        out[13] = ((riseTick ushr 8) and 0xFF).toByte()   // 对 0..40 这里永远 0
        out[14] = (riseTick and 0xFF).toByte()   // ✅ 7 -> 0x07
        out[15] = ((fallTick ushr 8) and 0xFF).toByte()
        out[16] = (fallTick and 0xFF).toByte()   // ✅ 5 -> 0x05

        out[17] = encodeWorkRestTime(sustainSec)
        out[18] = 0x00
        out[19] = encodeWorkRestTime(restSec)
        out[20] = 0x00
        out[21] = (channelDetail.totalTime ?: 0).and(0xFF).toByte()

        // ===== 22..31 中频扩展块 =====
        out[22] = channelDetail.modulationWaveform.and(0xFF).toByte() // 调制波形
        out[23] = depth.and(0xFF).toByte()                            // 调幅深度(%)
        out[24] = modFreqRaw.and(0xFF).toByte()                       // 调制频率
        out[25] = carrierByte                                         // 载波频率

        // 调制模式没有差频A/B & 周期 -> 保持 0
        // out[26..31] 默认 0

        return out
    }

    override fun decodeChannel(bytes: ByteArray): ChannelDetail {
        throw UnsupportedOperationException("Decode for 中频-调制 尚未实现/未使用")
    }

    private fun encodeDelayTime(delaySec: Double?): Byte {
        if (delaySec == null) return 0x00
        val scaled = (delaySec * 10.0).roundToInt()
        return scaled.coerceIn(0, 0xFF).toByte()
    }

    private fun encodeWorkRestTime(timeSec: Double?): Byte {
        if (timeSec == null) return 0x00
        val scaled = (timeSec * 2.0).roundToInt()
        return scaled.coerceIn(0, 198).toByte()
    }

    private fun encodeRiseFallHalfTick(ms: Double?): Int {
        if (ms == null) return 0
        return (ms * 2.0).roundToInt().coerceIn(0, 99)
    }
}
