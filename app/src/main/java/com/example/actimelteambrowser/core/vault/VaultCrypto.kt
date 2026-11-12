package com.example.actimelteambrowser.core.vault

import android.content.Context
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Kryptografie trezoru hesel. Vsechno lokalne, zadny server.
 *
 *   12 slov + sul --PBKDF2-HMAC-SHA256--> KEK (32 B)
 *   DEK = nahodnych 32 B (tim se sifruji skutecna data)
 *   encryptedDEK = AES-GCM(KEK, DEK)
 *   vault.enc = AES-GCM(DEK, JSON seznam hesel)
 *
 * 12 slov ze seznamu 2048 slov = 132 bitu entropie, takze rychlost odvozeni klice nehraje roli
 * (hrubou silou je nelze zkouset). PBKDF2 je v Androidu bez cizi knihovny a funguje na kazde verzi.
 * VOLAT Z POZADI VLAKNA (odvozeni klice trva chvili).
 */
object VaultCrypto {

    const val WORD_COUNT = 12
    private const val WORDS_IN_LIST = 2048
    private const val PBKDF2_ITERATIONS = 600_000
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val rnd = SecureRandom()

    /** Seznam 2048 slov z res/raw/bip39_words.txt (jedno slovo na radek). */
    @Suppress("DiscouragedApi")   // soubor se hleda podle jmena, aby appka nespadla na chybejicim seznamu
    fun loadWords(context: Context): List<String> {
        val id = context.resources.getIdentifier("bip39_words", "raw", context.packageName)
        if (id == 0) throw IllegalStateException("Chybí soubor res/raw/bip39_words.txt (seznam 2048 slov)")
        val words = context.resources.openRawResource(id).bufferedReader().use { r ->
            r.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        }
        if (words.size != WORDS_IN_LIST) throw IllegalStateException("bip39_words.txt musí mít $WORDS_IN_LIST slov, má ${words.size}")
        return words
    }

    /** 12 nahodnych slov oddelenych mezerou. Nikdy se neuklada. */
    fun generatePhrase(context: Context): String {
        val words = loadWords(context)
        return List(WORD_COUNT) { words[rnd.nextInt(WORDS_IN_LIST)] }.joinToString(" ")
    }

    /** Mala pismena, jedna mezera mezi slovy, bez okrajovych mezer. */
    fun normalize(phrase: String): String =
        phrase.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    /** true = presne 12 slov a vsechna jsou v seznamu. */
    fun isValidPhrase(context: Context, phrase: String): Boolean {
        val parts = normalize(phrase).split(" ")
        if (parts.size != WORD_COUNT) return false
        val set = loadWords(context).toHashSet()
        return parts.all { it in set }
    }

    fun newSalt(): ByteArray = ByteArray(16).also { rnd.nextBytes(it) }

    fun newKey(): ByteArray = ByteArray(32).also { rnd.nextBytes(it) }

    /** KEK z 12 slov: PBKDF2-HMAC-SHA256, jeden blok (32 B = delka hashe). */
    fun deriveKek(phrase: String, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): ByteArray {
        val pw = normalize(phrase).toByteArray(Charsets.UTF_8)
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(pw, "HmacSHA256"))
            mac.update(salt)
            mac.update(byteArrayOf(0, 0, 0, 1))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 2..iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            return t
        } finally {
            pw.fill(0)
        }
    }

    /** AES-256-GCM: vysledek = IV (12 B) + sifrovany text + tag. */
    fun encrypt(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return iv + c.doFinal(plain)
    }

    /** Vyhodi vyjimku, kdyz klic nesedi nebo jsou data poskozena (GCM tag). */
    fun decrypt(key: ByteArray, blob: ByteArray): ByteArray {
        if (blob.size <= IV_BYTES + TAG_BITS / 8) throw IllegalArgumentException("Poškozená data")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, IV_BYTES)))
        return c.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    private const val PW_CHARS = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#\$%^&*-_=+?"

    /** Nahodne heslo (bez zamenitelnych znaku jako 0/O, 1/l/I). */
    fun generatePassword(length: Int = 20): String =
        String(CharArray(length) { PW_CHARS[rnd.nextInt(PW_CHARS.length)] })
}
