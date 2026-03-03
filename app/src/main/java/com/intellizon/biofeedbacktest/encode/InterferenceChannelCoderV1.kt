package com.intellizon.app.biofeedback.domain.communication.protocol.v1
import com.intellizon.biofeedbacktest.encode.IChannelCoderV1
import com.intellizon.biofeedbacktest.domain.ChannelDetail
import kotlin.math.roundToInt

/**
 * Classname: InterferenceChannelCoderV1 </p>
 * Description: 中频-干扰电刺激</p>
 * Created by Nick on 2025/12/1.
 */
class InterferenceChannelCoderV1 : IChannelCoderV1 {

    override fun encodeChannel(channelDetail: ChannelDetail): ByteArray {

        val delaySec = channelDetail.delayTime
        val sustainSec = channelDetail.sustainTime
        val restSec = channelDetail.restTime
        // 脉宽：干扰电一般不用，你现在有字段就照写；没有就会默认 0
        val safeWidthUs = (channelDetail.width ?: 0).coerceIn(0, 1_000_000)
        val widthHi = ((safeWidthUs ushr 16) and 0xFF).toByte()
        val widthMid = ((safeWidthUs ushr 8) and 0xFF).toByte()
        val widthLo = (safeWidthUs and 0xFF).toByte()

        val intensity = channelDetail.presetIntensity ?: 0
        val depth = intensity.coerceIn(0, 100)

        val modFreqRaw = (channelDetail.modulationFreq ?: 0).coerceIn(0, 150)

        val carrierKHz = (channelDetail.tiLoadFreq ?: 4.0)
        val carrierRaw = (carrierKHz * 2.0).roundToInt().coerceIn(2, 20)
        val carrierByte = carrierRaw.toByte()

        // 差频A/B：0~200Hz，1Hz步长
        val diffA = (channelDetail.frequencyMin ?: 0.0).roundToInt().coerceIn(0, 200)
        val diffB = (channelDetail.frequencyMax ?: 0.0).roundToInt().coerceIn(0, 200)

        val diffAHi = ((diffA ushr 8) and 0xFF).toByte()
        val diffALo = (diffA and 0xFF).toByte()
        val diffBHi = ((diffB ushr 8) and 0xFF).toByte()
        val diffBLo = (diffB and 0xFF).toByte()

        // 差频周期/动态周期：来自 Therapy 级别注入到 ChannelDetail 的字段
        val diffPeriodRaw = (channelDetail.frequencyShift ?: 15).coerceIn(15, 30)
        val dynamicPeriodRaw = (channelDetail.dynamicShifts ?: 4).coerceIn(4, 10)


        val out = ByteArray(34) { 0x00 }

        val riseTick = encodeRiseFallHalfTick(channelDetail.riseTime)
        val fallTick = encodeRiseFallHalfTick(channelDetail.fallTime)

        // ===== 0..21 通用块（按低频布局）=====
        out[0] = (0x10 or (0x01 shl (channelDetail.channelName - 1))).and(0xFF).toByte()
        out[1] = channelDetail.waveform.and(0xFF).toByte()
        out[2] = 0x00
        out[3] = 0x00

        out[4] = widthHi
        out[5] = widthMid
        out[6] = widthLo

        out[7] = encodeDelayTime(delaySec)
        out[8] = channelDetail.frequencyType.and(0xFF).toByte()

        // 干扰电：低频那套扫频 min/max 不用 -> 保持 0
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
        out[22] = channelDetail.modulationWaveform.and(0xFF).toByte()
        out[23] = depth.and(0xFF).toByte()
        out[24] = modFreqRaw.and(0xFF).toByte()
        out[25] = carrierByte

        out[26] = diffAHi
        out[27] = diffALo
        out[28] = diffBHi
        out[29] = diffBLo

        out[30] = diffPeriodRaw.and(0xFF).toByte()
        out[31] = dynamicPeriodRaw.and(0xFF).toByte()

        return out
    }

    override fun decodeChannel(bytes: ByteArray): ChannelDetail {
        throw UnsupportedOperationException("Decode for 中频-干扰电 尚未实现/未使用")
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
        return (ms * 2.0).roundToInt().coerceIn(0, 198)
    }
}
