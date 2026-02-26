package com.intellizon.biofeedbacktest

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

//import com.intellizon.biofeedbacktest.BuildConfig

import com.intellizon.biofeedbacktest.R
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var isLowFreqExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.d("version name: ${BuildConfig.VERSION_NAME}")



        val cardLow = findViewById<View>(R.id.lowFrequency)
        val grid = findViewById<View>(R.id.panelRight)
        val overlay = findViewById<View>(R.id.lowFreqExpandedOverlay)

        bindDoubleTapToClose(overlay) {
            // 这里直接收起
            overlay.visibility = View.GONE
            grid.visibility = View.VISIBLE
            isLowFreqExpanded = false
        }

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                toggleLowFreqExpanded(grid, overlay)
                return true
            }
        })

        // 双击空白处也能触发：给 card1 整块接管触摸
        cardLow.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            true
        }

        // 放大态按返回先收起
        onBackPressedDispatcher.addCallback(this) {
            if (isLowFreqExpanded) {
                toggleLowFreqExpanded(grid, overlay)
            } else {
                finish()
            }
        }


    }


    private fun toggleLowFreqExpanded(grid: View, overlay: View) {
        isLowFreqExpanded = !isLowFreqExpanded
        if (isLowFreqExpanded) {
            grid.visibility = View.INVISIBLE   // 或 GONE（看你要不要保持布局）
            overlay.visibility = View.VISIBLE
        } else {
            overlay.visibility = View.GONE
            grid.visibility = View.VISIBLE
        }
    }


    private fun bindDoubleTapToClose(
        overlay: View,
        onClose: () -> Unit
    ) {
        val detector = GestureDetector(overlay.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                onClose()
                return true
            }
        })

        overlay.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()

    }
}
