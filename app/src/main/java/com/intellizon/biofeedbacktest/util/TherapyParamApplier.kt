package com.intellizon.biofeedbacktest.util

import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import com.intellizon.biofeedbacktest.R
import com.intellizon.biofeedbacktest.vo.TherapyVO
import com.intellizon.biofeedbacktest.domain.ChannelDetail
import com.intellizon.biofeedbacktest.domain.ChannelName
import com.intellizon.biofeedbacktest.domain.FrequencyType.Companion.CONSTANT_FREQUENCY
import com.intellizon.biofeedbacktest.domain.FrequencyType.Companion.VARIABLE_FREQUENCY
import com.intellizon.biofeedbacktest.domain.SubMode
import com.intellizon.biofeedbacktest.domain.SubModeInMiddle
import com.intellizon.biofeedbacktest.domain.SubModeInOther
import com.intellizon.biofeedbacktest.domain.TherapyMode
import com.intellizon.biofeedbacktest.domain.TherapyParamMode
import com.intellizon.biofeedbacktest.domain.Waveform

object TherapyChannelApplier {

    fun applyMiddleSubModeToAllChannels(
        therapyVO: TherapyVO,
        @TherapyMode mode: Int,
        @SubModeInMiddle subMode: Int
    ) {
        // 只存 legacy subMode（0x11/0x12）
        therapyVO.modifiedDto = therapyVO.modifiedDto.copy(subMode = subMode)

        for (ch in arrayOf(ChannelName.CHANNEL_A, ChannelName.CHANNEL_B, ChannelName.CHANNEL_C, ChannelName.CHANNEL_D)) {
            val old = therapyVO.getOrCreateChannel(mode, ch)

            val freqType =
                if (subMode == SubModeInMiddle.INTERFERENCE) VARIABLE_FREQUENCY else CONSTANT_FREQUENCY

            therapyVO.putChannel(mode, old.copy(subMode = subMode, frequencyType = freqType))
        }
    }


    fun applyBioSubModeToAllChannels(
        therapyVO: TherapyVO,
        @TherapyMode mode: Int,
        @SubModeInOther subMode: Int
    ) {
        therapyVO.modifiedDto = therapyVO.modifiedDto.copy(subMode = subMode)

        for (ch in arrayOf(ChannelName.CHANNEL_A, ChannelName.CHANNEL_B, ChannelName.CHANNEL_C, ChannelName.CHANNEL_D)) {
            val old = therapyVO.getOrCreateChannel(mode, ch)
            therapyVO.putChannel(mode, old.copy(subMode = subMode))
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


    fun ensureIndex(parent: RadioGroup, child: View, index: Int) {
        if (child.parent != parent) return
        val target = index.coerceIn(0, parent.childCount)
        if (parent.indexOfChild(child) == target) return
        parent.removeView(child)
        parent.addView(child, target)
    }

    fun setMarginStartDp(v: View, dp: Int) {
        val lp = v.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val px = (dp * v.resources.displayMetrics.density + 0.5f).toInt()
        if (lp.marginStart == px) return
        lp.marginStart = px
        v.layoutParams = lp
    }



}