package com.intellizon.biofeedbacktest

import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.intellizon.biofeedbacktest.databinding.ViewLowfreqExpandedOverlayBinding
import com.intellizon.biofeedbacktest.domain.ChannelDetail
import com.intellizon.biofeedbacktest.domain.ChannelName
import com.intellizon.biofeedbacktest.domain.TherapyDetail
import com.intellizon.biofeedbacktest.domain.TherapyMode
import com.intellizon.biofeedbacktest.domain.TherapyMode.Companion.LOW_FREQUENCY
import com.intellizon.biofeedbacktest.domain.Waveform
import com.intellizon.biofeedbacktest.progress.RyCompactSeekbar
import com.intellizon.biofeedbacktest.ui.ChannelControlsBinder
import com.intellizon.biofeedbacktest.ui.SeekbarUiBinder
import com.intellizon.biofeedbacktest.ui.SeekbarUiBinder.initFrequencyMinUi
import com.intellizon.biofeedbacktest.ui.SeekbarUiBinder.initLowHeaderUi
import com.intellizon.biofeedbacktest.ui.SeekbarUiBinder.initStepSeekbarUi
import com.intellizon.biofeedbacktest.vo.ChannelVO
import com.intellizon.biofeedbacktest.vo.TherapyVO
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.Locale

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var activeOverlay: View? = null

    private lateinit var voA: ChannelVO
    private lateinit var voB: ChannelVO
    private lateinit var voC: ChannelVO
    private lateinit var voD: ChannelVO

    private val overlayIds = intArrayOf(
        R.id.lowFreqExpandedOverlay,
        // R.id.middleExpandedOverlay,
        // R.id.otherExpandedOverlay,
    )

    private val therapyVO = TherapyVO(
        therapyId = 0L,
        modifiedDto = TherapyDetail(
            name = "temp",
            mode = TherapyMode.LOW_FREQUENCY,
            subMode = 0,
            tremorFrequency = 0,
            hasChannelA = true,
            hasChannelB = true,
            hasChannelC = true,
            hasChannelD = true,
            isDeleted = false,
            frequencyShift = 0,
            dynamicShifts = 0,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        Timber.d("version name: ${BuildConfig.VERSION_NAME}")

        // 双击 lowFrequency 进入 overlay
        val cardLow = findViewById<View>(R.id.lowFrequency)
        val enterLowDetector =
            GestureDetector(
                this,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        showOverlay(R.id.lowFreqExpandedOverlay)
                        return true
                    }
                },
            )

        cardLow.setOnTouchListener { _, event ->
            enterLowDetector.onTouchEvent(event)
            true
        }

        // Back：优先收起 overlay
        onBackPressedDispatcher.addCallback(this) {
            if (activeOverlay != null) {
                hideOverlay()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        findViewById<View>(R.id.panelRight).visibility = View.VISIBLE
    }

    private fun showOverlay(overlayId: Int) {
        overlayIds.forEach { id -> findViewById<View>(id)?.visibility = View.GONE }

        val overlay = findViewById<View>(overlayId)
        overlay.visibility = View.VISIBLE
        activeOverlay = overlay

        findViewById<View>(R.id.panelRight).visibility = View.INVISIBLE

        if (overlayId == R.id.lowFreqExpandedOverlay) {
            // ✅ 强制当前模式=低频
            therapyVO.modifiedDto = therapyVO.modifiedDto.copy(mode = TherapyMode.LOW_FREQUENCY)

            // ✅ 低频只有一个波形选项的话也可以强制（或用缓存值回填）
            val wf = Waveform.BIPHASIC_SQUARE
            setWaveformForAllChannels(TherapyMode.LOW_FREQUENCY, wf)

        }

        // 1) 准备 VO（从缓存取/默认创建）
        prepareLowVos()

        // 2) 赋值给 overlay binding
        val binding = DataBindingUtil.bind<ViewLowfreqExpandedOverlayBinding>(overlay)
        binding?.voA = voA
        binding?.voB = voB
        binding?.voC = voC
        binding?.voD = voD
        binding?.lifecycleOwner = this


        // 3) 初始化 seekbar UI（formatter/+/-），只做一次
        bindChannelsForOverlayOnce(overlay)

        // 4) 中间按钮 commit + 打印
        bindCenterInfoButtonOnce(overlay)

        // 5) 标题双击关闭
        bindDoubleTapCloseToTitleAOnce(overlay)
    }

    private fun setWaveformForAllChannels(@TherapyMode mode: Int, @Waveform wf: Int) {
        fun update(@ChannelName ch: Int) {
            val old: ChannelDetail = therapyVO.getOrCreateChannel(mode, ch)
            therapyVO.putChannel(mode, old.copy(waveform = wf))
        }
        update(ChannelName.CHANNEL_A)
        update(ChannelName.CHANNEL_B)
        update(ChannelName.CHANNEL_C)
        update(ChannelName.CHANNEL_D)

        // 如果当前 overlay 正在编辑这个 mode，也同步到 voA~voD（可选）
        if (::voA.isInitialized && voA.mode == mode) voA.dto = voA.dto.copy(waveform = wf)
        if (::voB.isInitialized && voB.mode == mode) voB.dto = voB.dto.copy(waveform = wf)
        if (::voC.isInitialized && voC.mode == mode) voC.dto = voC.dto.copy(waveform = wf)
        if (::voD.isInitialized && voD.mode == mode) voD.dto = voD.dto.copy(waveform = wf)
    }

    private fun hideOverlay() {
        activeOverlay?.visibility = View.GONE
        activeOverlay = null
        findViewById<View>(R.id.panelRight).visibility = View.VISIBLE
    }

    private fun bindCenterInfoButtonOnce(overlay: View) {
        if (overlay.getTag(TAG_CENTER_BOUND) == true) return
        overlay.setTag(TAG_CENTER_BOUND, true)

        val btn = overlay.findViewById<TextView>(R.id.btnCenterInfo)
        btn.text = "commit"

        btn.setOnClickListener {
            // commit：把最新 dto 落到 therapyVO
            therapyVO.putChannel(TherapyMode.LOW_FREQUENCY, voA.dto)
            therapyVO.putChannel(TherapyMode.LOW_FREQUENCY, voB.dto)
            therapyVO.putChannel(TherapyMode.LOW_FREQUENCY, voC.dto)
            therapyVO.putChannel(TherapyMode.LOW_FREQUENCY, voD.dto)

            Timber.d(
                """
                ===== COMMIT LOW =====
                therapyDetail=%s
                A=%s
                B=%s
                C=%s
                D=%s
                """.trimIndent(),
                therapyVO.modifiedDto,
                voA.dto,
                voB.dto,
                voC.dto,
                voD.dto,
            )
        }
    }

    private fun bindChannelsForOverlayOnce(overlay: View) {
        if (overlay.getTag(TAG_BOUND) == true) return
        overlay.setTag(TAG_BOUND, true)

        fun initOne(root: View) {
            // delay: 0..20 step 0.5 => progress 0..40
            initStepSeekbarUi(root = root, seekId = R.id.seek_delay, minusId = R.id.iv_minus_delay, plusId = R.id.iv_add_delay, maxProgress = 40, stepValue = 0.5)
            initStepSeekbarUi(root, R.id.seek_rise, R.id.iv_minus_rise, R.id.iv_add_rise, 40, 0.5)
            initStepSeekbarUi(root, R.id.seek_fall, R.id.iv_minus_fall, R.id.iv_add_fall, 40, 0.5)
            initStepSeekbarUi(root, R.id.seek_rest, R.id.iv_minus_rest, R.id.iv_add_rest, 40, 0.5)
            initStepSeekbarUi(root, R.id.seek_sustain, R.id.iv_minus_sustain, R.id.iv_add_sustain, 40, 0.5)
            initStepSeekbarUi(root, R.id.seek_total, R.id.iv_minus_total, R.id.iv_add_total, 99, 1.0, decimals = 0)
            initStepSeekbarUi(root, R.id.seek_width, R.id.iv_minus_width, R.id.iv_add_width, 100000, 10.0, decimals = 0)

            initFrequencyMinUi(root = root, seekId = R.id.seek_freq, minusId = R.id.iv_minus_freq, plusId = R.id.iv_add_freq)

        }

        initOne(overlay.findViewById(R.id.expContentA))
        initOne(overlay.findViewById(R.id.expContentB))
        initOne(overlay.findViewById(R.id.expContentC))
        initOne(overlay.findViewById(R.id.expContentD))
    }

    private fun bindDoubleTapCloseToTitleAOnce(overlay: View) {
        if (overlay.getTag(TAG_TITLE_A_BOUND) == true) return
        overlay.setTag(TAG_TITLE_A_BOUND, true)

        val tvTitleA = overlay.findViewById<View?>(R.id.tvTitleA) ?: run {
            Timber.e("tvTitleA not found in overlay! overlayId=${overlay.id}")
            return
        }

        tvTitleA.isClickable = true
        tvTitleA.isFocusable = true

        val thresholdMs = 500L
        tvTitleA.setOnClickListener {
            val now = android.os.SystemClock.uptimeMillis()
            val last = (tvTitleA.getTag(TAG_LAST_CLICK_MS) as? Long) ?: 0L
            if (now - last in 1..thresholdMs) {
                hideOverlay()
                tvTitleA.setTag(TAG_LAST_CLICK_MS, 0L)
            } else {
                tvTitleA.setTag(TAG_LAST_CLICK_MS, now)
            }
        }
    }


    private fun prepareLowVos() {
        voA =
            ChannelVO(
                mode = TherapyMode.LOW_FREQUENCY,
                channelName = ChannelName.CHANNEL_A,
                dto = therapyVO.getOrCreateChannel(TherapyMode.LOW_FREQUENCY, ChannelName.CHANNEL_A),
            )
        voB =
            ChannelVO(
                mode = TherapyMode.LOW_FREQUENCY,
                channelName = ChannelName.CHANNEL_B,
                dto = therapyVO.getOrCreateChannel(TherapyMode.LOW_FREQUENCY, ChannelName.CHANNEL_B),
            )
        voC =
            ChannelVO(
                mode = TherapyMode.LOW_FREQUENCY,
                channelName = ChannelName.CHANNEL_C,
                dto = therapyVO.getOrCreateChannel(TherapyMode.LOW_FREQUENCY, ChannelName.CHANNEL_C),
            )
        voD =
            ChannelVO(
                mode = TherapyMode.LOW_FREQUENCY,
                channelName = ChannelName.CHANNEL_D,
                dto = therapyVO.getOrCreateChannel(TherapyMode.LOW_FREQUENCY, ChannelName.CHANNEL_D),
            )
    }

    private companion object {
        private const val TAG_BOUND: Int = 0xCC010001.toInt()
        private const val TAG_TITLE_A_BOUND: Int = 0xCC010004.toInt()
        private const val TAG_LAST_CLICK_MS: Int = 0xCC010005.toInt()
        private const val TAG_CENTER_BOUND: Int = 0xCC010006.toInt()
    }
}