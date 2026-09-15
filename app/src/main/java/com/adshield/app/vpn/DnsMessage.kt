package com.adshield.app.vpn

/** Parses outgoing DNS queries and builds the replies AdShield answers with itself. */
object DnsMessage {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3

    data class Question(
        val id: Int,
        val domain: String,
        val qType: Int,
        val qClass: Int,
        val questionEnd: Int
    )

    /** Returns the question of a standard single-question query, or null if it is not one. */
    fun parseQuery(message: ByteArray): Question? {
        if (message.size < 12) return null
        val flags = Net.u16(message, 2)
        if ((flags and 0x8000) != 0) return null
        if (((flags shr 11) and 0x0F) != 0) return null
        val questionCount = Net.u16(message, 4)
        if (questionCount < 1) return null

        var offset = 12
        val labels = ArrayList<String>(4)
        while (offset < message.size) {
            val labelLength = message[offset].toInt() and 0xFF
            if (labelLength == 0) {
                offset += 1
                break
            }
            if ((labelLength and 0xC0) != 0) return null
            if (labelLength > 63 || offset + 1 + labelLength > message.size) return null
            labels += String(message, offset + 1, labelLength, Charsets.US_ASCII)
            offset += 1 + labelLength
            if (offset > message.size) return null
        }
        if (labels.isEmpty()) return null
        if (offset + 4 > message.size) return null
        val qType = Net.u16(message, offset)
        val qClass = Net.u16(message, offset + 2)
        offset += 4
        return Question(
            id = Net.u16(message, 0),
            domain = labels.joinToString(".").lowercase(),
            qType = qType,
            qClass = qClass,
            questionEnd = offset
        )
    }

    /** NXDOMAIN: the fastest way to make a client give up on a blocked name. */
    fun nxdomain(query: ByteArray, question: Question?): ByteArray =
        errorResponse(query, question, RCODE_NXDOMAIN)

    /** SERVFAIL: used when the upstream resolver could not answer in time. */
    fun servfail(query: ByteArray, question: Question?): ByteArray =
        errorResponse(query, question, RCODE_SERVFAIL)

    private fun errorResponse(query: ByteArray, question: Question?, rcode: Int): ByteArray {
        val end = when {
            question != null && question.questionEnd <= query.size -> question.questionEnd
            query.size >= 12 -> 12
            else -> query.size
        }
        val response = query.copyOf(end)
        if (response.size < 12) return response
        val requestFlags = Net.u16(query, 2)
        val flags = 0x8000 or (requestFlags and 0x0100) or 0x0080 or (rcode and 0x0F)
        Net.put16(response, 2, flags)
        Net.put16(response, 4, if (question != null) 1 else 0)
        Net.put16(response, 6, 0)
        Net.put16(response, 8, 0)
        Net.put16(response, 10, 0)
        return response
    }

    fun responseMatches(queryId: Int, response: ByteArray): Boolean {
        if (response.size < 12) return false
        return Net.u16(response, 0) == queryId
    }
}
