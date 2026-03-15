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

        val sustainSec = channelDetail.sustainTime
        val restSec = channelDetail.restTime

        val intensity = channelDetail.presetIntensity ?: 0
        val depth = intensity.coerceIn(0, 100)

        val carrierKHz = (channelDetail.tiLoadFreq ?: 4.0)
        val carrierRaw = encodeCarrierFreq(carrierKHz)

        //调制频率
        val freqMin = ((channelDetail.frequencyMin ?: 0.0) * 10.0)
            .roundToInt()
            .coerceIn(0, 0xFFFF)

        //差频频率
        val freqMax = (channelDetail.frequencyMax ?: 0.0)
            .roundToInt()
            .coerceIn(0, 200)


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
        out[3] = (((channelDetail.amplitude ?: 0) * 2).and(0xFF)).toByte()

        //脉宽
        out[4] = 0x00
        out[5] = 0x00
        out[6] = 0x00

        //延时时间
        out[7] = 0x00

        //type
        out[8] = channelDetail.frequencyType.and(0xFF).toByte()

        // 调制频率
        out[9] = ((freqMin shr 8) and 0xFF).toByte()
        out[10] = (freqMin and 0xFF).toByte()

        //干扰电 差频频率
        out[11] = ((freqMax shr 8) and 0xFF).toByte()
        out[12] = (freqMax and 0xFF).toByte()

        out[13] = ((riseTick ushr 8) and 0xFF).toByte()   // 对 0..40 这里永远 0
        out[14] = (riseTick and 0xFF).toByte()   // ✅ 7 -> 0x07
        out[15] = ((fallTick ushr 8) and 0xFF).toByte()
        out[16] = (fallTick and 0xFF).toByte()   // ✅ 5 -> 0x05

        out[17] = encodeWorkRestTime(sustainSec)
        out[18] = 0x00
        out[19] = encodeWorkRestTime(restSec)
        out[20] = 0x00
        out[21] = (channelDetail.totalTime ?: 0).and(0xFF).toByte()

        // ===== 22..33 中频扩展块 =====
        out[22] = channelDetail.modulationWaveform.and(0xFF).toByte()
        out[23] = depth.and(0xFF).toByte()


        //out[24]-out[26] carrierByte (工作频率)
        // 3字节：高->中->低
        out[24] = ((carrierRaw shr 16) and 0xFF).toByte()
        out[25] = ((carrierRaw shr 8) and 0xFF).toByte()
        out[26] = (carrierRaw and 0xFF).toByte()

        //差频周期
        out[27] = diffPeriodRaw.and(0xFF).toByte()
        //动态周期
        out[28] = dynamicPeriodRaw.and(0xFF).toByte()

        //out[29] 放松
        //out[30] 收缩
        //out[31-32] 触发阈值
        out[33] = 0x00 //预留

        return out
    }

    override fun decodeChannel(bytes: ByteArray): ChannelDetail {
        throw UnsupportedOperationException("Decode for 中频-干扰电 尚未实现/未使用")
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

    private fun encodeCarrierFreq(kHz: Double?): Int {
        if (kHz == null) return 0
        return (kHz * 10000.0).roundToInt()
    }
}
