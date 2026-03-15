package com.intellizon.biofeedbacktest.vo

import androidx.annotation.Keep
import com.intellizon.biofeedbacktest.domain.ChannelDetail
import com.intellizon.biofeedbacktest.domain.ChannelName
import com.intellizon.biofeedbacktest.domain.TherapyMode
import java.util.Stack

@Keep
class ChannelVO(
    @TherapyMode val mode: Int,
    @ChannelName val channelName: Int,
    var dto: ChannelDetail,
    val snapshots: Stack<ChannelDetail> = Stack(),
) {

    var delayProgress: Int
        get() {
            val sec = dto.delayTime ?: 0.0
            return kotlin.math.round(sec / 0.1).toInt().coerceIn(0, 200)
        }
        set(value) {
            val p = value.coerceIn(0, 200)
            dto = dto.copy(delayTime = p * 0.1)
        }

    var riseProgress: Int
        get() {
            val sec = dto.riseTime ?: 3.0
            return kotlin.math.round(sec / 0.5).toInt().coerceIn(0, 40)
        }
        set(value) {
            val p = value.coerceIn(0, 40)
            dto = dto.copy(riseTime = p * 0.5)
        }

    var descendProgress: Int
        get() {
            val sec = dto.fallTime ?: 3.0
            return kotlin.math.round(sec / 0.5).toInt().coerceIn(0, 40)
        }
        set(value) {
            val p = value.coerceIn(0, 40)
            dto = dto.copy(fallTime = p * 0.5)
        }

    var restProgress: Int
        get() {
            val sec = dto.restTime ?: 2.0
            return kotlin.math.round(sec / 0.5).toInt().coerceIn(0, 40)
        }
        set(value) {
            val p = value.coerceIn(0, 40)
            dto = dto.copy(restTime = p * 0.5)
        }

    var sustainProgress: Int
        get() {
            val sec = dto.sustainTime ?: 2.0
            return kotlin.math.round(sec / 0.5).toInt().coerceIn(0, 40)
        }
        set(value) {
            val p = value.coerceIn(0, 40)
            dto = dto.copy(sustainTime = p * 0.5)
        }

    var widthProgress: Int
        get() {
            val width = dto.width ?: 100
            return (width / 10).coerceIn(0, 100_000)
        }
        set(value) {
            val p = value.coerceIn(0, 100_000)
            dto = dto.copy(width = p * 10)
        }

    var totalTimeProgress: Int
        get() {
            val totalTime = dto.totalTime ?: 5
            return totalTime.coerceIn(0, 99)
        }
        set(value) {
            val p = value.coerceIn(0, 99)
            dto = dto.copy(totalTime = p * 1)
        }


    var amplitudeProgress: Int //AmplitudeProgress
        get() {
            val amplitude = dto.amplitude ?: 0
            return amplitude.coerceIn(0, 100)
        }
        set(value) {
            val p = value.coerceIn(0, 100)
            dto = dto.copy(amplitude = p * 1)
        }

    //脉冲频率
    var frequencyMinProgress: Int
        get() {
            val f = (dto.frequencyMin ?: 0.0).coerceIn(0.0, 1000.0)
            return if (f < 1.0) {
                kotlin.math.round(f / 0.1).toInt().coerceIn(0, 9)
            } else {
                val iv = kotlin.math.round(f).toInt().coerceIn(1, 1000)
                9 + iv
            }
        }
        set(value) {
            val p = value.coerceIn(0, 1009)
            val f = if (p <= 9) {
                p * 0.1
            } else {
                (p - 9).toDouble()
            }
            dto = dto.copy(frequencyMin = f, frequencyMax = f)
        }


    var intensityProgress: Int
        get() {
            val intensity = dto.presetIntensity ?: 75
            return (intensity / 25).coerceIn(0, 4)

        }
        set(value) {
            val p = value.coerceIn(0, 4)
            // progress -> 实际值：0/25/50/75/100
            dto = dto.copy(presetIntensity = p * 25)
        }


    var tiloadProgress: Int
        get() {
            val tiload = dto.tiLoadFreq ?: 4.0
            return kotlin.math.round(tiload / 0.5).toInt().coerceIn(0, 20)
        }
        set(value) {
            val p = value.coerceIn(0, 20)
            dto = dto.copy(tiLoadFreq = p * 0.5)
        }


    var modulationFreqProgress: Int
        get() {
            val tiload = dto.modulationFreq ?: 1
            return tiload.coerceIn(0, 150)
        }
        set(value) {
            val p = value.coerceIn(0, 150)
            dto = dto.copy(modulationFreq = p)
        }


    //动态周期(中频)
    var dynamicShiftProgress: Int
        get() {
            val dynamicShift = dto.dynamicShifts ?: 4
            return dynamicShift.coerceIn(0, 10)
        }
        set(value) {
            val p = value.coerceIn(0, 10)
            dto = dto.copy(dynamicShifts = p)
        }

    //差频周期(中频)
    var frequencyShiftProgress: Int
        get() {
            val dynamicShift = dto.frequencyShift ?: 15
            return dynamicShift.coerceIn(0, 30)
        }
        set(value) {
            val p = value.coerceIn(0, 30)
            dto = dto.copy(frequencyShift = p)
        }

    //差频频率(中频)
    var frequencyMaxProgress: Int
        get() {
            val max = dto.frequencyMax ?: 40.0
            return kotlin.math.round(max / 1.0).toInt().coerceIn(0, 200)
        }
        set(value) {
            val p = value.coerceIn(0, 200)
            dto = dto.copy(frequencyMax = p * 1.0)
        }

    //收缩（生物反馈）
    var contractionTimeProgress: Int
        get() {
            val max = dto.contractionTimeSec
            return max.coerceIn(0, 20)
        }
        set(value) {
            val p = value.coerceIn(0, 20)
            dto = dto.copy(contractionTimeSec = p )
        }

    //放松（生物反馈）
    var stimulationTimeProgress: Int
        get() {
            val max = dto.stimulationTimeSec
            return max.coerceIn(0, 20)
        }
        set(value) {
            val p = value.coerceIn(0, 20)
            dto = dto.copy(stimulationTimeSec = p )
        }


    fun pushSnapshot() {
        snapshots.push(dto)
    }

    fun restoreLastSnapshotOrKeep() {
        if (snapshots.isNotEmpty()) dto = snapshots.pop()
    }
}