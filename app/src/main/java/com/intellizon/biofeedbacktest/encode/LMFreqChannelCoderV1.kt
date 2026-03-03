package com.intellizon.biofeedbacktest.encode


import com.intellizon.biofeedbacktest.domain.ChannelDetail
import kotlin.math.roundToInt

/**
 * Classname: LMFreqChannelCoderV1 </p>
 * Description: 低频、ETS CCFES 通道序列化和反序列化 </p>
 */
class LMFreqChannelCoderV1 : IChannelCoderV1 {

    override fun encodeChannel(channelDetail: ChannelDetail): ByteArray {
        // mode 对低频来说可以不用（留着统一接口/方便日志）
        // Timber.d("LMFreq encode, mode=%02X", mode)

        val safeWidthUs = (channelDetail.width ?: 10).coerceIn(10, 1_000_000)
        val widthHi  = ((safeWidthUs ushr 16) and 0xFF).toByte()
        val widthMid = ((safeWidthUs ushr 8)  and 0xFF).toByte()
        val widthLo  = ( safeWidthUs          and 0xFF).toByte()

        val fMinRaw: Int = ((channelDetail.frequencyMin ?: 0.0) * 10.0)
            .roundToInt()
            .coerceIn(0, 10_000)

        val fMaxRaw: Int = ((channelDetail.frequencyMax ?: 0.0) * 10.0)
            .roundToInt()
            .coerceIn(0, 10_000)

        val out = ByteArray(34) { 0x00 } // ✅ 固定 34B，尾部自动补 0

        val riseTick = encodeRiseFallHalfTick(channelDetail.riseTime)
        val fallTick = encodeRiseFallHalfTick(channelDetail.fallTime)

        // --- 0..21：原低频字段 ---
        out[0]  = (0x10 or (0x01 shl (channelDetail.channelName - 1))).and(0xFF).toByte()
        out[1]  = channelDetail.waveform.and(0xFF).toByte()
        out[2]  = 0x00
        out[3]  = 0x00

        out[4]  = widthHi
        out[5]  = widthMid
        out[6]  = widthLo

        out[7]  = encodeDelayTime(channelDetail.delayTime)
        out[8]  = channelDetail.frequencyType.and(0xFF).toByte()

        out[9]  = ((fMinRaw ushr 8) and 0xFF).toByte()
        out[10] = ( fMinRaw         and 0xFF).toByte()
        out[11] = ((fMaxRaw ushr 8) and 0xFF).toByte()
        out[12] = ( fMaxRaw         and 0xFF).toByte()

        //out[13] = channelDetail.riseTime?.ushr(8)?.and(0xFF)?.toByte() ?: 0x00
        //out[14] = channelDetail.riseTime?.and(0xFF)?.toByte() ?: 0x00

        out[13] = ((riseTick ushr 8) and 0xFF).toByte()   // 对 0..40 这里永远 0
        out[14] = ( riseTick         and 0xFF).toByte()   // ✅ 7 -> 0x07


//        out[15] = channelDetail.fallTime?.ushr(8)?.and(0xFF)?.toByte() ?: 0x00
//        out[16] = channelDetail.fallTime?.and(0xFF)?.toByte() ?: 0x00

        out[15] = ((fallTick ushr 8) and 0xFF).toByte()
        out[16] = ( fallTick         and 0xFF).toByte()   // ✅ 5 -> 0x05

        out[17] = encodeWorkRestTime(channelDetail.sustainTime)
        out[18] = 0x00
        out[19] = encodeWorkRestTime(channelDetail.restTime)
        out[20] = 0x00
        out[21] = channelDetail.totalTime?.and(0xFF)?.toByte() ?: 0x00

        // --- 22..31：中频扩展字段，低频不使用 -> 保持 0 ---
        // out[22] modulationWaveform
        // out[23] depth
        // out[24] modulationFreq
        // out[25] carrier
        // out[26..27] diffA
        // out[28..29] diffB
        // out[30] diffPeriod
        // out[31] dynamicPeriod

        out[32] = channelDetail.contractionTimeSec.and(0xFF).toByte() ?: 0x00
        out[33] = channelDetail.stimulationTimeSec.and(0xFF).toByte() ?: 0x00

        return out
    }

    override fun decodeChannel(bytes: ByteArray): ChannelDetail {
        throw UnsupportedOperationException("Decode not used")
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
        val scaled = (ms * 2.0).roundToInt()
        return scaled
    }

}
