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
            return kotlin.math.round(sec / 0.5).toInt().coerceIn(0, 40)
        }
        set(value) {
            val p = value.coerceIn(0, 40)
            dto = dto.copy(delayTime = p * 0.5)
        }

    var riseProgress: Int
        get() {
            val sec = dto.riseTime ?: 0.0
            return kotlin.math.round(sec / 0.5).toInt().coerceIn(0, 40)
        }
        set(value) {
            val p = value.coerceIn(0, 40)
            dto = dto.copy(riseTime = p * 0.5)
        }

    var descendProgress: Int
        get() {
            val sec = dto.fallTime ?: 0.0
            return kotlin.math.round(sec / 0.5).toInt().coerceIn(0, 40)
        }
        set(value) {
            val p = value.coerceIn(0, 40)
            dto = dto.copy(fallTime = p * 0.5)
        }

    var restProgress: Int
        get() {
            val sec = dto.restTime ?: 0.0
            return kotlin.math.round(sec / 0.5).toInt().coerceIn(0, 40)
        }
        set(value) {
            val p = value.coerceIn(0, 40)
            dto = dto.copy(restTime = p * 0.5)
        }

    var sustainProgress: Int
        get() {
            val sec = dto.sustainTime ?: 0.0
            return kotlin.math.round(sec / 0.5).toInt().coerceIn(0, 40)
        }
        set(value) {
            val p = value.coerceIn(0, 40)
            dto = dto.copy(sustainTime = p * 0.5)
        }

    var widthProgress: Int
        get() {
            val width = dto.width ?: 0
            return (width / 10).coerceIn(0, 100_000)
        }
        set(value) {
            val p = value.coerceIn(0, 100_000)
            dto = dto.copy(width = p * 10)
        }

    var totalTimeProgress: Int
        get() {
            val totalTime = dto.totalTime ?: 0
            return totalTime.coerceIn(0, 99)
        }
        set(value) {
            val p = value.coerceIn(0, 99)
            dto = dto.copy(totalTime = p * 1)
        }

    var frequencyMinProgress: Int
        get() {
            val f = (dto.frequencyMin ?: 0.1).coerceIn(0.1, 1000.0)
            return if (f < 1.0) {
                // 0.1..0.9 -> progress 0..8
                val idx = kotlin.math.round((f - 0.1) / 0.1).toInt()
                idx.coerceIn(0, 8)
            } else {
                // 1..1000 -> progress 9..1008
                val iv = kotlin.math.round(f).toInt().coerceIn(1, 1000)
                8 + iv // 1->9, 1000->1008
            }
        }
        set(value) {
            val p = value.coerceIn(0, 1008)
            val f = if (p <= 8) {
                // 0..8 -> 0.1..0.9
                0.1 + p * 0.1
            } else {
                // 9..1008 -> 1..1000
                (p - 8).toDouble()
            }
            dto = dto.copy(frequencyMin = f)
        }

    fun pushSnapshot() {
        snapshots.push(dto)
    }

    fun restoreLastSnapshotOrKeep() {
        if (snapshots.isNotEmpty()) dto = snapshots.pop()
    }
}