package com.intellizon.biofeedbacktest.module

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.intellizon.biofeedbacktest.R
import com.intellizon.biofeedbacktest.encodetool.WifiFrameDecoder
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlin.math.abs


class EncodeDialog : DialogFragment() {

    private val disposables = CompositeDisposable()
    var onDismissCallback: (() -> Unit)? = null


    @SuppressLint("AutoDispose")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val root = LayoutInflater.from(ctx).inflate(R.layout.dialog_encode, null)

        val tvHex = root.findViewById<TextView>(R.id.tvHex)
        val tvMeta = root.findViewById<TextView>(R.id.tvMeta)
        val tvDecoded = root.findViewById<TextView>(R.id.tvDecoded)

        val hex = requireArguments().getString(ARG_HEX).orEmpty()
        val meta = requireArguments().getString(ARG_META).orEmpty()


        tvMeta.text = meta
        tvHex.text = hex.ifBlank { "(暂无提交数据)" }

        // ✅ 后台解析
        if (hex.isBlank()) {
            tvDecoded.text = ""
        } else {
            tvDecoded.text = "解析中..."
            disposables.add(
                Single.fromCallable {
                    WifiFrameDecoder.parseAll(hex)
                }
                    .subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { out -> tvDecoded.text = out },
                        { e -> tvDecoded.text = "decode error: ${e.message}" }
                    )
            )
        }

        val dialog = Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        dialog.setContentView(root)

        // 点外面关闭，点panel不关闭
        val panel = root.findViewById<View>(R.id.panel)
        bindDraggablePanel(panel)

        // 外部点击关闭（仍然保留）
        root.setOnClickListener { dismissAllowingStateLoss() }

        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    companion object {
        private const val ARG_HEX = "hex"
        private const val ARG_META = "meta"
        fun newInstance(hex: String, meta: String = "") = EncodeDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_HEX, hex)
                putString(ARG_META, meta)
            }
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun bindDraggablePanel(panel: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startTx = 0f
        var startTy = 0f
        var moved = false

        panel.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    startTx = v.translationX
                    startTy = v.translationY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (!moved && (abs(dx) > 6 || abs(dy) > 6)) moved = true
                    v.translationX = startTx + dx
                    v.translationY = startTy + dy
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    true
                }
                else -> false
            }
        }
    }
}

