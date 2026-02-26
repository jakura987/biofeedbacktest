package com.intellizon.biofeedbacktest.ui

import android.view.View
import android.widget.SeekBar
import com.intellizon.biofeedbacktest.progress.RyCompactSeekbar
import com.intellizon.biofeedbacktest.R
import kotlin.math.roundToInt

object ChannelControlsBinder {

    /**
     * 绑定：延时时间 delayTime（0..20s，步长0.5s）
     *
     * @param root 通道面板的根View（例如 expContentA / expContentB / 中频面板root）
     * @param initialSec 初始值（秒），null 表示不回填
     * @param onChanged 拖动/点击 +/- 后回调（秒，0.0..20.0，步长0.5）
     */
    fun bindDelayTime(
        root: View,
        initialSec: Double? = null,
        onChanged: (Double) -> Unit,
    ) {
        val seek = root.findViewById<RyCompactSeekbar>(R.id.seek_delay)
        val btnMinus = root.findViewById<View>(R.id.iv_minus_delay)
        val btnPlus = root.findViewById<View>(R.id.iv_add_delay)

        // 0..20s step 0.5 => progress 0..40
        seek.max = 40

        // thumb 显示：0.0/0.5/1.0...
        seek.setFormatter { p ->
            val sec = p * 0.5
            String.format("%.1f", sec)
        }

        fun progressToSec(p: Int): Double = p.coerceIn(0, seek.max) * 0.5

        fun secToProgress(sec: Double): Int {
            // 四舍五入到最近的 0.5
            val p = (sec / 0.5).roundToInt()
            return p.coerceIn(0, seek.max)
        }

        fun setProgress(p: Int, fromUser: Boolean) {
            val np = p.coerceIn(0, seek.max)
            if (seek.progress != np) seek.progress = np
            onChanged(progressToSec(np))
            // RyCompactSeekbar 的 thumb 需要刷新一次显示
            if (!fromUser) seek.notifyRefresh()
        }

        // 回填初始值
        initialSec?.let { setProgress(secToProgress(it), fromUser = false) } ?: seek.notifyRefresh()

        // +/-：每次 1 progress = 0.5s
        btnMinus.setOnClickListener { setProgress(seek.progress - 1, fromUser = false) }
        btnPlus.setOnClickListener { setProgress(seek.progress + 1, fromUser = false) }

        // 拖动
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    onChanged(progressToSec(progress))
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                // 拖完也刷新一下，确保 thumb 文本跟上
                seek.notifyRefresh()
            }
        })
    }
}