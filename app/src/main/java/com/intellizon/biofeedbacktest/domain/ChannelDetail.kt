package com.intellizon.biofeedbacktest.domain

import androidx.annotation.FloatRange
import androidx.annotation.IntDef
import androidx.annotation.IntRange

/**
 * 单通道参数（阉割版：只保留你当前需要的字段）
 */
data class ChannelDetail(
    val therapyId: Long,
    @ChannelName
    val channelName: Int,              // A/B/C/D 等
    @Waveform
    val waveform: Int,
    @Waveform
    val modulationWaveform: Int,       // 调制波形
    @field:IntRange(from = 0, to = 100)
    val presetIntensity: Int? = null,  // 0-100 mA
    val width: Int? = null,            // 脉宽（单位你后面定：us/ms）
    @field:FrequencyType
    val frequencyType: Int,
    val frequencyMin: Double? = null,
    val frequencyMax: Double? = null,

    val tiLoadFreq: Double? = null,    // 载波频率(工作频率)
    val modulationFreq: Int? = null,   // 调制频率

    @field:FloatRange(from = 0.0, to = 99.0)
    val riseTime: Double? = null,
    val fallTime: Double? = null,

    @field:FloatRange(from = 0.0, to = 99.0)
    val restTime: Double? = null,      // 秒
    @field:FloatRange(from = 0.0, to = 99.0)
    val sustainTime: Double? = null,   // 秒
    @field:FloatRange(from = 0.0, to = 60.0)
    val delayTime: Double? = null,     // 秒
    @field:IntRange(from = 1, to = 99)
    val totalTime: Int? = null,        // 总时长（单位你后面定：秒/分钟）
    @field:IntRange(from = 0, to = 20)
    val contractionTimeSec: Int = 0,   // 收缩时间（秒）
    @field:IntRange(from = 0, to = 20)
    val stimulationTimeSec: Int = 0,   // 刺激时间（秒）
    val partDescription: String? = null,
    @field:SubModeInOther
    val subMode: Int? = null,          // 其他类型的子类型（实际存储在 Therapy 级）
    val frequencyShift: Int? = null,   // 频差周期（实际存储在 Therapy 级）
    val dynamicShifts: Int? = null,    // 动态周期（实际存储在 Therapy 级）
)


@IntDef(
    ChannelName.CHANNEL_A,
    ChannelName.CHANNEL_B,
    ChannelName.CHANNEL_C,
    ChannelName.CHANNEL_D,
)
annotation class ChannelName {
    companion object {
        const val CHANNEL_A: Int = 1
        const val CHANNEL_B: Int = 2
        const val CHANNEL_C: Int = 3
        const val CHANNEL_D: Int = 4

        fun from(value: Int): String =
            when (value) {
                CHANNEL_A -> "通道A"
                CHANNEL_B -> "通道B"
                CHANNEL_C -> "通道C"
                CHANNEL_D -> "通道D"
                else -> "未知通道"
            }
    }
}


@IntDef(
    Waveform.BIPHASIC_SQUARE,
    Waveform.UNIPHASIC_SQUARE,
    Waveform.TRIANGLE,
    Waveform.SINE,
    Waveform.TENS,
    //Waveform.TRIANGULAR,
    Waveform.EXPONENTIAL,
    Waveform.SAWTOOTH,
    Waveform.SHARP,
    Waveform.TRAPEZOID
    )
annotation class Waveform {
    companion object {
        /**
         * 单相方波
         * */
        const val UNIPHASIC_SQUARE: Int = 0x00
        /**
         * 双相方波
         * */
        const val BIPHASIC_SQUARE: Int = 0x01
        /**
         * 正弦波
         * */
        const val SINE: Int = 0x02
        /**
         * 梯形波
         * */
        const val TRAPEZOID: Int = 0x03
        /**
         * 锯齿波
         */
        const val SAWTOOTH: Int = 0x04
        /**
         * 三角波
         * */
        const val TRIANGLE: Int = 0x05
        /**
         * 指数波
         */
        const val EXPONENTIAL: Int = 0x06
        /**
         * TENS
         * */
        const val TENS: Int = 0x07
        /**
         * 尖波
         */
        const val SHARP: Int = 0x08
        fun from(value: Int): String =
            when (value) {
                BIPHASIC_SQUARE -> "双相方波"
                UNIPHASIC_SQUARE -> "单相方波"
                TRIANGLE -> "三角波"
                SINE -> "正弦波"
                TENS -> "不对称双相波"
                TRAPEZOID -> "梯形波"
                EXPONENTIAL -> "指数波"
                SAWTOOTH -> "锯齿波"
                SHARP -> "尖波"
                else -> "未知波形"
            }
    }
}


@IntDef(
    FrequencyType.CONSTANT_FREQUENCY,
    FrequencyType.VARIABLE_FREQUENCY
)
annotation class FrequencyType{
    companion object{
        const val CONSTANT_FREQUENCY: Int = 0b00 //恒定频率
        const val VARIABLE_FREQUENCY: Int = 0b01 //变频刺激
    }
}


