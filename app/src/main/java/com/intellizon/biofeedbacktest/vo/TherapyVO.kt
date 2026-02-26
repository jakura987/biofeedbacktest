package com.intellizon.biofeedbacktest.vo

import com.intellizon.biofeedbacktest.domain.ChannelDetail
import com.intellizon.biofeedbacktest.domain.ChannelName
import com.intellizon.biofeedbacktest.domain.FrequencyType
import com.intellizon.biofeedbacktest.domain.TherapyDetail
import com.intellizon.biofeedbacktest.domain.Waveform

class TherapyVO(
    val therapyId: Long = 0L,
    val modifiedDto: TherapyDetail,
) {
    // LOW 四通道缓存
    private val low: MutableMap<Int, ChannelDetail> = mutableMapOf()

    /** 取 LOW 指定通道：无则创建默认并缓存 */
    fun getOrCreateLowChannel(@ChannelName ch: Int): ChannelDetail {
        return low[ch] ?: defaultLowChannel(therapyId, ch).also { low[ch] = it }
    }

    /** 提交/覆盖 LOW 指定通道 */
    fun putLowChannel(detail: ChannelDetail) {
        low[detail.channelName] = detail
    }

    fun getLowChannelOrNull(@ChannelName ch: Int): ChannelDetail? = low[ch]

    private fun defaultLowChannel(therapyId: Long, @ChannelName ch: Int): ChannelDetail {
        return ChannelDetail(
            therapyId = therapyId,
            channelName = ch,
            waveform = Waveform.BIPHASIC_SQUARE,
            modulationWaveform = Waveform.BIPHASIC_SQUARE,
            presetIntensity = 0,
            width = null,
            frequencyType = FrequencyType.CONSTANT_FREQUENCY,
            frequencyMin = null,
            frequencyMax = null,
            tiLoadFreq = null,
            modulationFreq = null,
            riseTime = null,
            fallTime = null,
            restTime = null,
            sustainTime = null,
            delayTime = 0.0, // 先只管这个
            totalTime = null,
            contractionTimeSec = 0,
            stimulationTimeSec = 0,
            partDescription = null,
            subMode = null,
            frequencyShift = null,
            dynamicShifts = null,
        )
    }
}