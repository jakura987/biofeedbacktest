package com.intellizon.biofeedbacktest.ui

import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import com.intellizon.biofeedbacktest.R
import com.intellizon.biofeedbacktest.progress.RyCompactSeekbar
import java.util.Locale

object SeekbarUiBinder {

    /**
     * 通用：seek + +/- 初始化（线性步长）
     *
     * @param maxProgress SeekBar max（例如 delay 0..20 step0.5 => 40）
     * @param stepValue progress=1 对应的真实步长（0.5秒/10us/1Hz 等）
     * @param decimals  显示小数位（delay=1；width=0）
     */
    fun initStepSeekbarUi(
        root: View,
        seekId: Int,
        minusId: Int,
        plusId: Int,
        maxProgress: Int,
        stepValue: Double,
        decimals: Int = 1,
    ) {
        val seek = root.findViewById<View?>(seekId) as? RyCompactSeekbar ?: return
        // ✅ minus/plus 找不到就忽略，不再 return
        val minus = root.findViewById<View?>(minusId)
        val plus = root.findViewById<View?>(plusId)

        seek.max = maxProgress
        seek.setFormatter { p ->
            val v = p * stepValue
            String.format(Locale.US, "%.${decimals}f", v)
        }
        seek.notifyRefresh()
        //seek.post { seek.notifyRefresh() }

        minus.setOnClickListener {
            seek.progress = (seek.progress - 1).coerceAtLeast(0)
            seek.notifyRefresh()
        }
        plus.setOnClickListener {
            seek.progress = (seek.progress + 1).coerceAtMost(seek.max)
            seek.notifyRefresh()
        }
    }

    /**
     * 频率（脉冲频率）分段步进 UI：
     * 0.1..0.9 step 0.1  +  1..1000 step 1
     *
     * progress:
     * 0..8   => 0.1..0.9
     * 9..1008=> 1..1000
     */
    fun initFrequencyMinUi(
        root: View,
        seekId: Int,
        minusId: Int,
        plusId: Int,
    ) {
        val seek = root.findViewById<View?>(seekId) as? RyCompactSeekbar ?: return
        // ✅ 缺按钮不致命：允许没有 +/- 也能拖动
        val minus = root.findViewById<View?>(minusId)
        val plus = root.findViewById<View?>(plusId)

        seek.max = 1008

        seek.setFormatter { p ->
            val f = if (p <= 8) 0.1 + p * 0.1 else (p - 8).toDouble()
            if (f < 1.0) String.format(Locale.US, "%.1f", f) else String.format(Locale.US, "%.0f", f)
        }
        seek.notifyRefresh()

        minus.setOnClickListener {
            seek.progress = (seek.progress - 1).coerceAtLeast(0)
            seek.notifyRefresh()
        }
        plus.setOnClickListener {
            seek.progress = (seek.progress + 1).coerceAtMost(seek.max)
            seek.notifyRefresh()
        }
    }

    fun initLowHeaderUi(
        root: View,
        // 进入时默认值（可选）
        defaultMode: Int,
        defaultWaveform: Int,
        // 用户操作回调：把值抛给外面
        onModeChanged: (Int) -> Unit,
        onWaveformChanged: (Int) -> Unit,
    ) {
        val rgMode = root.findViewById<RadioGroup?>(R.id.rg_stim_mode) ?: return
        val rgWave = root.findViewById<RadioGroup?>(R.id.rg_waveform) ?: return

        val rbLow = root.findViewById<RadioButton?>(R.id.rb_stim_low_frequency)
        val rbBiphasic = root.findViewById<RadioButton?>(R.id.rb_biphasic_square)

        // 1) 默认选中（你现在低频只有一个选项，就直接选中）
        rbLow?.isChecked = true
        rbBiphasic?.isChecked = true

        // 2) 把默认值同步出去（可选，但推荐）
        onModeChanged(defaultMode)
        onWaveformChanged(defaultWaveform)

        // 3) 监听选择变化
        rgMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_stim_low_frequency -> onModeChanged(defaultMode) // 低频
            }
        }

        rgWave.setOnCheckedChangeListener { _, checkedId ->
            val wf = when (checkedId) {
                R.id.rb_biphasic_square -> defaultWaveform
                else -> defaultWaveform
            }
            onWaveformChanged(wf)
        }
    }

}