package com.intellizon.biofeedbacktest.encode

import com.intellizon.biofeedbacktest.domain.ChannelDetail
import kotlin.math.roundToInt

/**
 * Classname: EQChannelCoderV1 </p>
 * Description: 中频-等幅中频电刺激 </p>
 * Created by Nick on 2025/12/1.
 */
class EQChannelCoderV1 : IChannelCoderV1 {

    override fun encodeChannel(channelDetail: ChannelDetail): ByteArray {

        // 载波频率 tiLoadFreq：kHz，步长 0.5k -> raw=kHz*2
        val carrierKHz = (channelDetail.tiLoadFreq ?: 4.0)
        val carrierRaw = encodeCarrierFreq(carrierKHz)

        val out = ByteArray(34) { 0x00 }

        // ===== 0..21 通用块（按低频布局）=====
        out[0] = (0x10 or (0x01 shl (channelDetail.channelName - 1))).and(0xFF).toByte()
        out[1] = channelDetail.waveform.and(0xFF).toByte()            // 刺激波形(载波波形)
        out[2] = 0x00                         // 电流强度
        out[3] = 0x00

        out[4] = 0x00
        out[5] = 0x00
        out[6] = 0x00

        out[7] = 0x00 //延时时间
        out[8] = 0x00 //frequencyType

        // 调制频率
        out[9] = 0x00
        out[10] = 0x00

        //干扰电 差频频率
        out[11] = 0x00
        out[12] = 0x00


        out[13] = 0x00
        out[14] = 0x00
        out[15] = 0x00
        out[16] = 0x00

        out[17] = 0x00
        out[18] = 0x00
        out[19] = 0x00
        out[20] = 0x00
        out[21] = (channelDetail.totalTime ?: 0).and(0xFF).toByte()

        // ===== 22..31 中频扩展块 =====
        out[22] = channelDetail.modulationWaveform.and(0xFF).toByte() // 调制波形
        out[23] = 0x00

        //out[24]-out[26] carrierByte (工作频率)
        // 3字节：高->中->低
        out[24] = ((carrierRaw shr 16) and 0xFF).toByte()
        out[25] = ((carrierRaw shr 8) and 0xFF).toByte()
        out[26] = (carrierRaw and 0xFF).toByte()

        // 调制模式没有差频A/B & 周期 -> 保持 0
        // out[27..32] 默认 0
        out[33] = 0x00 //预留

        return out
    }

    override fun decodeChannel(bytes: ByteArray): ChannelDetail {
        throw UnsupportedOperationException("Decode for 中频-调制 尚未实现/未使用")
    }

    private fun encodeCarrierFreq(kHz: Double?): Int {
        if (kHz == null) return 0
        return (kHz * 10000.0).roundToInt()
    }
}
