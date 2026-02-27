package com.intellizon.biofeedbacktest.vo

import com.intellizon.biofeedbacktest.domain.ChannelDetail
import com.intellizon.biofeedbacktest.domain.ChannelName
import com.intellizon.biofeedbacktest.domain.FrequencyType
import com.intellizon.biofeedbacktest.domain.TherapyDetail
import com.intellizon.biofeedbacktest.domain.TherapyMode
import com.intellizon.biofeedbacktest.domain.Waveform

class TherapyVO(
    val therapyId: Long = 0L,
    var modifiedDto: TherapyDetail,
) {
    // mode -> (channelName -> ChannelDetail)
    private val channelsByMode: MutableMap<Int, MutableMap<Int, ChannelDetail>> = mutableMapOf()

    fun getOrCreateChannel(@TherapyMode mode: Int, @ChannelName ch: Int): ChannelDetail {
        val perMode = channelsByMode.getOrPut(mode) { mutableMapOf() }
        return perMode[ch] ?: defaultChannel(therapyId, mode, ch).also { perMode[ch] = it }
    }

    fun putChannel(@TherapyMode mode: Int, detail: ChannelDetail) {
        val perMode = channelsByMode.getOrPut(mode) { mutableMapOf() }
        perMode[detail.channelName] = detail
    }

    fun getChannelOrNull(@TherapyMode mode: Int, @ChannelName ch: Int): ChannelDetail? {
        return channelsByMode[mode]?.get(ch)
    }

    private fun defaultChannel(therapyId: Long, @TherapyMode mode: Int, @ChannelName ch: Int): ChannelDetail {
        // 先统一默认（你阉割版只关心 delayTime 也没问题）
        return ChannelDetail(
            therapyId = therapyId,
            channelName = ch,
            waveform = Waveform.BIPHASIC_SQUARE,
            modulationWaveform = Waveform.UNIPHASIC_SQUARE,//低频不会发出这个属性
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
            delayTime = 0.0,
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