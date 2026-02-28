package com.intellizon.biofeedbacktest

import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
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
import com.intellizon.biofeedbacktest.domain.TherapyParamMode
import com.intellizon.biofeedbacktest.domain.Waveform
import com.intellizon.biofeedbacktest.ui.SeekbarUiBinder.initFrequencyMinUi
import com.intellizon.biofeedbacktest.ui.SeekbarUiBinder.initStepSeekbarUi
import com.intellizon.biofeedbacktest.util.TherapyChannelApplier
import com.intellizon.biofeedbacktest.util.TherapyChannelApplier.ensureIndex
import com.intellizon.biofeedbacktest.util.TherapyChannelApplier.setMarginStartDp
import com.intellizon.biofeedbacktest.vo.ChannelVO
import com.intellizon.biofeedbacktest.vo.TherapyVO
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber


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

        bindMidCarrierWaveformByStimTypeOnce()

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
                TherapyChannelApplier.applyWaveformToAllChannels(
                    therapyVO = therapyVO,
                    mode = TherapyMode.LOW_FREQUENCY,
                    wf = Waveform.BIPHASIC_SQUARE
                )

                prepareVos(TherapyMode.LOW_FREQUENCY)

                val binding = DataBindingUtil.bind<ViewLowfreqExpandedOverlayBinding>(overlay)
                binding?.voA = voA
                binding?.voB = voB
                binding?.voC = voC
                binding?.voD = voD
                binding?.lifecycleOwner = this

                bindChannelsForOverlayOnce(overlay)
                bindCenterInfoButtonOnce(overlay, TherapyMode.LOW_FREQUENCY)
                bindDoubleTapCloseToTitlesOnce(overlay)
            }

            R.id.middleExpandedOverlay -> {
                therapyVO.modifiedDto = therapyVO.modifiedDto.copy(mode = TherapyMode.MIDDLE)
                val paramMode  = readMiddleParamModeFromCard()
                val waveform = readMiddleWaveformFromCard()
                TherapyChannelApplier.applyParamModeToAllChannels(therapyVO, TherapyMode.MIDDLE, paramMode)
                TherapyChannelApplier.applyWaveformToAllChannels(therapyVO, TherapyMode.MIDDLE, waveform)


                prepareVos(TherapyMode.MIDDLE)

                val binding = DataBindingUtil.bind<ViewMidfreqExpandedOverlayBinding>(overlay)
                binding?.voA = voA
                binding?.voB = voB
                binding?.voC = voC
                binding?.voD = voD
                binding?.lifecycleOwner = this

                bindChannelsForOverlayOnce(overlay) // 你刚改了缺控件会跳过，OK
                bindCenterInfoButtonOnce(overlay, TherapyMode.MIDDLE)
                bindDoubleTapCloseToTitlesOnce(overlay) // 你中频布局里有 tvTitleA 才行
            }
        }
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

    private fun bindDoubleTapCloseToTitlesOnce(overlay: View) {
        // ✅ 一个 overlay 只绑定一次
        val tagKey = TAG_TITLE_A_BOUND
        if (overlay.getTag(tagKey) == true) return
        overlay.setTag(tagKey, true)

        val thresholdMs = 500L
        val titleIds = intArrayOf(R.id.tvTitleA, R.id.tvTitleB, R.id.tvTitleC, R.id.tvTitleD)

        titleIds.forEach { id ->
            val tv = overlay.findViewById<View?>(id)
            if (tv == null) {
                Timber.w("title not found: id=%s overlayId=%s", id, overlay.id)
                return@forEach
            }
            tv.isClickable = true
            tv.isFocusable = true

            tv.setOnClickListener {
                val now = android.os.SystemClock.uptimeMillis()
                val last = (tv.getTag(TAG_LAST_CLICK_MS) as? Long) ?: 0L
                if (now - last in 1..thresholdMs) {
                    hideOverlay()
                    tv.setTag(TAG_LAST_CLICK_MS, 0L)
                } else {
                    tv.setTag(TAG_LAST_CLICK_MS, now)
                }
            }
        }
    }

    //中频不同刺激类型对应 不同载波波形
    fun bindMidCarrierWaveformByStimTypeOnce() {
        val cardMid = findViewById<View>(R.id.midFrequency)

        val tagKey = 0xCC030101.toInt()
        if (cardMid.getTag(tagKey) == true) return
        cardMid.setTag(tagKey, true)

        val rgStim = cardMid.findViewById<RadioGroup?>(R.id.rg_stim_sub_mode_mid) ?: return
        val rbInterference = cardMid.findViewById<RadioButton?>(R.id.rb_stim_mid_interference)
        val rbModulated = cardMid.findViewById<RadioButton?>(R.id.rb_stim_mid_modulated)

        val rgCarrier = cardMid.findViewById<RadioGroup?>(R.id.rg_mid_waveform) ?: return
        val rbCarrierBi = cardMid.findViewById<RadioButton?>(R.id.rb_mid_carrier_biphasic_square)
        val rbCarrierSine = cardMid.findViewById<RadioButton?>(R.id.rb_mid_carrier_sine)

        //调整位置
        fun applyInterferenceCarrierUi() {
            rbCarrierBi?.visibility = View.GONE
            rbCarrierSine?.visibility = View.VISIBLE
            rbCarrierSine?.let { setMarginStartDp(it, 0) }
            if (rbCarrierSine != null) ensureIndex(rgCarrier, rbCarrierSine, 0)
            rbCarrierSine?.let { rgCarrier.check(it.id) }
        }
        //调整位置
        fun applyModulatedCarrierUi() {
            rbCarrierBi?.visibility = View.VISIBLE
            rbCarrierSine?.visibility = View.VISIBLE
            if (rbCarrierBi != null) {
                setMarginStartDp(rbCarrierBi, 0)
                ensureIndex(rgCarrier, rbCarrierBi, 0)
            }
            if (rbCarrierSine != null) {
                setMarginStartDp(rbCarrierSine, 10)
                ensureIndex(rgCarrier, rbCarrierSine, 1)
            }
            rbCarrierBi?.let { rgCarrier.check(it.id) }
        }

        // 首帧：按当前刺激类型应用一次（默认干扰电的话就会强制正弦）
        val isInterference = rbInterference?.isChecked ?: true
        if (isInterference) applyInterferenceCarrierUi() else applyModulatedCarrierUi()

        // 切换刺激类型时动态更新
        rgStim.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                rbInterference?.id -> applyInterferenceCarrierUi()
                rbModulated?.id -> applyModulatedCarrierUi()
                else -> applyInterferenceCarrierUi()
            }
        }
    }


    //读取中频刺激类型
    private fun readMiddleParamModeFromCard(): Int {
        val cardMid = findViewById<View>(R.id.midFrequency)
        val rbInterference = cardMid.findViewById<RadioButton>(R.id.rb_stim_mid_interference)
        // 默认干扰电
        return if (rbInterference.isChecked) {
            TherapyParamMode.MIDDLE_INTERFERENCE   // 0x41
        } else {
            TherapyParamMode.MIDDLE_MODULATED      // 0x43
        }
    }

    @Waveform
    private fun readMiddleWaveformFromCard():  Int {
        val cardMid = findViewById<View>(R.id.midFrequency)
        val rgCarrier = cardMid.findViewById<RadioGroup?>(R.id.rg_mid_waveform)

        val checkedId = rgCarrier?.checkedRadioButtonId ?: View.NO_ID
        return when (checkedId) {
            R.id.rb_mid_carrier_biphasic_square -> Waveform.BIPHASIC_SQUARE
            R.id.rb_mid_carrier_sine -> Waveform.SINE
            else -> Waveform.SINE // ✅ 默认（干扰电时就是它）
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