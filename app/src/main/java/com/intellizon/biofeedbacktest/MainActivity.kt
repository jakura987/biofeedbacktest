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
import com.intellizon.biofeedbacktest.databinding.ViewMidfreqExpandedOverlayBinding
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
        R.id.middleExpandedOverlay,
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


        // 双击 midFrequency 进入 overlay
        val cardMid = findViewById<View>(R.id.midFrequency)
        val enterMidDetector =
            GestureDetector(
                this,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        showOverlay(R.id.middleExpandedOverlay)
                        return true
                    }
                },
            )

        cardMid.setOnTouchListener { _, event ->
            enterMidDetector.onTouchEvent(event)
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

        Timber.d("showOverlay overlayId=%s", resources.getResourceEntryName(overlayId))

        val overlay = findViewById<View>(overlayId)
        overlay.visibility = View.VISIBLE
        activeOverlay = overlay
        findViewById<View>(R.id.panelRight).visibility = View.INVISIBLE

        when (overlayId) {
            R.id.lowFreqExpandedOverlay -> {
                therapyVO.modifiedDto = therapyVO.modifiedDto.copy(mode = TherapyMode.LOW_FREQUENCY)
                setWaveformForAllChannels(TherapyMode.LOW_FREQUENCY, Waveform.BIPHASIC_SQUARE)

                prepareVos(TherapyMode.LOW_FREQUENCY)

                val binding = DataBindingUtil.bind<ViewLowfreqExpandedOverlayBinding>(overlay)
                binding?.voA = voA
                binding?.voB = voB
                binding?.voC = voC
                binding?.voD = voD
                binding?.lifecycleOwner = this

                bindChannelsForOverlayOnce(overlay)
                bindCenterInfoButtonOnce(overlay, TherapyMode.LOW_FREQUENCY)
                bindDoubleTapCloseToTitleAOnce(overlay)
            }

            R.id.middleExpandedOverlay -> {
                therapyVO.modifiedDto = therapyVO.modifiedDto.copy(mode = TherapyMode.MIDDLE)

                prepareVos(TherapyMode.MIDDLE)

                val binding = DataBindingUtil.bind<ViewMidfreqExpandedOverlayBinding>(overlay)
                binding?.voA = voA
                binding?.voB = voB
                binding?.voC = voC
                binding?.voD = voD
                binding?.lifecycleOwner = this

                bindChannelsForOverlayOnce(overlay) // 你刚改了缺控件会跳过，OK
                bindCenterInfoButtonOnce(overlay, TherapyMode.MIDDLE)
                bindDoubleTapCloseToTitleAOnce(overlay) // 你中频布局里有 tvTitleA 才行
            }
        }
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

    private fun bindCenterInfoButtonOnce(overlay: View, @TherapyMode mode: Int) {
        // ✅ 让不同 overlay / 不同 mode 都能各自绑定一次（避免 tag 冲突）
        val tagKey = TAG_CENTER_BOUND xor mode
        if (overlay.getTag(tagKey) == true) return
        overlay.setTag(tagKey, true)

        val btn = overlay.findViewById<TextView>(R.id.btnCenterInfo)
        btn.text = "commit"

        btn.setOnClickListener {
            // ✅ commit：把最新 dto 落到指定 mode
            therapyVO.putChannel(mode, voA.dto)
            therapyVO.putChannel(mode, voB.dto)
            therapyVO.putChannel(mode, voC.dto)
            therapyVO.putChannel(mode, voD.dto)

            Timber.d(
                """
            ===== COMMIT mode=%d =====
            therapyDetail=%s
            A=%s
            B=%s
            C=%s
            D=%s
            """.trimIndent(),
                mode,
                therapyVO.modifiedDto,
                voA.dto, voB.dto, voC.dto, voD.dto
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



    private fun prepareVos(@TherapyMode mode: Int) {
        voA = ChannelVO(
            mode = mode,
            channelName = ChannelName.CHANNEL_A,
            dto = therapyVO.getOrCreateChannel(mode, ChannelName.CHANNEL_A),
        )
        voB = ChannelVO(
            mode = mode,
            channelName = ChannelName.CHANNEL_B,
            dto = therapyVO.getOrCreateChannel(mode, ChannelName.CHANNEL_B),
        )
        voC = ChannelVO(
            mode = mode,
            channelName = ChannelName.CHANNEL_C,
            dto = therapyVO.getOrCreateChannel(mode, ChannelName.CHANNEL_C),
        )
        voD = ChannelVO(
            mode = mode,
            channelName = ChannelName.CHANNEL_D,
            dto = therapyVO.getOrCreateChannel(mode, ChannelName.CHANNEL_D),
        )
    }

    private companion object {
        private const val TAG_BOUND: Int = 0xCC010001.toInt()
        private const val TAG_TITLE_A_BOUND: Int = 0xCC010004.toInt()
        private const val TAG_LAST_CLICK_MS: Int = 0xCC010005.toInt()
        private const val TAG_CENTER_BOUND: Int = 0xCC010006.toInt()
    }

    private fun forceCheckLowHeader(root: View) {
        root.findViewById<View?>(R.id.rb_stim_low_frequency)?.let { (it as? android.widget.RadioButton)?.isChecked = true }
        root.findViewById<View?>(R.id.rb_biphasic_square)?.let { (it as? android.widget.RadioButton)?.isChecked = true }
    }
}