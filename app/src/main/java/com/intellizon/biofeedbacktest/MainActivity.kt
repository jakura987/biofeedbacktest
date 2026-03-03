package com.intellizon.biofeedbacktest

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.intellizon.biofeedbacktest.databinding.ActivityMainBinding
import com.intellizon.biofeedbacktest.databinding.ViewBiofreqExpandedOverlayBinding
import com.intellizon.biofeedbacktest.databinding.ViewLowfreqExpandedOverlayBinding
import com.intellizon.biofeedbacktest.databinding.ViewMidfreqExpandedOverlayBinding
import com.intellizon.biofeedbacktest.domain.ChannelDetail
import com.intellizon.biofeedbacktest.domain.ChannelName
import com.intellizon.biofeedbacktest.domain.SubModeInMiddle
import com.intellizon.biofeedbacktest.domain.SubModeInOther
import com.intellizon.biofeedbacktest.domain.TcpPacket
import com.intellizon.biofeedbacktest.domain.TherapyDetail
import com.intellizon.biofeedbacktest.domain.TherapyMode
import com.intellizon.biofeedbacktest.domain.TherapyParamMode
import com.intellizon.biofeedbacktest.domain.Waveform
import com.intellizon.biofeedbacktest.encode.TherapyCoderV1
import com.intellizon.biofeedbacktest.ui.SeekbarUiBinder.initFrequencyMinUi
import com.intellizon.biofeedbacktest.ui.SeekbarUiBinder.initStepSeekbarUi
import com.intellizon.biofeedbacktest.util.TherapyChannelApplier
import com.intellizon.biofeedbacktest.vo.ChannelVO
import com.intellizon.biofeedbacktest.vo.TherapyVO
import com.intellizon.biofeedbacktest.wifi.TherapyFrameBuilderV1
import com.intellizon.biofeedbacktest.wifi.connect.LocalIpHelper
import com.intellizon.biofeedbacktest.wifi.manager.WifiTcpServerManager
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var activeOverlay: View? = null

    private lateinit var binding: ActivityMainBinding

    @Inject lateinit var wifiTcpServerManager: WifiTcpServerManager

    private val tcpPort = 8883

    private val disposables = CompositeDisposable()


    private lateinit var voA: ChannelVO
    private lateinit var voB: ChannelVO
    private lateinit var voC: ChannelVO
    private lateinit var voD: ChannelVO

    private val overlayIds = intArrayOf(
        R.id.lowFreqExpandedOverlay,
        R.id.middleExpandedOverlay,
        R.id.bioExpandedOverlay,
    )

    private val therapyVO = TherapyVO(
        therapyId = 0L,
        modifiedDto = TherapyDetail(
            name = "swets",
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

    @SuppressLint("AutoDispose")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 启动 TCP server
        disposables.add(
            wifiTcpServerManager.startIfNeeded(tcpPort)
                .subscribe(
                    { /* ignore */ },
                    { Timber.e(it, "start tcp failed") }
                )
        )


        disposables.add(
            wifiTcpServerManager.incoming()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { p ->
                        Timber.i("TCP <- peer=%s len=%d", p.peer, p.byteString.size)
                        onTcpPacket(p)
                        // 这里就能解析 ID，并绑定到 peer
                        // handlePacket(p.peer, p.bytes)
                    },
                    { Timber.e(it, "incoming error") }
                )
        )



        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        Timber.d("version name: ${BuildConfig.VERSION_NAME}")

        updateWifiPanel()


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

        // 双击 bioFrequency 进入 overlay
        val cardBio = findViewById<View>(R.id.bioFrequency)
        val enterBioDetector =
            GestureDetector(
                this,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        showOverlay(R.id.bioExpandedOverlay)
                        return true
                    }
                },
            )
        cardBio.setOnTouchListener { _, event ->
            enterBioDetector.onTouchEvent(event)
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
                val subMode = readMiddleSubModeFromCard()
                val waveform = readMiddleWaveformFromCard()
                val modulationWaveform = readMiddleModulationWaveformFromCard()
                TherapyChannelApplier.applyMiddleSubModeToAllChannels(therapyVO, TherapyMode.MIDDLE, subMode)
                TherapyChannelApplier.applyWaveformToAllChannels(therapyVO, TherapyMode.MIDDLE, waveform)
                TherapyChannelApplier.applyModulationWaveformToAllChannels(therapyVO, TherapyMode.MIDDLE, modulationWaveform)

                prepareVos(TherapyMode.MIDDLE)

                val binding = DataBindingUtil.bind<ViewMidfreqExpandedOverlayBinding>(overlay)
                binding?.voA = voA
                binding?.voB = voB
                binding?.voC = voC
                binding?.voD = voD
                binding?.lifecycleOwner = this

                //中频双击不同布局
                val isInterference = (subMode == SubModeInMiddle.INTERFERENCE)
                applyMiddleStimTypeUi(overlay, isInterference)

                bindChannelsForOverlayOnce(overlay)
                bindCenterInfoButtonOnce(overlay, TherapyMode.MIDDLE)
                bindDoubleTapCloseToTitlesOnce(overlay) // 中频布局里有 tvTitleA 才行
            }


            R.id.bioExpandedOverlay -> {
                therapyVO.modifiedDto = therapyVO.modifiedDto.copy(mode = TherapyMode.BIOFEEDBACK)
                TherapyChannelApplier.applyWaveformToAllChannels(
                    therapyVO = therapyVO,
                    mode = TherapyMode.BIOFEEDBACK,
                    wf = Waveform.BIPHASIC_SQUARE
                )
                val subMode = readBioParamModeFromCard()
                TherapyChannelApplier.applyBioSubModeToAllChannels(therapyVO, TherapyMode.BIOFEEDBACK, subMode)

                prepareVos(TherapyMode.BIOFEEDBACK)

                val binding = DataBindingUtil.bind<ViewBiofreqExpandedOverlayBinding>(overlay)
                binding?.voA = voA
                binding?.voB = voB
                binding?.voC = voC
                binding?.voD = voD
                binding?.lifecycleOwner = this

                bindChannelsForOverlayOnce(overlay)
                bindCenterInfoButtonOnce(overlay, TherapyMode.LOW_FREQUENCY)
                bindDoubleTapCloseToTitlesOnce(overlay)
            }
        }
    }

    //中频双击不同布局
    private fun applyMiddleStimTypeUi(overlay: View, isInterference: Boolean) {
        toggleMidUi(
            channelRoot = overlay.findViewById(R.id.expContentA),
            isInterference = isInterference,
            interId = R.id.incMidInterferenceA,
            modId = R.id.incMidModulationA,
        )
        toggleMidUi(
            channelRoot = overlay.findViewById(R.id.expContentB),
            isInterference = isInterference,
            interId = R.id.incMidInterferenceB,
            modId = R.id.incMidModulationB,
        )
        toggleMidUi(
            channelRoot = overlay.findViewById(R.id.expContentC),
            isInterference = isInterference,
            interId = R.id.incMidInterferenceC,
            modId = R.id.incMidModulationC,
        )
        toggleMidUi(
            channelRoot = overlay.findViewById(R.id.expContentD),
            isInterference = isInterference,
            interId = R.id.incMidInterferenceD,
            modId = R.id.incMidModulationD,
        )
    }

    private fun toggleMidUi(channelRoot: View, isInterference: Boolean, @IdRes interId: Int, @IdRes modId: Int) {
        val incInterference = channelRoot.findViewById<View?>(interId)
        val incModulation = channelRoot.findViewById<View?>(modId)

        if (incInterference == null || incModulation == null) {
            Timber.w("toggleMidUi missing include: inter=%s mod=%s", interId, modId)
            return
        }

        incInterference.visibility = if (isInterference) View.VISIBLE else View.GONE
        incModulation.visibility = if (isInterference) View.GONE else View.VISIBLE
    }


    private fun hideOverlay() {
        activeOverlay?.visibility = View.GONE
        activeOverlay = null
        findViewById<View>(R.id.panelRight).visibility = View.VISIBLE
    }

    @SuppressLint("AutoDispose")
    private fun bindCenterInfoButtonOnce(overlay: View, @TherapyMode mode: Int) {
        // ✅ 让不同 overlay / 不同 mode 都能各自绑定一次（避免 tag 冲突）
        val tagKey = TAG_CENTER_BOUND xor mode
        if (overlay.getTag(tagKey) == true) return
        overlay.setTag(tagKey, true)

        val btn = overlay.findViewById<TextView>(R.id.btnCenterInfo)
        btn.text = "commit"

        btn.setOnClickListener {

            // ✅ 0) 先判断有没有 TCP 客户端
            if (wifiTcpServerManager.connectedCount() <= 0) {
                Toast.makeText(overlay.context, "没有TCP客户端连接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val peer = selectedPeer
            if (peer.isNullOrBlank()) {
                Toast.makeText(overlay.context, "请选择一个设备", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            // 1) 读勾选状态（注意：必须从 overlay 找，因为每个 overlay 里都有一套）
            val hasA = overlay.findViewById<CheckBox>(R.id.cbSelectA)?.isChecked == true
            val hasB = overlay.findViewById<CheckBox>(R.id.cbSelectB)?.isChecked == true
            val hasC = overlay.findViewById<CheckBox>(R.id.cbSelectC)?.isChecked == true
            val hasD = overlay.findViewById<CheckBox>(R.id.cbSelectD)?.isChecked == true

            if (!(hasA || hasB || hasC || hasD)) {
                Toast.makeText(overlay.context, "请至少选择一个通道", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            therapyVO.modifiedDto = therapyVO.modifiedDto.copy(
                hasChannelA = hasA,
                hasChannelB = hasB,
                hasChannelC = hasC,
                hasChannelD = hasD,
            )

            // ✅ commit：把最新 dto 落到指定 mode
            therapyVO.putChannel(mode, voA.dto)
            therapyVO.putChannel(mode, voB.dto)
            therapyVO.putChannel(mode, voC.dto)
            therapyVO.putChannel(mode, voD.dto)

            Timber.d("ABCD dto before encode: restA=%s, restB=%s, restC=%s, restD=%s",
                voA.dto.restTime,
                voB.dto.restTime,
                voC.dto.restTime,
                voD.dto.restTime,
            )

            //转成协议hex
            // 3) 组帧并打印 hex（按 SDU 校验）
            val builder = TherapyFrameBuilderV1()


            val channelsByName = buildMap<Int, ChannelDetail> {
                if (hasA) put(1, voA.dto)
                if (hasB) put(2, voB.dto)
                if (hasC) put(3, voC.dto)
                if (hasD) put(4, voD.dto)
            }

            val frame = builder.buildTherapyFrame(
                schemeNo = 0x01,
                therapyDetail = therapyVO.modifiedDto,
                channelsByName = channelsByName,
                therapyCoder = TherapyCoderV1.INSTANCE
            )

            Timber.d("===== COMMIT mode 333 =%d =====\ntherapyDetail=%s\nHEX(len=%d): %s",
                mode, therapyVO.modifiedDto, frame.size, frame.toHex()
            )

            //发送至选中设备
            disposables.add(
                wifiTcpServerManager.sendToPeer(peer, frame)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { ok -> Toast.makeText(overlay.context, if (ok) "已发送到 $peer" else "发送失败", Toast.LENGTH_SHORT).show() },
                        { e -> Timber.e(e, "sendToPeer failed") }
                    )
            )

            // 发送全部
//            disposables.add(
//                wifiTcpServerManager.send(frame)
//                    .observeOn(AndroidSchedulers.mainThread())
//                    .subscribe(
//                        { ok ->
//                            Timber.i("TCP -> sent ok=%s len=%d", ok, frame.size)
//                            Toast.makeText(overlay.context, if (ok) "已发送" else "发送失败：无连接", Toast.LENGTH_SHORT).show()
//                        },
//                        { e ->
//                            Timber.e(e, "TCP send failed")
//                            Toast.makeText(overlay.context, "发送异常: ${e.message}", Toast.LENGTH_SHORT).show()
//                        }
//                    )
//            )
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(" ") { b -> "%02X".format(b.toInt() and 0xFF) }


    private fun bindChannelsForOverlayOnce(overlay: View) {
        if (overlay.getTag(TAG_BOUND) == true) return
        overlay.setTag(TAG_BOUND, true)

        fun initOne(root: View) {
            initStepSeekbarUi(root, R.id.seek_delay, R.id.iv_minus_delay, R.id.iv_add_delay, 200, 0.1)
            initStepSeekbarUi(root, R.id.seek_rise, R.id.iv_minus_rise, R.id.iv_add_rise, 40, 0.5)
            initStepSeekbarUi(root, R.id.seek_fall, R.id.iv_minus_fall, R.id.iv_add_fall, 40, 0.5)
            initStepSeekbarUi(root, R.id.seek_rest, R.id.iv_minus_rest, R.id.iv_add_rest, 40, 0.5)
            initStepSeekbarUi(root, R.id.seek_sustain, R.id.iv_minus_sustain, R.id.iv_add_sustain, 40, 0.5)
            initStepSeekbarUi(root, R.id.seek_total, R.id.iv_minus_total, R.id.iv_add_total, 99, 1.0, decimals = 0)

            initStepSeekbarUi(root, R.id.seek_width, R.id.iv_minus_width, R.id.iv_add_width, 100000, 10.0, decimals = 0)
            initStepSeekbarUi(root, R.id.seek_intensity, R.id.iv_minus_intensity, R.id.iv_add_intensity, 4, 25.0, decimals = 0)

            initStepSeekbarUi(root, R.id.seek_tiload, R.id.iv_minus_tiload, R.id.iv_add_tiload, 20, 0.5, decimals = 1)

            initStepSeekbarUi(root, R.id.seek_modulationFreq, R.id.iv_minus_modulationFreq, R.id.iv_add_modulationFreq, 150, 1.0, decimals = 0)

            // 动态周期 0..10
            initStepSeekbarUi(root, R.id.seek_dynamicShifts, R.id.iv_minus_dynamicShifts, R.id.iv_add_dynamicShifts, 10, 1.0, decimals = 0)

            // 差频周期 0..30
            initStepSeekbarUi(root, R.id.seek_frequencyShift, R.id.iv_minus_frequencyShift, R.id.iv_add_frequencyShift, 30, 1.0, decimals = 0)

            // 差频频率 max 0..200
            initStepSeekbarUi(root, R.id.seek_frequencyMax, R.id.iv_minus_frequencyMax, R.id.iv_add_frequencyMax, 200, 1.0, decimals = 0)


            // 收缩
            initStepSeekbarUi(root, R.id.seek_contraction, R.id.iv_minus_contraction, R.id.iv_add_contraction, 20, 1.0, decimals = 0)


            // 刺激
            initStepSeekbarUi(root, R.id.seek_stimulation, R.id.iv_minus_stimulation, R.id.iv_add_stimulation, 20, 1.0, decimals = 0)


            // 差频频率 min（你的特殊规则）
            initFrequencyMinUi(root, R.id.seek_freq, R.id.iv_minus_freq, R.id.iv_add_freq)


        }

        /**
         * ✅ 通用初始化：
         * - 中频：content 里能找到 inter/mod include -> 两套都 init
         * - 低频/BIO：找不到 inter/mod -> 直接 init content
         */
        fun initChannel(content: View?, interId: Int, modId: Int) {
            if (content == null) return

            val inter = content.findViewById<View?>(interId)
            val mod = content.findViewById<View?>(modId)

            if (inter != null || mod != null) {
                inter?.let { initOne(it) }
                mod?.let { initOne(it) }
            } else {
                initOne(content)
            }
        }

        initChannel(overlay.findViewById<View?>(R.id.expContentA), R.id.incMidInterferenceA, R.id.incMidModulationA)
        initChannel(overlay.findViewById<View?>(R.id.expContentB), R.id.incMidInterferenceB, R.id.incMidModulationB)
        initChannel(overlay.findViewById<View?>(R.id.expContentC), R.id.incMidInterferenceC, R.id.incMidModulationC)
        initChannel(overlay.findViewById<View?>(R.id.expContentD), R.id.incMidInterferenceD, R.id.incMidModulationD)
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


    //读取中频刺激类型
    @TherapyParamMode
    @SubModeInMiddle
    private fun readMiddleSubModeFromCard(): Int {
        val cardMid = findViewById<View>(R.id.midFrequency)
        val rbInterference = cardMid.findViewById<RadioButton>(R.id.rb_stim_mid_interference)
        return if (rbInterference.isChecked) {
            SubModeInMiddle.INTERFERENCE // 0x11
        } else {
            SubModeInMiddle.MODULATED    // 0x12
        }
    }

    //读取中频载波波形
    @Waveform
    private fun readMiddleWaveformFromCard(): Int {
        val cardMid = findViewById<View>(R.id.midFrequency)
        val rgCarrier = cardMid.findViewById<RadioGroup?>(R.id.rg_mid_waveform)

        val checkedId = rgCarrier?.checkedRadioButtonId ?: View.NO_ID
        return when (checkedId) {
            R.id.rb_mid_carrier_biphasic_square -> Waveform.BIPHASIC_SQUARE
            R.id.rb_mid_carrier_sine -> Waveform.SINE
            else -> Waveform.SINE // ✅ 默认（干扰电时就是它）
        }
    }

    //读取中频调制波形
    @Waveform
    private fun readMiddleModulationWaveformFromCard(): Int {
        val cardMid = findViewById<View>(R.id.midFrequency)
        val rg = cardMid.findViewById<RadioGroup?>(R.id.rg_mid_modulationWaveform)
        return when (rg?.checkedRadioButtonId ?: View.NO_ID) {
            R.id.rb_mid_mod_sine -> Waveform.SINE
            R.id.rb_mid_mod_biphasic_square -> Waveform.BIPHASIC_SQUARE
            R.id.rb_mid_mod_triangle -> Waveform.TRIANGLE
            else -> Waveform.TRIANGLE // 干扰电默认
        }
    }

    //读取生物反馈刺激类型
    @SubModeInOther
    private fun readBioParamModeFromCard(): Int {
        val cardMid = findViewById<View>(R.id.bioFrequency)
        val rbEts = cardMid.findViewById<RadioButton>(R.id.rb_stim_bio_ets)
        // 默认干扰电
        return if (rbEts.isChecked) {
            SubModeInOther.ETS  // 0x03
        } else {
            SubModeInOther.CCFES  // 0x04
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

    @SuppressLint("AutoDispose")
    override fun onDestroy() {
        // ✅ 先停 server（释放端口）
        disposables.add(
            wifiTcpServerManager.stop()
                .subscribe(
                    { Timber.i("tcp stopped onDestroy") },
                    { Timber.w(it, "tcp stop onDestroy fail") }
                )
        )

        // ✅ 再清理所有订阅
        disposables.clear()
        super.onDestroy()
    }





    //wifi相关
    private var selectedPeer: String? = null
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val uiTicker = object : Runnable {
        override fun run() {
            updateTcpClientsUi()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    private fun updateWifiPanel() {
        binding.tvWifiInfo.text =
            "Wi-Fi信息(本机私网IP):\n${LocalIpHelper.listPrivateIPv4()}"
    }

    private val deviceIdByPeer = mutableMapOf<String, String>()

    private fun onTcpPacket(p: TcpPacket) {
        val text = p.byteString.toString(Charsets.UTF_8).trim()

        // 例：ID=DeviceA
        if (text.startsWith("ID=")) {
            val id = text.removePrefix("ID=").trim()
            val ip = p.peer.substringBefore(':')
            val port = p.peer.substringAfter(':').toIntOrNull()

            deviceIdByPeer[p.peer] = id
            Timber.i("Device ID bound: id=%s peer=%s ip=%s port=%s", id, p.peer, ip, port)

            // 立刻刷新一下 UI（可选）
            updateTcpClientsUi()
        }
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(uiTicker)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(uiTicker)
    }

    private fun updateTcpClientsUi() {
        val peers = wifiTcpServerManager.connectedPeers()

        Timber.i("updateTcpClientsUi: peersCount=%d peers=%s",
            peers.size,
            peers.joinToString()
        )

        val rg = binding.rgClients
        rg.removeAllViews()

        updateConnStatusImage(peers.size)

        if (peers.isEmpty()) {
            // 没设备时显示一行灰提示也行：这里简单点用一个不可选的 RadioButton
            val rb = RadioButton(this).apply {
                text = "TCP已连接：0"
                isEnabled = false
            }
            rg.addView(rb)
            selectedPeer = null
            return
        }

        // 如果之前选中的设备已经断开，就清空选择
        if (selectedPeer != null && selectedPeer !in peers) {
            selectedPeer = null
        }

        peers.forEach { peer ->
            val id = deviceIdByPeer[peer] // 你已有的映射：peer -> 设备名/ID
            val label = if (id.isNullOrBlank()) peer else "$id  ($peer)"

            val rb = RadioButton(this).apply {
                text = label
                tag = peer // ✅ 用 tag 存真实 peer
                isChecked = (peer == selectedPeer)
                setOnCheckedChangeListener { buttonView, isChecked ->
                    if (isChecked) {
                        selectedPeer = buttonView.tag as String
                    }
                }
            }
            rg.addView(rb)
        }

        // 如果还没选中过任何设备，默认选第一个（可选）
        if (selectedPeer == null && peers.isNotEmpty()) {
            selectedPeer = peers.first()
            // 触发勾选状态刷新
            (0 until rg.childCount).forEach { i ->
                val v = rg.getChildAt(i)
                if (v is RadioButton) v.isChecked = (v.tag == selectedPeer)
            }
        }


    }

    private fun updateConnStatusImage(count: Int) {
        val hasClient = count > 0
        Timber.i("clientCount=%d", count)

        binding.ivConnStatus.setImageResource(
            if (hasClient) R.drawable.laugh_ else R.drawable.cry_
        )
        binding.ivConnStatus.contentDescription = if (hasClient) "已连接" else "未连接"
    }
}