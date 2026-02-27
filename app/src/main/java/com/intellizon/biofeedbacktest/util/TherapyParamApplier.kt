package com.intellizon.biofeedbacktest.util

import com.intellizon.biofeedbacktest.vo.TherapyVO
import com.intellizon.biofeedbacktest.domain.ChannelDetail
import com.intellizon.biofeedbacktest.domain.ChannelName
import com.intellizon.biofeedbacktest.domain.TherapyMode
import com.intellizon.biofeedbacktest.domain.TherapyParamMode
import com.intellizon.biofeedbacktest.domain.Waveform

object TherapyChannelApplier {

    fun applyParamModeToAllChannels(therapyVO: TherapyVO, @TherapyMode mode: Int, @TherapyParamMode paramMode: Int) {
        therapyVO.modifiedDto = therapyVO.modifiedDto.copy(subMode = paramMode)
        for (ch in arrayOf(ChannelName.CHANNEL_A, ChannelName.CHANNEL_B, ChannelName.CHANNEL_C, ChannelName.CHANNEL_D)) {
            val old = therapyVO.getOrCreateChannel(mode, ch)
            therapyVO.putChannel(mode, old.copy(subMode = paramMode))
        }
    }

    fun applyWaveformToAllChannels(therapyVO: TherapyVO, @TherapyMode mode: Int, @Waveform wf: Int) {
        for (ch in arrayOf(ChannelName.CHANNEL_A, ChannelName.CHANNEL_B, ChannelName.CHANNEL_C, ChannelName.CHANNEL_D)) {
            val old = therapyVO.getOrCreateChannel(mode, ch)
            therapyVO.putChannel(mode, old.copy(waveform = wf))
        }
    }

    fun applyModulationWaveformToAllChannels(therapyVO: TherapyVO, @TherapyMode mode: Int, @Waveform wf: Int) {
        for (ch in arrayOf(ChannelName.CHANNEL_A, ChannelName.CHANNEL_B, ChannelName.CHANNEL_C, ChannelName.CHANNEL_D)) {
            val old = therapyVO.getOrCreateChannel(mode, ch)
            therapyVO.putChannel(mode, old.copy(modulationWaveform = wf)) // 按你字段名改
        }
    }

    /** 可选：中频一键默认（干扰/调制） */
    fun applyMiddleDefaults(therapyVO: TherapyVO, @TherapyParamMode paramMode: Int) {
        applyParamModeToAllChannels(therapyVO, TherapyMode.MIDDLE, paramMode)
        when (paramMode) {
            TherapyParamMode.MIDDLE_INTERFERENCE -> {
                applyWaveformToAllChannels(therapyVO, TherapyMode.MIDDLE, Waveform.SINE)
                applyModulationWaveformToAllChannels(therapyVO, TherapyMode.MIDDLE, Waveform.TRIANGLE)
            }
            TherapyParamMode.MIDDLE_MODULATED -> {
                applyWaveformToAllChannels(therapyVO, TherapyMode.MIDDLE, Waveform.BIPHASIC_SQUARE)
                applyModulationWaveformToAllChannels(therapyVO, TherapyMode.MIDDLE, Waveform.SINE)
            }
        }
    }
}