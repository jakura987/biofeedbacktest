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
    fun pushSnapshot() {
        snapshots.push(dto)
    }

    fun restoreLastSnapshotOrKeep() {
        if (snapshots.isNotEmpty()) dto = snapshots.pop()
    }
}