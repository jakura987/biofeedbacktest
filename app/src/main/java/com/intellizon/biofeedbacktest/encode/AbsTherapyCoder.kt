package com.intellizon.biofeedbacktest.encode

import com.intellizon.biofeedbacktest.domain.ChannelDetail
import com.intellizon.biofeedbacktest.domain.ChannelName
import com.intellizon.biofeedbacktest.domain.TherapyDetail
import com.intellizon.biofeedbacktest.domain.TherapyParamMode


/**
 * Classname: AbsTherapyCoder
 */
abstract class AbsTherapyCoder {
    /**
     * 64 byte
     * */
    val maxNameBytes: Int = 0x40

    abstract val basicSettingLength: Int

    abstract val channelLength: Int

    abstract fun encodeName(therapyDetail: TherapyDetail): ByteArray

    abstract fun encodeBasicSettings(therapyDetail: TherapyDetail): ByteArray

    abstract fun encodeChannel(@TherapyParamMode mode: Int, channelDetail: ChannelDetail): ByteArray

    //abstract fun decodeChannel(@TherapyParamMode mode: Int, bytes: ByteArray, bodyPart: String = ChannelDetail.DEFAULT_BODY_PART): ChannelDetail

    abstract fun encode(therapyDetail: TherapyDetail): ByteArray

//    @Throws(ParameterError::class)
//    abstract fun decode(bytes: ByteArray, reference: TherapyDetail? = null): TherapyDetail

    companion object {
        @ChannelName
        fun Int.channelFlag(): Int {
            when {
                this.and(0b0001) != 0 -> return ChannelName.CHANNEL_A
                this.and(0b0010) != 0 -> return ChannelName.CHANNEL_B
                this.and(0b0100) != 0 -> return ChannelName.CHANNEL_C
                this.and(0b1000) != 0 -> return ChannelName.CHANNEL_D
            }
            return ChannelName.CHANNEL_A
        }
    }
}
