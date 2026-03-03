package com.intellizon.biofeedbacktest.encode

import com.intellizon.biofeedbacktest.domain.ChannelDetail

/**
 * Classname: IChannelCoderV1 </p>
 * Description: TODO </p>
 * Created by Lenovo on 2025/3/25.
 */
interface IChannelCoderV1 {
    //fun encodeChannel(channelDetail: ChannelDetail, mode: Int): ByteArray

    fun encodeChannel(channelDetail: ChannelDetail): ByteArray

    fun decodeChannel(bytes: ByteArray): ChannelDetail
}