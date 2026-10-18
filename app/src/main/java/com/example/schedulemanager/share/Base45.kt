package com.example.schedulemanager.share

/**
 * Base45 (RFC 9285): turns bytes into text using only the 45 characters QR codes store most
 * compactly (their "alphanumeric" mode), so binary data costs ~3% extra instead of ~33% with Base64.
 */
object Base45 {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

    fun encode(bytes: ByteArray): String = buildString {
        var i = 0
        while (i + 1 < bytes.size) {
            val n = (bytes[i].toInt() and 0xFF) * 256 + (bytes[i + 1].toInt() and 0xFF)
            append(ALPHABET[n % 45]).append(ALPHABET[n / 45 % 45]).append(ALPHABET[n / 2025])
            i += 2
        }
        if (i < bytes.size) {
            val n = bytes[i].toInt() and 0xFF
            append(ALPHABET[n % 45]).append(ALPHABET[n / 45])
        }
    }

    /** @throws IllegalArgumentException if [text] is not valid Base45 */
    fun decode(text: String): ByteArray {
        require(text.length % 3 != 1) { "Invalid Base45 length" }
        val values = text.map { c -> ALPHABET.indexOf(c).also { require(it >= 0) { "Invalid Base45 character" } } }
        val out = ByteArray(text.length / 3 * 2 + if (text.length % 3 == 2) 1 else 0)
        var o = 0
        for (i in values.indices step 3) {
            if (i + 2 < values.size) {
                val n = values[i] + values[i + 1] * 45 + values[i + 2] * 2025
                require(n <= 0xFFFF) { "Invalid Base45 group" }
                out[o++] = (n shr 8).toByte()
                out[o++] = n.toByte()
            } else {
                val n = values[i] + values[i + 1] * 45
                require(n <= 0xFF) { "Invalid Base45 group" }
                out[o++] = n.toByte()
            }
        }
        return out
    }
}
