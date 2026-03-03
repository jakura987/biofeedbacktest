package com.intellizon.biofeedbacktest.domain

import okio.ByteString

data class TcpPacket(val peer: String, val byteString: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TcpPacket

        if (peer != other.peer) return false
        if (!byteString.contentEquals(other.byteString)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = peer.hashCode()
        result = 31 * result + byteString.contentHashCode()
        return result
    }
}
