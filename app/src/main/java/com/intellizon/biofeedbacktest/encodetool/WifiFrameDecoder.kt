package com.intellizon.biofeedbacktest.encodetool

import com.intellizon.biofeedbacktest.domain.Waveform
import java.lang.StringBuilder

object WifiFrameDecoder {

    private val MODE_MAP = mapOf(
        0x04 to "LOW_FREQ(低频)",
        0x11 to "MIDDLE_FREQ(legacy?)",
        0x12 to "MIDDLE_FREQ(legacy?)",
        0x41 to "MIDDLE_INTERFERENCE(干扰电刺激)",
        0x43 to "MIDDLE_MODULATED(调制中频电刺激)",
        0x51 to "BIOFEEDBACK_ETS",
        0x52 to "BIOFEEDBACK_CCFES",
    )

    data class Frame(
        val raw: ByteArray,
        val length: Int, // LEN (不含CS)，等于 CMD+TYPE+DATA 总长度
        val cmd: Int,
        val typ: Int,
        val data: ByteArray,
        val cs: Int,
    )

    fun parseAll(text: String): String {
        val stream = parseHexTokens(text)
        val frames = splitFrames(stream)
        if (frames.isEmpty()) return "No frames found."

        val sb = StringBuilder()
        frames.forEachIndexed { idx, fr ->
            sb.appendLine("================================================================================")
            sb.appendLine(
                "Frame[$idx] total=${fr.raw.size}B  LEN=0x${fr.length.toHex2()}(${fr.length}) " +
                        "CMD=0x${fr.cmd.toHex2()} TYPE=0x${fr.typ.toHex2()} CS=0x${fr.cs.toHex2()}"
            )
            sb.appendLine("RAW: ${fr.raw.toHex()}")

            if (fr.cmd == 0x20 && fr.typ == 0x12) {
                sb.appendLine(decodeType12(fr))
            } else {
                sb.appendLine("DATA(${fr.data.size}): ${fr.data.toHex()}")
            }
        }
        return sb.toString().trimEnd()
    }

    /** 从任意文本中提取所有两位HEX */
    fun parseHexTokens(text: String): ByteArray {
        val re = Regex("""\b[0-9A-Fa-f]{2}\b""")
        val list = re.findAll(text).map { it.value.toInt(16).toByte() }.toList()
        return list.toByteArray()
    }

    /** 按 5A A5 | LEN | ... | CS 切帧；总长=LEN+4 */
    fun splitFrames(stream: ByteArray): List<Frame> {
        val frames = mutableListOf<Frame>()
        var i = 0
        val n = stream.size
        while (i + 4 <= n) {
            val b0 = stream[i].u8()
            val b1 = stream[i + 1].u8()
            if (b0 == 0x5A && b1 == 0xA5) {
                val length = stream[i + 2].u8()
                val total = length + 4
                if (i + total <= n && total >= 4) {
                    val raw = stream.copyOfRange(i, i + total)
                    val cmd = raw[3].u8()
                    val typ = raw[4].u8()
                    val dataLen = (length - 2).coerceAtLeast(0)
                    val data = raw.copyOfRange(5, 5 + dataLen)
                    val cs = raw.last().u8()
                    frames.add(Frame(raw, length, cmd, typ, data, cs))
                    i += total
                    continue
                }
            }
            i += 1
        }
        return frames
    }

    private fun decodeType12(frame: Frame): String {
        val data = frame.data
        val sb = StringBuilder()
        sb.appendLine("TYPE=0x12 DATA_LEN=${data.size}")

        // 情况1：评估 4B
        if (data.size == 4) {
            val schemeNo = data[0].u8()
            val chCount = data[1].u8()
            val contraction = data[2].u8()
            val relaxation = data[3].u8()
            sb.appendLine("  evaluation: schemeNo=$schemeNo chCount=$chCount contraction=${contraction}s relaxation=${relaxation}s")
            return sb.toString().trimEnd()
        }

        // 情况2：合并写入：32(name) + 2(mode+flag) + N*34(channel)
        if (data.size < 34) {
            sb.appendLine("  [WARN] too short to be all-in-one")
            sb.appendLine("  data: ${data.toHex()}")
            return sb.toString().trimEnd()
        }

        val name32 = data.copyOfRange(0, 32)
        val mode = data[32].u8()
        val chFlag = data[33].u8()
        val rest = data.copyOfRange(34, data.size)

        val nameInfo = decodeNamePage32(name32)
        val modeName = MODE_MAP[mode] ?: "未知模式(0x${mode.toHex2()})"

        sb.appendLine("================================================================================")
        sb.appendLine("================================================================================")
        sb.appendLine("  方案号=0x${nameInfo.schemeNo.toHex2()}  页号=${nameInfo.pageNo}  名称长度=${nameInfo.nameLen}")
        sb.appendLine("  方案名=${nameInfo.name.ifBlank { "(空)" }} nameBytes=${nameInfo.nameBytes.toHex()}")
        sb.appendLine("  模式=0x${mode.toHex2()}（$modeName）")

        val chs = flagToChannels(chFlag)
        sb.appendLine("  channelFlag=0x${chFlag.toHex2()} -> $chs (count=${chs.size})")

        if (rest.size % 34 != 0) {
            sb.appendLine("  [WARN] channelBlocks size not multiple of 34: ${rest.size}")
        }

        var idx = 0
        var off = 0
        while (off + 34 <= rest.size) {
            val blk = rest.copyOfRange(off, off + 34)
            val info = decodeChannelBlock34(blk)
            val wf = info.waveform
            val modulationWf = info.modulationWaveform


            sb.appendLine("  channelBlock[$idx] channel=${info.channel}")
            sb.appendLine("  [0] flag0 (通道标识)                    : 0x${info.flag0.toHex2()} (raw=${blk.sliceHex(0,0)})")
            sb.appendLine("  [1] waveform (刺激/载波波形)             : ${Waveform.from(wf)} (raw=${blk.sliceHex(1,1)} )")
            sb.appendLine("  [2,3] intensity (电流强度)              : ${info.intensity} (raw/2) (raw=${blk.sliceHex(2,3)})")
            sb.appendLine("  [4,5,6] width_us (脉冲宽度)             : ${info.widthUs} (raw=${blk.sliceHex(4,6)})")
            sb.appendLine("  [7] delay_s (延时时间)                  : ${info.delayS} (raw/10) (raw=${blk.sliceHex(7,7)})")
            sb.appendLine("  [8] freqType (0恒定,1变频)              : ${info.freqType} (raw=${blk.sliceHex(8,8)})")
            sb.appendLine("  [9,10] fMin_hz (调制/脉冲频率)               : ${info.fMinHz} (raw/10) (raw=${blk.sliceHex(9,10)})")
            sb.appendLine("  [11,12] fMax_hz (差频频率)              : ${info.fMaxHz} (raw=${blk.sliceHex(11,12)})")

            sb.appendLine("  [13,14] rise_ms (上升时间)              : ${info.riseMsOrTick} (raw/2) (raw=${blk.sliceHex(13,14)})")
            sb.appendLine("  [15,16] fall_ms (下降时间)              : ${info.fallMsOrTick} (raw/2) (raw=${blk.sliceHex(15,16)})")
            sb.appendLine("  [17] work_s (工作/维持)                 : ${info.workS} (raw/2) (raw=${blk.sliceHex(17,17)})")
            sb.appendLine("  [19] rest_s (休息时间)                  : ${info.restS} (raw/2) (raw=${blk.sliceHex(19,19)})")
            sb.appendLine("  [21] total_min (治疗时间min)            : ${info.totalMin} (raw=${blk.sliceHex(21,21)})")

            sb.appendLine("  [22] modulationWaveform (调制波形)      : ${Waveform.from(modulationWf)} (raw=${blk.sliceHex(22,22)})")
            sb.appendLine("  [23] depth_percent (调幅深度%)          : ${info.depthPercent} (raw=${blk.sliceHex(23,23)})")

            sb.appendLine("  [24,25,26] carrier_khz (工作频率kHz)              : ${info.carrierKhz} (raw/10) (raw=${blk.sliceHex(24,26)})")

            sb.appendLine("  [27] diffPeriod_s (差频周期s)           : ${info.diffPeriodS} (raw=${blk.sliceHex(27,27)})")
            sb.appendLine("  [28] dynamicPeriod_s (动态周期s)        : ${info.dynamicPeriodS} (raw=${blk.sliceHex(28,28)})")
            sb.appendLine("  [29] stimulation_s (放松时间s)          : ${info.stimulationS} (raw=${blk.sliceHex(29,29)})")
            sb.appendLine("  [30] contraction_s (收缩时间s)          : ${info.contractionS} (raw=${blk.sliceHex(30,30)})")
            sb.appendLine("  [31,32] 阈值                           : ${info.threshold} (raw=${blk.sliceHex(31,32)})")

            sb.appendLine("  [33] 预留       : ${info.reservation} (raw=${blk.sliceHex(30,30)})")

            sb.appendLine("  rawHex(block34)                         : ${blk.toHex()}")

            idx++
            off += 34
        }

        if (off < rest.size) {
            sb.appendLine("  [WARN] drop tail bytes: ${rest.size - off} (need 34)")
        }

        return sb.toString().trimEnd()
    }

    private fun flagToChannels(flag: Int): List<String> {
        val chs = mutableListOf<String>()
        if ((flag and 0b0001) != 0) chs.add("A")
        if ((flag and 0b0010) != 0) chs.add("B")
        if ((flag and 0b0100) != 0) chs.add("C")
        if ((flag and 0b1000) != 0) chs.add("D")
        return chs
    }

    data class NameInfo(
        val schemeNo: Int,
        val nameLen: Int,
        val pageNo: Int,
        val name: String,
        val nameBytes: ByteArray,
    )

    private fun decodeNamePage32(name32: ByteArray): NameInfo {
        val schemeNo = name32[0].u8()
        val nameLen = name32[1].u8()
        val pageNo = name32[2].u8()
        val end = (3 + nameLen).coerceAtMost(name32.size)
        val nameBytes = name32.copyOfRange(3, end)
        val name = try {
            nameBytes.toString(Charsets.UTF_8)
        } catch (_: Exception) {
            String(nameBytes, Charsets.UTF_8)
        }
        return NameInfo(schemeNo, nameLen, pageNo, name, nameBytes)
    }

    data class ChannelInfo(
        val channel: String,
        val flag0: Int,
        val waveform: Int,
        val intensity: String,
        val widthUs: Int,
        val delayS: Double,
        val freqType: Int,
        val fMinHz: Double,
        val fMaxHz: Double,
        val riseMsOrTick: Double,
        val fallMsOrTick: Double,
        val workS: Double,
        val restS: Double,
        val totalMin: Int,
        val modulationWaveform: Int,
        val depthPercent: Int,
        val carrierKhz: Double,
        val diffPeriodS: Int,
        val dynamicPeriodS: Int,
        val contractionS: Int,
        val stimulationS: Int,
        val threshold: Int,
        val reservation: Int
    )

    /** 固定 34B 通道块解码（与你 Python 一致）
     * 除以某个数字是模拟下位机处理
     * */
    private fun decodeChannelBlock34(block: ByteArray): ChannelInfo {
        require(block.size == 34) { "channel block must be 34B, got ${block.size}" }
        val b = block

        val flag0 = b[0].u8()
        val waveform = b[1].u8()
        val intensity = u16be(b[2], b[3]) / 2
        val width = (b[4].u8() shl 16) or (b[5].u8() shl 8) or b[6].u8()

        val delay01s = b[7].u8()
        val delayS = delay01s / 10.0

        val freqType = b[8].u8()
        val fMinRaw = u16be(b[9], b[10])
        val fMaxRaw = u16be(b[11], b[12])
        val fMinHz = fMinRaw / 10.0 //低频生物反馈: 脉冲频率 中频：调制频率
        val fMaxHz = fMaxRaw //差频频率 仅中频干扰有 其余模式需要设置成0

        val rise = u16be(b[13], b[14]) / 2.0
        val fall = u16be(b[15], b[16]) / 2.0

        val workRaw = b[17].u8()
        val restRaw = b[19].u8()
        val workS = workRaw / 2.0
        val restS = restRaw / 2.0

        val totalMin = b[21].u8()

        val modWave = b[22].u8()
        val depth = b[23].u8()

        val carrierRaw = (b[24].u8() shl 16) or (b[25].u8() shl 8) or b[26].u8()
        val carrierKhz = carrierRaw / 10.0
        val diffPeriod = b[27].u8()
        val dynPeriod = b[28].u8()

        val stimulation = b[29].u8()
        val contraction = b[30].u8()

        val threshold = u16be(b[31], b[32])

        val reservation33 = b[33].u8()

        val chBits = flag0 and 0x0F
        val channel = when (chBits) {
            0x01 -> "A"
            0x02 -> "B"
            0x04 -> "C"
            0x08 -> "D"
            else -> "?"
        }

        return ChannelInfo(
            channel = channel,
            flag0 = flag0,
            waveform = waveform,
            intensity = intensity.toString(),
            widthUs = width,
            delayS = delayS,
            freqType = freqType,
            fMinHz = fMinHz,
            fMaxHz = fMaxHz.toDouble(),
            riseMsOrTick = rise,
            fallMsOrTick = fall,
            workS = workS,
            restS = restS,
            totalMin = totalMin,
            modulationWaveform = modWave,
            depthPercent = depth,
            carrierKhz = carrierKhz,

            diffPeriodS = diffPeriod,
            dynamicPeriodS = dynPeriod,
            contractionS = contraction,
            stimulationS = stimulation,
            threshold = threshold,
            reservation = reservation33
        )
    }

    private fun ByteArray.sliceHex(from: Int, to: Int): String {
        if (from < 0 || to >= size || from > to) return ""
        return copyOfRange(from, to + 1).toHex()
    }

    private fun u16be(b0: Byte, b1: Byte): Int = (b0.u8() shl 8) or b1.u8()

    private fun Byte.u8(): Int = toInt() and 0xFF

    private fun Int.toHex2(): String = this.toString(16).uppercase().padStart(2, '0')

    private fun ByteArray.toHex(): String = joinToString(" ") { (it.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0') }
}