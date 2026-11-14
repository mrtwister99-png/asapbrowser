package com.example.actimelteambrowser.core

/** Precte proud, ale nejvys [max] bajtu; vic = chyba "Soubor je příliš velký" (obri soubor se do pameti nenacte cely). */
internal fun readLimited(input: java.io.InputStream, max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        if (out.size() + n > max) throw IllegalArgumentException("Soubor je příliš velký")
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
