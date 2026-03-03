package com.intellizon.biofeedbacktest.wifi

import com.intellizon.biofeedbacktest.domain.ChannelDetail
import com.intellizon.biofeedbacktest.domain.TherapyDetail
import com.intellizon.biofeedbacktest.domain.TherapyParamMode
import com.intellizon.biofeedbacktest.encode.IChannelCoderV1
import java.io.OutputStream
import kotlin.math.min

class TherapySenderV1(
    private val out: OutputStream, // 你的 socket outputStream
) {
    companion object {
        private const val HEADER_0: Byte = 0x5A
        private const val HEADER_1: Byte = 0xA5.toByte()

        private const val CMD_PUSH: Byte = 0x20
        private const val TYPE_THERAPY: Byte = 0x12

        private const val NAME_PAGE_SIZE = 32
    }

    /**
     * 发送“下发方案”帧（测试版：namePage 固定 32B）
     *
     * @param schemeNo 方案号（比如 0x01）
     * @param therapyDetail 用来提供 mode/subMode/hasChannelA~D / nameLen
     * @param channelsByName 1..4 -> ChannelDetail（只会按 hasChannelX + flag 拼进去）
     * @param coder 负责 34B 通道块编码（低频/ETS 等你现用 LMFreqChannelCoderV1）
     */
    fun sendTherapy(
        schemeNo: Int = 0x01,
        therapyDetail: TherapyDetail,
        channelsByName: Map<Int, ChannelDetail>,
        coder: IChannelCoderV1,
    ) {
        val modeByte: Byte = TherapyParamMode.create(therapyDetail.mode, therapyDetail.subMode).toByte()
        val flagByte: Byte = encodeFlag(modeByte, therapyDetail)

        val data = buildData(
            schemeNo = schemeNo,
            name = therapyDetail.name, // 你测试版是 temp
            modeByte = modeByte,
            flagByte = flagByte,
            therapyDetail = therapyDetail,
            channelsByName = channelsByName,
            coder = coder,
        )

        val frame = buildFrame(
            cmd = CMD_PUSH,
            type = TYPE_THERAPY,
            data = data,
        )

        out.write(frame)
        out.flush()
    }

    /** DATA = schemeNo + namePart(L,page,namePage32) + mode + flag + N*34B */
    private fun buildData(
        schemeNo: Int,
        name: String,
        modeByte: Byte,
        flagByte: Byte,
        therapyDetail: TherapyDetail,
        channelsByName: Map<Int, ChannelDetail>,
        coder: IChannelCoderV1,
    ): ByteArray {
        val namePart = encodeNamePage32(name, pageIndex = 0)

        val channelBytes = buildList<ByteArray> {
            if (therapyDetail.hasChannelA) channelsByName[1]?.let { add(coder.encodeChannel(it)) }
            if (therapyDetail.hasChannelB) channelsByName[2]?.let { add(coder.encodeChannel(it)) }
            if (therapyDetail.hasChannelC) channelsByName[3]?.let { add(coder.encodeChannel(it)) }
            if (therapyDetail.hasChannelD) channelsByName[4]?.let { add(coder.encodeChannel(it)) }
        }.fold(ByteArray(0)) { acc, b -> acc + b }

        return byteArrayOf((schemeNo and 0xFF).toByte()) +
                namePart +
                byteArrayOf(modeByte, flagByte) +
                channelBytes
    }

    /**
     * 返回: [nameLen(1B)] [pageIndex(1B)] [namePage32(32B padded)]
     * - nameLen 是 UTF-8 的总长度（测试版单页就够用）
     * - pageIndex 固定 0
     */
    private fun encodeNamePage32(name: String, pageIndex: Int): ByteArray {
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
     * LEN 口径：CMD+TYPE+DATA（不含 CS）
     */
    private fun buildFrame(cmd: Byte, type: Byte, data: ByteArray): ByteArray {
        val len = (1 + 1 + data.size) // CMD + TYPE + DATA
        require(len <= 0xFF) { "LEN too large: $len" }

        val noCs = byteArrayOf(HEADER_0, HEADER_1, (len and 0xFF).toByte(), cmd, type) + data
        val cs = checksumSum8(noCs, startIndex = 2) // 常见做法：从 LEN 开始算；若你们全帧都算，就改 startIndex=0

        return noCs + byteArrayOf(cs)
    }

    /** 校验：所有字节求和取低8位（如果你们是 XOR，改这里即可） */
    private fun checksumSum8(bytes: ByteArray, startIndex: Int = 0): Byte {
        var sum = 0
        for (i in startIndex until bytes.size) {
            sum = (sum + (bytes[i].toInt() and 0xFF)) and 0xFF
        }
        return sum.toByte()
    }

    /** 复用你 TherapyCoderV1 的 flag 规则（TPAS 特判你也可以带上） */
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