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

    fun pushSnapshot() {
        snapshots.push(dto)
    }

    fun restoreLastSnapshotOrKeep() {
        if (snapshots.isNotEmpty()) dto = snapshots.pop()
    }
}