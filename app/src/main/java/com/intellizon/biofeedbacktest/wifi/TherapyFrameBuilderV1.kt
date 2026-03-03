package com.intellizon.biofeedbacktest.wifi

import com.intellizon.biofeedbacktest.domain.ChannelDetail
import com.intellizon.biofeedbacktest.domain.TherapyDetail
import com.intellizon.biofeedbacktest.domain.TherapyParamMode
import com.intellizon.biofeedbacktest.encode.IChannelCoderV1
import com.intellizon.biofeedbacktest.encode.TherapyCoderV1
import kotlin.math.min

class TherapyFrameBuilderV1 {

    companion object {
        private const val HEADER_0: Byte = 0x5A
        private const val HEADER_1: Byte = 0xA5.toByte()

        private const val CMD_PUSH: Byte = 0x20
        private const val TYPE_THERAPY: Byte = 0x12

        private const val NAME_PAGE_SIZE = 29
    }

    /** 生成完整帧：5A A5 | LEN | 20 | 12 | DATA | CS */
    fun buildTherapyFrame(
        schemeNo: Int,
        therapyDetail: TherapyDetail,
        channelsByName: Map<Int, ChannelDetail>,
        therapyCoder: TherapyCoderV1 = TherapyCoderV1.INSTANCE,
    ): ByteArray {
        val paramMode = TherapyParamMode.create(therapyDetail.mode, therapyDetail.subMode)
        val modeByte: Byte = paramMode.toByte()
        val flagByte: Byte = encodeFlag(modeByte, therapyDetail)

        // DATA = schemeNo + (L,page,namePage32) + mode + flag + N*34B
        val data = buildData(
            schemeNo = schemeNo,
            name = therapyDetail.name,
            modeByte = modeByte,
            flagByte = flagByte,
            therapyDetail = therapyDetail,
            channelsByName = channelsByName,
            therapyCoder = therapyCoder,
            paramMode = paramMode,
        )


        return buildFrame(
            cmd = CMD_PUSH,
            type = TYPE_THERAPY,
            data = data,
        )
    }

    private fun buildData(
        schemeNo: Int,
        name: String,
        modeByte: Byte,
        flagByte: Byte,
        therapyDetail: TherapyDetail,
        channelsByName: Map<Int, ChannelDetail>,
        therapyCoder: TherapyCoderV1,          // ✅ 改成传 TherapyCoderV1
        @TherapyParamMode paramMode: Int,      // ✅ 传入 paramMode（避免重复计算）
    ): ByteArray {
        val namePart = encodeNamePage29(name, pageIndex = 0)

        val channelBytes = buildList<ByteArray> {
            if (therapyDetail.hasChannelA) channelsByName[1]?.let { add(therapyCoder.encodeChannel(paramMode, it)) }
            if (therapyDetail.hasChannelB) channelsByName[2]?.let { add(therapyCoder.encodeChannel(paramMode, it)) }
            if (therapyDetail.hasChannelC) channelsByName[3]?.let { add(therapyCoder.encodeChannel(paramMode, it)) }
            if (therapyDetail.hasChannelD) channelsByName[4]?.let { add(therapyCoder.encodeChannel(paramMode, it)) }
        }.fold(ByteArray(0)) { acc, b -> acc + b }

        return byteArrayOf((schemeNo and 0xFF).toByte()) +
                namePart +
                byteArrayOf(modeByte, flagByte) +
                channelBytes
    }

    /** 返回: [nameLen(1B)] [pageIndex(1B)] [namePage32(32B padded)] */
    private fun encodeNamePage29(name: String, pageIndex: Int): ByteArray {
        val bytes = name.toByteArray(Charsets.UTF_8)
        val L = bytes.size.coerceAtMost(0xFF)

        val page = ByteArray(NAME_PAGE_SIZE) { 0x00 }
        val copyLen = min(bytes.size, NAME_PAGE_SIZE)
        System.arraycopy(bytes, 0, page, 0, copyLen)

        return byteArrayOf(
            (L and 0xFF).toByte(),
            (pageIndex and 0xFF).toByte(),
        ) + page
    }

    /**
     * 帧 = 5A A5 | LEN | CMD | TYPE | DATA | CS
     * LEN = CMD+TYPE+DATA（不含 CS）
     */
    private fun buildFrame(cmd: Byte, type: Byte, data: ByteArray): ByteArray {
        // SDU: pdu.length = CMD(1) + TYPE(1) + DATA
        val len = 2 + data.size
        require(len <= 0xFF) { "LEN too large: $len" }

        val cs = checksumLikeSdu(len, cmd, type, data)

        return byteArrayOf(
            HEADER_0,
            HEADER_1,
            (len and 0xFF).toByte(),
            cmd,
            type,
        ) + data + byteArrayOf(cs)
    }

    private fun checksumLikeSdu(len: Int, cmd: Byte, type: Byte, data: ByteArray): Byte {
        var sum: Byte = (len and 0xFF).toByte()
        sum = (sum + cmd).toByte()
        sum = (sum + type).toByte()
        for (b in data) {
            sum = (sum + b).toByte()
        }
        return sum
    }

    private fun checksumSum8(bytes: ByteArray, startIndex: Int = 0): Byte {
        var sum = 0
        for (i in startIndex until bytes.size) {
            sum = (sum + (bytes[i].toInt() and 0xFF)) and 0xFF
        }
        return sum.toByte()
    }


    private fun encodeFlag(paramMode: Byte, therapyDetail: TherapyDetail): Byte {
        if (paramMode == TherapyParamMode.OTHER_TPAS.toByte()) {
            return (0b0001 or 0b0010).toByte()
        }
        var f = 0
        if (therapyDetail.hasChannelA) f = f or 0b0001
        if (therapyDetail.hasChannelB) f = f or 0b0010
        if (therapyDetail.hasChannelC) f = f or 0b0100
        if (therapyDetail.hasChannelD) f = f or 0b1000
        return (f and 0xFF).toByte()
    }
}

/** 打印工具 */
fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }