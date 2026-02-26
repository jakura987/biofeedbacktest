package com.intellizon.biofeedbacktest.domain

import androidx.annotation.IntDef

data class TherapyDetail constructor(
    val name: String,
    @field:TherapyMode
    val mode: Int,


    @field:SubModeInOther
    val subMode: Int = 0,

    /*
    * 震颤频率
    * */

    val tremorFrequency: Int = 0,

    val hasChannelA: Boolean = false,
    val hasChannelB: Boolean = false,
    val hasChannelC: Boolean = false,
    val hasChannelD: Boolean = false,
    val isDeleted: Boolean = false,
    /*
    * 频差周期
    * */
    val frequencyShift: Int = 0,

    /*
    * 动态周期
    * */
    val dynamicShifts: Int = 0,

)



@IntDef(
    TherapyMode.BASIC_TEST,
    TherapyMode.MIDDLE,
    TherapyMode.LOW_FREQUENCY,
    TherapyMode.BIOFEEDBACK,
    TherapyMode.EVALUATION,
)
annotation class TherapyMode {
    companion object {
        fun from(mode: Int): String =
            when (mode) {
                MIDDLE -> "中频"
                LOW_FREQUENCY -> "低频"
                BIOFEEDBACK -> "生物反馈"
                EVALUATION -> "评估"
                else -> "UNKNOWN"
            }

        const val MIDDLE: Int = 0b0011 //3
        const val LOW_FREQUENCY: Int = 0b0100 //4
        const val BIOFEEDBACK: Int = 0b0101   // NEW (生物反馈)
        const val EVALUATION: Int = 0b0110    // 评估

        //非正常业务使用
        const val BASIC_TEST = 0xFF

        // Tab 个数（低频/中频/生物反馈/其他）
        const val MODE_COUNT = 3

        val IN_USE = listOf(LOW_FREQUENCY, MIDDLE, BIOFEEDBACK, EVALUATION)
    }
}


@IntDef(
    TherapyParamMode.MIDDLE_INTERFERENCE,
    TherapyParamMode.MIDDLE_MODULATED,
    TherapyParamMode.LOW_FREQ,
    TherapyParamMode.EVA,
    TherapyParamMode.BASIC_TEST,
    TherapyParamMode.BIOFEEDBACK_ETS,     // NEW
    TherapyParamMode.BIOFEEDBACK_CCFES    // NEW
)
@Retention(AnnotationRetention.SOURCE)
annotation class TherapyParamMode {
    companion object {
        const val LOW_FREQ: Int = 0x04
        const val EVA: Int = 0x06 //评估
        const val OTHER_TPAS: Int = 0x12
        const val BASIC_TEST: Int = 0xFF

        // —— Middle（细分到组合码）——
        const val MIDDLE_INTERFERENCE: Int = 0x41  // (MIDDLE, 0x11) //65
        const val MIDDLE_MODULATED: Int = 0x43   // (MIDDLE, 0x12)

        // —— Biofeedback ——（组合码）
        const val BIOFEEDBACK_ETS: Int = 0x51   // ETS
        const val BIOFEEDBACK_CCFES: Int = 0x52   // CCFES

        @TherapyParamMode
        fun create(@TherapyMode mode: Int, @SubMode subMode: Int): Int {
            return when (mode) {
                TherapyMode.MIDDLE -> when (subMode) {
                    SubModeInMiddle.INTERFERENCE -> MIDDLE_INTERFERENCE
                    SubModeInMiddle.MODULATED -> MIDDLE_MODULATED
                    else -> throw IllegalArgumentException("MIDDLE: unknown subMode=$subMode")
                }

                TherapyMode.LOW_FREQUENCY -> LOW_FREQ
                TherapyMode.EVALUATION -> EVA

                TherapyMode.BASIC_TEST -> BASIC_TEST

                TherapyMode.BIOFEEDBACK -> when (subMode) {
                    SubModeInOther.ETS -> BIOFEEDBACK_ETS
                    SubModeInOther.CCFES -> BIOFEEDBACK_CCFES
                    else -> throw IllegalArgumentException("BIOFEEDBACK: unknown subMode=$subMode")
                }

                else -> throw IllegalArgumentException("unknown mode=$mode")
            }
        }

        /** 可逆：从编码还原 (mode, subMode)  (decode)*/
        fun parse(@TherapyParamMode mode: Int): Pair<Int, Int> {
            return when (mode) {
                // Middle
                //MIDDLE_FREQ         -> TherapyMode.MIDDLE to SubModeInMiddle.UNSET
                MIDDLE_INTERFERENCE -> TherapyMode.MIDDLE to SubModeInMiddle.INTERFERENCE
                MIDDLE_MODULATED -> TherapyMode.MIDDLE to SubModeInMiddle.MODULATED
                // Low
                // Low（无子模式）
                // Other
                // Biofeedback
                BASIC_TEST -> TherapyMode.BASIC_TEST to 0x00
                BIOFEEDBACK_ETS -> TherapyMode.BIOFEEDBACK to SubModeInOther.ETS
                BIOFEEDBACK_CCFES -> TherapyMode.BIOFEEDBACK to SubModeInOther.CCFES

                else -> throw IllegalArgumentException("unknown param mode=$mode")
            }
        }
    }
}


@IntDef(SubModeInOther.ETS, SubModeInOther.CCFES, SubModeInOther.SUB_MODE_UNSET)
annotation class SubModeInOther {
    companion object {
        /** ✅ BIOFEEDBACK: ETS */
        const val ETS: Int = 0x03
        /** ✅ BIOFEEDBACK: CCFES */
        const val CCFES: Int = 0x04
        /** 未初始化兜底 */
        const val SUB_MODE_UNSET: Int = -1
    }
}

@IntDef(SubModeInMiddle.INTERFERENCE, SubModeInMiddle.MODULATED, SubModeInMiddle.UNSET)
@Retention(AnnotationRetention.SOURCE)
annotation class SubModeInMiddle {
    companion object {
        const val UNSET = -1
        /** 干扰电刺激 */
        const val INTERFERENCE = 0x11
        /** 调制中频电刺激 */
        const val MODULATED = 0x12

    }
}


@IntDef(
    SubModeInMiddle.INTERFERENCE,
    SubModeInMiddle.MODULATED,
    SubModeInMiddle.UNSET,
    SubModeInOther.ETS,
    SubModeInOther.CCFES,

)
@Retention(AnnotationRetention.SOURCE)
annotation class SubMode

