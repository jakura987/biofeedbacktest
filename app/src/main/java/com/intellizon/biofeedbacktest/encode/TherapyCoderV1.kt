package com.intellizon.biofeedbacktest.encode

import com.intellizon.app.biofeedback.domain.communication.protocol.v1.InterferenceChannelCoderV1
import com.intellizon.app.biofeedback.domain.communication.protocol.v1.ModulatedChannelCoderV1
import com.intellizon.biofeedbacktest.domain.ChannelDetail
import com.intellizon.biofeedbacktest.domain.ChannelName
import com.intellizon.biofeedbacktest.domain.TherapyDetail
import com.intellizon.biofeedbacktest.domain.TherapyParamMode


/**
 * Classname: TherapyCoderV1 </p>
 * Created by Lenovo on 2024/10/22.
 */
class TherapyCoderV1 : AbsTherapyCoder() {

    companion object {
        val INSTANCE = TherapyCoderV1()
    }

    override val basicSettingLength: Int = 5

    override val channelLength: Int = 34

    private val lmFreqChannelCoderV1: IChannelCoderV1 = LMFreqChannelCoderV1()
    private val interferenceChannelCoderV1: IChannelCoderV1 = InterferenceChannelCoderV1()
    private val modulatedChannelCoderV1: IChannelCoderV1 = ModulatedChannelCoderV1()
    private val equalChannelCoderV1: IChannelCoderV1 = EQChannelCoderV1()


    override fun encodeName(therapyDetail: TherapyDetail): ByteArray {
        val data = therapyDetail.name.toByteArray(Charsets.UTF_8).apply {
            if (size > maxNameBytes) {
                throw IllegalArgumentException("Name too long")
            }
        }
        return byteArrayOf(data.size.and(0xff).toByte()) + data
    }

    override fun encodeBasicSettings(therapyDetail: TherapyDetail): ByteArray {
        val paramMode = TherapyParamMode.create(therapyDetail.mode, therapyDetail.subMode).toByte()

        return byteArrayOf(
            // 00
            0x01, //data0(方案号=0x01),
            // 01
            paramMode, //data1(模式码=现在的0x41/0x42/0x51/0x52等),
            // 02
            encodeFlag(paramMode, therapyDetail), // data2(通道Flag),
            // 03
            therapyDetail.frequencyShift.and(0xff).toByte(), // data3(频差),
            // 04
            0x00, // 震颤参数，不使用  data4(震颤切换频率)
        )
    }

    private fun encodeFlag(paramMode: Byte, therapyDetail: TherapyDetail): Byte {
        if (paramMode == TherapyParamMode.OTHER_TPAS.toByte()) {
            return (0b0001.or(0b0010)).toByte()
        }

        return 0x00.run {
            if (therapyDetail.hasChannelA) {
                this.or(0b0001)
            } else {
                this
            }
        }.run {
            if (therapyDetail.hasChannelB) {
                this.or(0b0010)
            } else {
                this
            }
        }.run {
            if (therapyDetail.hasChannelC) {
                this.or(0b0100)
            } else {
                this
            }
        }.run {
            if (therapyDetail.hasChannelD) {
                this.or(0b1000)
            } else {
                this
            }
        }.and(0xff).toByte()
    }

    private fun getChannelCoder(@TherapyParamMode mode: Int): IChannelCoderV1 {
        return when (mode) {
            TherapyParamMode.BASIC_TEST -> lmFreqChannelCoderV1

            TherapyParamMode.MIDDLE_INTERFERENCE -> interferenceChannelCoderV1
            TherapyParamMode.MIDDLE_MODULATED -> modulatedChannelCoderV1
            TherapyParamMode.MIDDLE_EQUAL -> equalChannelCoderV1

            TherapyParamMode.LOW_FREQ,
            TherapyParamMode.BIOFEEDBACK_ETS,
            TherapyParamMode.BIOFEEDBACK_CCFES ->
                lmFreqChannelCoderV1


            else -> throw IllegalArgumentException("模式不支持")
        }
    }

    override fun encodeChannel(mode: Int, channelDetail: ChannelDetail): ByteArray {
        return getChannelCoder(mode).encodeChannel(channelDetail)
    }


    override fun encode(therapyDetail: TherapyDetail): ByteArray {
        var ret = encodeName(therapyDetail) + encodeBasicSettings(therapyDetail)

        return ret
    }


    fun encode(
        therapyDetail: TherapyDetail,
        channelsByName: Map<Int, ChannelDetail>, // key=1..4
    ): ByteArray {
        val paramMode = TherapyParamMode.create(therapyDetail.mode, therapyDetail.subMode)

        var ret = encodeName(therapyDetail) + encodeBasicSettings(therapyDetail)

        fun appendIf(has: Boolean, ch: Int) {
            if (!has) return
            val detail = channelsByName[ch] ?: return
            ret += encodeChannel(paramMode, detail)
        }

        appendIf(therapyDetail.hasChannelA, 1)
        appendIf(therapyDetail.hasChannelB, 2)
        appendIf(therapyDetail.hasChannelC, 3)
        appendIf(therapyDetail.hasChannelD, 4)

        return ret
    }

}
