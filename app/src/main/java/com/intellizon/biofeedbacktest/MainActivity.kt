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
import com.intellizon.biofeedbacktest.progress.RyCompactSeekbar
import com.intellizon.biofeedbacktest.ui.ChannelControlsBinder
import com.intellizon.biofeedbacktest.vo.TherapyVO
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var activeOverlay: View? = null

    // 目前只开低频 overlay，其它先注释
    private val overlayIds = intArrayOf(
        R.id.lowFreqExpandedOverlay,
//        R.id.middleExpandedOverlay,
//        R.id.otherExpandedOverlay,
    )

    private val therapyVO = TherapyVO()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.d("version name: ${BuildConfig.VERSION_NAME}")

        // ===== 1) 双击 lowFrequency 进入 overlay =====
        val cardLow = findViewById<View>(R.id.lowFrequency)
        val enterLowDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    showOverlay(R.id.lowFreqExpandedOverlay)
                    return true
                }
            },
        )

        cardLow.setOnTouchListener { _, event ->
            // 这里吞掉事件也没关系，因为 cardLow 本身只是入口
            enterLowDetector.onTouchEvent(event)
            true
        }

        // ===== 2) Back 键：优先收起 overlay =====
        onBackPressedDispatcher.addCallback(this) {
            if (activeOverlay != null) {
                hideOverlay()
            } else {
                // 交给系统默认返回行为
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        // 初始态：grid 可见
        findViewById<View>(R.id.panelRight).visibility = View.VISIBLE


    }

    private fun showOverlay(overlayId: Int) {
        // 1) 隐藏所有 overlay
        overlayIds.forEach { id ->
            findViewById<View>(id)?.visibility = View.GONE
        }

        // 2) 显示目标 overlay
        val overlay = findViewById<View>(overlayId)
        overlay.visibility = View.VISIBLE
        activeOverlay = overlay

        // 3) 隐藏正常态 Grid（你的设计是 overlay 覆盖右侧，所以隐藏右侧）
        findViewById<View>(R.id.panelRight).visibility = View.INVISIBLE

        // 4) 绑定四通道（只绑定一次）
        bindChannelsForOverlayOnce(overlay)


        // ✅ 在这里绑定中间圆按钮
        bindCenterInfoButtonOnce(overlay)

        // ✅ 5) 只在 “通道A 标题(tvTitleA)” 双击关闭 overlay
        bindDoubleTapCloseToTitleAOnce(overlay)
    }

    /** 收起当前 overlay，恢复正常态 grid */
    private fun hideOverlay() {
        activeOverlay?.visibility = View.GONE
        activeOverlay = null
        findViewById<View>(R.id.panelRight).visibility = View.VISIBLE
    }

    private fun bindCenterInfoButtonOnce(overlay: View) {
        if (overlay.getTag(TAG_CENTER_BOUND) == true) return
        overlay.setTag(TAG_CENTER_BOUND, true)

        val btn = overlay.findViewById<TextView>(R.id.btnCenterInfo)
        btn.text = "info"

        btn.setOnClickListener {
            // expContentA 是 include 的根 view（ViewGroup）
            val contentA = overlay.findViewById<View>(R.id.expContentA)
            val seek = contentA.findViewById<View>(R.id.seek_delay) as? RyCompactSeekbar
            Timber.v("delay seek=%s", seek)
        }
    }

    /**
     * 只绑定一次（避免每次 showOverlay 都重复 setOnSeekBarChangeListener）
     */
    private fun bindChannelsForOverlayOnce(overlay: View) {
        if (overlay.getTag(TAG_BOUND) == true) return
        overlay.setTag(TAG_BOUND, true)

        val chA = overlay.findViewById<View>(R.id.expContentA)
        Timber.d("chA=${chA.javaClass.simpleName}, seek_delay=${chA.findViewById<View?>(R.id.seek_delay)}")

        // 你后面打开 B/C/D 时，再放开
//        val chB = overlay.findViewById<View>(R.id.expContentB)
//        val chC = overlay.findViewById<View>(R.id.expContentC)
//        val chD = overlay.findViewById<View>(R.id.expContentD)

        ChannelControlsBinder.bindDelayTime(chA, initialSec = null) { sec ->
            Timber.d("A delay=$sec")
        }

//        ChannelControlsBinder.bindDelayTime(chB, initialSec = null) { sec -> Timber.d("B delay=$sec") }
//        ChannelControlsBinder.bindDelayTime(chC, initialSec = null) { sec -> Timber.d("C delay=$sec") }
//        ChannelControlsBinder.bindDelayTime(chD, initialSec = null) { sec -> Timber.d("D delay=$sec") }
    }

    /**
     * ✅ 只对“通道A标题(tvTitleA)”做双击关闭，避免误触发（+/-、SeekBar）
     *
     */
    private fun bindDoubleTapCloseToTitleAOnce(overlay: View) {
        // 防重复绑定：overlay 级别
        if (overlay.getTag(TAG_TITLE_A_BOUND) == true) return
        overlay.setTag(TAG_TITLE_A_BOUND, true)

        // ✅ 关键：不要从 expContentA(ScrollView) 里找，直接在 overlay 里找
        val tvTitleA = overlay.findViewById<View?>(R.id.tvTitleA)
        if (tvTitleA == null) {
            Timber.e("tvTitleA not found in overlay! overlayId=${overlay.id}")
            return
        }

        // ✅ 确保它能收到 click
        tvTitleA.isClickable = true
        tvTitleA.isFocusable = true

        // 手动双击：两次点击间隔 < 500ms 就关闭
        val thresholdMs = 500L
        tvTitleA.setOnClickListener {
            val now = android.os.SystemClock.uptimeMillis()
            val last = (tvTitleA.getTag(TAG_LAST_CLICK_MS) as? Long) ?: 0L

            Timber.d("tvTitleA click: now=$now last=$last diff=${now - last}")

            if (now - last in 1..thresholdMs) {
                Timber.d("tvTitleA double click -> hideOverlay()")
                hideOverlay()
                tvTitleA.setTag(TAG_LAST_CLICK_MS, 0L) // 重置，避免三连击误判
            } else {
                tvTitleA.setTag(TAG_LAST_CLICK_MS, now)
            }
        }
    }

    private companion object {
        private const val TAG_BOUND: Int = 0xCC010001.toInt()
        private const val TAG_TITLE_A_BOUND: Int = 0xCC010004.toInt()
        private const val TAG_LAST_CLICK_MS: Int = 0xCC010005.toInt()
        private const val TAG_CENTER_BOUND: Int = 0xCC010006.toInt()
    }
}