package com.example.actimelteambrowser.core.vault

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.actimelteambrowser.core.settings.AppSettings
import com.example.actimelteambrowser.noteError
import com.example.actimelteambrowser.data.VaultEntry
import org.json.JSONArray
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import androidx.annotation.RequiresApi
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Vysledek odemceni PINem: spatny PIN / po 5 pokusech je PIN smazany a je potreba zadat 12 slov. */
enum class PinResult { OK, WRONG, TOO_MANY }

/** Zaloha trezoru pro export / import (nic z toho neni citelne bez 12 slov). */
class VaultBackup(val salt: String, val encDek: String, val data: String)

/**
 * Trezor hesel: jeden zasifrovany soubor filesDir/vault.enc. Hesla nikdy neleží v databazi ani v nastaveni.
 * DEK (klic dat) je v RAM jen kdyz je trezor odemceny; po zamknuti se prepise nulami.
 * Dotazy na odemceni / vytvoreni / obnovu volat z pozadi vlakna (odvozeni klice trva chvili).
 */
object VaultManager {

    private const val FILE = "vault.enc"
    const val PIN_MAX_FAILS = 5
    private const val PIN_ITERATIONS = 100_000   // PIN chrani hlavne Keystore a limit pokusu, ne pocet kol
    private const val KS_ALIAS = "atb_vault_pin"
    private const val AUTO_LOCK_MS = 15 * 60 * 1000L
    private val mutex = Any()
    private var dek: ByteArray? = null
    private val entries = ArrayList<VaultEntry>()

    // ---------------- automaticke zamykani (15 min na pozadi) ----------------

    private val handler = Handler(Looper.getMainLooper())
    private val autoLockTask = Runnable { lock() }
    private var startedActivities = 0
    private var backgroundAt = 0L

    /** Volat z onStart kazde aktivity (pred super.onStart): po 15 min na pozadi se trezor zamkne. */
    fun activityStarted() {
        handler.removeCallbacks(autoLockTask)
        synchronized(mutex) {
            if (startedActivities == 0 && backgroundAt != 0L && SystemClock.elapsedRealtime() - backgroundAt >= AUTO_LOCK_MS) lock()
            startedActivities++
            backgroundAt = 0L
        }
    }

    /** Volat z onStop kazde aktivity: kdyz zadna neni videt, zacne odpocet 15 min. */
    fun activityStopped() {
        synchronized(mutex) {
            if (startedActivities > 0) startedActivities--
            if (startedActivities == 0) {
                backgroundAt = SystemClock.elapsedRealtime()
                if (dek != null) handler.postDelayed(autoLockTask, AUTO_LOCK_MS)
            }
        }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
    private fun file(context: Context) = File(context.filesDir, FILE)

    fun isCreated(context: Context): Boolean {
        AppSettings.init(context)
        return AppSettings.vaultCreated.get() && file(context).isFile
    }

    val isUnlocked: Boolean get() = synchronized(mutex) { dek != null }

    /** Zamkne trezor: klic i hesla v pameti se zahodi. */
    fun lock() = synchronized(mutex) {
        dek?.fill(0)
        dek = null
        entries.clear()
    }

    /** Zalozi novy prazdny trezor s klicem z 12 slov (slova se nikam neukladaji). Trezor zustane odemceny. */
    fun create(context: Context, phrase: String) {
        AppSettings.init(context)
        removePin(context)   // novy trezor = stary PIN by uz nesedel
        removeBio(context)
        val salt = VaultCrypto.newSalt()
        val kek = VaultCrypto.deriveKek(phrase, salt)
        val newDek = VaultCrypto.newKey()
        val encDek = try { VaultCrypto.encrypt(kek, newDek) } finally { kek.fill(0) }
        synchronized(mutex) {
            writeFile(context, VaultCrypto.encrypt(newDek, JSONArray().toString().toByteArray(Charsets.UTF_8)))
            AppSettings.vaultSalt.set(b64(salt))
            AppSettings.vaultEncryptedDek.set(b64(encDek))
            AppSettings.vaultCreated.set(true)
            dek?.fill(0)
            dek = newDek
            entries.clear()
        }
    }

    /** true = 12 slov sedi a trezor je odemceny. */
    fun unlock(context: Context, phrase: String): Boolean {
        if (!isCreated(context)) return false
        return try {
            val kek = VaultCrypto.deriveKek(phrase, unb64(AppSettings.vaultSalt.get()))
            val key = try { VaultCrypto.decrypt(kek, unb64(AppSettings.vaultEncryptedDek.get())) } finally { kek.fill(0) }
            val list = parse(VaultCrypto.decrypt(key, file(context).readBytes()))
            synchronized(mutex) {
                dek?.fill(0)
                dek = key
                entries.clear()
                entries.addAll(list)
            }
            true
        } catch (e: Exception) {
            noteError(e)
            false   // spatna slova nebo poskozeny soubor
        }
    }

    private fun parse(plain: ByteArray): List<VaultEntry> {
        val arr = JSONArray(String(plain, Charsets.UTF_8))
        return List(arr.length()) { VaultEntry.fromJson(arr.getJSONObject(it)) }
    }

    private fun writeFile(context: Context, bytes: ByteArray) {
        val target = file(context)
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw IllegalStateException("Trezor se nepodařilo uložit")
        }
    }

    // Zapise aktualni seznam (volat uvnitr synchronized(mutex) po zmene)
    private fun persist(context: Context) {
        val key = dek ?: throw IllegalStateException("Trezor je zamčený")
        val arr = JSONArray()
        for (e in entries) arr.put(e.toJson())
        writeFile(context, VaultCrypto.encrypt(key, arr.toString().toByteArray(Charsets.UTF_8)))
    }

    private fun requireUnlocked() { if (dek == null) throw IllegalStateException("Trezor je zamčený") }

    /** Vsechna hesla podle domeny (jen v odemcenem stavu, jinak prazdny seznam). */
    fun all(): List<VaultEntry> = synchronized(mutex) {
        if (dek == null) emptyList() else entries.sortedBy { it.domain.lowercase() }
    }

    /** Pridani nebo aktualizace: stejna domena + stejne jmeno = prepise se heslo. Vraci true, kdyz to bylo nove. */
    fun save(context: Context, domain: String, username: String, password: String, note: String = ""): Boolean = synchronized(mutex) {
        requireUnlocked()
        val d = domain.trim().lowercase().removePrefix("www.")
        val i = entries.indexOfFirst { it.domain == d && it.username == username }
        val isNew = i < 0
        if (isNew) {
            val id = (entries.maxOfOrNull { it.id } ?: 0L) + 1
            entries.add(VaultEntry(id, d, username, password, note, System.currentTimeMillis()))
        } else {
            entries[i] = entries[i].copy(password = password, note = if (note.isEmpty()) entries[i].note else note)
        }
        persist(context)
        isNew
    }

    /** Uprava existujiciho zaznamu podle id (zmena domeny, jmena, hesla i poznamky). */
    fun update(context: Context, entry: VaultEntry) = synchronized(mutex) {
        requireUnlocked()
        val i = entries.indexOfFirst { it.id == entry.id }
        if (i >= 0) {
            entries[i] = entry.copy(domain = entry.domain.trim().lowercase().removePrefix("www."))
            persist(context)
        }
    }

    fun remove(context: Context, id: Long) = synchronized(mutex) {
        requireUnlocked()
        if (entries.removeAll { it.id == id }) persist(context)
    }

    /** Vsechny zaznamy pro web (nejnovejsi prvni), kdyz je trezor odemceny. */
    fun findAllForHost(host: String): List<VaultEntry> = synchronized(mutex) {
        if (dek == null) emptyList() else {
            val h = host.lowercase().removePrefix("www.")
            entries.filter { h == it.domain || h.endsWith("." + it.domain) }.sortedByDescending { it.createdAt }
        }
    }

    /** Presne takovy zaznam uz v trezoru je (stejna domena, jmeno i heslo). */
    fun hasExact(host: String, username: String, password: String): Boolean = synchronized(mutex) {
        val h = host.lowercase().removePrefix("www.")
        entries.any { it.domain == h && it.username == username && it.password == password }
    }

    /** Je tam uz zaznam se stejnou domenou a jmenem (ale jinym heslem)? Pak se nabidne aktualizace. */
    fun hasLogin(host: String, username: String): Boolean = synchronized(mutex) {
        val h = host.lowercase().removePrefix("www.")
        entries.any { it.domain == h && it.username == username }
    }

    // ---------------- zaloha (export / import) ----------------

    /** Cast [va] pro textovy export, nebo null, kdyz trezor neexistuje. Je zasifrovana, bez 12 slov nic neprozradi. */
    fun exportSection(context: Context): String? {
        if (!isCreated(context)) return null
        val data = file(context).readBytes()
        return "[va]\nsalt=" + AppSettings.vaultSalt.get() +
            "\ndek=" + AppSettings.vaultEncryptedDek.get() +
            "\ndata=" + b64(data) + "\n"
    }

    /** Najde sekci [va] v textu exportu (null = soubor trezor neobsahuje). */
    fun parseBackup(text: String): VaultBackup? {
        var inVa = false
        var salt = ""; var dekS = ""; var data = ""
        for (raw in text.lineSequence()) {
            val l = raw.trim()
            if (l.startsWith("[") && l.endsWith("]")) { inVa = l == "[va]"; continue }
            if (!inVa) continue
            when {
                l.startsWith("salt=") -> salt = l.removePrefix("salt=")
                l.startsWith("dek=") -> dekS = l.removePrefix("dek=")
                l.startsWith("data=") -> data = l.removePrefix("data=")
            }
        }
        return if (salt.isNotEmpty() && dekS.isNotEmpty() && data.isNotEmpty()) VaultBackup(salt, dekS, data) else null
    }

    /** Obnoví trezor ze zalohy po zadani 12 slov. Vraci pocet hesel, nebo -1 kdyz slova nesedi / soubor je poskozeny. */
    fun restore(context: Context, phrase: String, backup: VaultBackup): Int {
        AppSettings.init(context)
        return try {
            val kek = VaultCrypto.deriveKek(phrase, unb64(backup.salt))
            val key = try { VaultCrypto.decrypt(kek, unb64(backup.encDek)) } finally { kek.fill(0) }
            val blob = unb64(backup.data)
            val list = parse(VaultCrypto.decrypt(key, blob))   // overeni, ze data jdou precist
            synchronized(mutex) {
                writeFile(context, blob)
                AppSettings.vaultSalt.set(backup.salt)
                AppSettings.vaultEncryptedDek.set(backup.encDek)
                AppSettings.vaultCreated.set(true)
                dek?.fill(0)
                dek = key
                entries.clear()
                entries.addAll(list)
            }
            removePin(context)   // PIN patril k puvodnimu trezoru
            removeBio(context)
            list.size
        } catch (e: Exception) {
            noteError(e)
            -1
        }
    }

    // ---------------- PIN (jen v tomto telefonu) ----------------
    // DEK se zabali dvakrat: klicem z PINu a klicem z Android Keystore (z telefonu ho nejde vytahnout).
    // Po 5 spatnych pokusech se PIN smaze. 12 slov zustavaji hlavnim klicem (PIN se nezalohuje).

    private fun ksKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        (ks.getKey(KS_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KS_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun ksEncrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, ksKey())
        return c.iv + c.doFinal(plain)
    }

    private fun ksDecrypt(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, ksKey(), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        return c.doFinal(blob, 12, blob.size - 12)
    }

    fun hasPin(context: Context): Boolean = isCreated(context) && AppSettings.vaultPinBlob.get().isNotEmpty()

    /** Zbyvajici pokusy o PIN. */
    fun pinTriesLeft(): Int = PIN_MAX_FAILS - AppSettings.vaultPinFails.get()

    /** Nastavi (nebo zmeni) PIN; trezor musi byt odemceny. VOLAT Z POZADI VLAKNA. */
    fun setPin(context: Context, pin: String) {
        AppSettings.init(context)
        val key = synchronized(mutex) { dek?.copyOf() } ?: throw IllegalStateException("Trezor je zamčený")
        try {
            val salt = VaultCrypto.newSalt()
            val pinKey = VaultCrypto.deriveKek(pin, salt, PIN_ITERATIONS)
            val inner = try { VaultCrypto.encrypt(pinKey, key) } finally { pinKey.fill(0) }
            AppSettings.vaultPinSalt.set(b64(salt))
            AppSettings.vaultPinBlob.set(b64(ksEncrypt(inner)))
            AppSettings.vaultPinFails.set(0)
        } finally {
            key.fill(0)
        }
    }

    fun removePin(context: Context) {
        AppSettings.init(context)
        AppSettings.vaultPinBlob.set("")
        AppSettings.vaultPinSalt.set("")
        AppSettings.vaultPinFails.set(0)
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KS_ALIAS) }
    }

    /** Odemkne trezor PINem. VOLAT Z POZADI VLAKNA. */
    fun unlockWithPin(context: Context, pin: String): PinResult {
        if (!hasPin(context)) return PinResult.TOO_MANY
        return try {
            val inner = ksDecrypt(unb64(AppSettings.vaultPinBlob.get()))
            val pinKey = VaultCrypto.deriveKek(pin, unb64(AppSettings.vaultPinSalt.get()), PIN_ITERATIONS)
            val key = try { VaultCrypto.decrypt(pinKey, inner) } finally { pinKey.fill(0) }
            val list = parse(VaultCrypto.decrypt(key, file(context).readBytes()))
            synchronized(mutex) {
                dek?.fill(0)
                dek = key
                entries.clear()
                entries.addAll(list)
            }
            AppSettings.vaultPinFails.set(0)
            PinResult.OK
        } catch (e: Exception) {
            noteError(e)
            val fails = AppSettings.vaultPinFails.get() + 1
            if (fails >= PIN_MAX_FAILS) {
                removePin(context)
                PinResult.TOO_MANY
            } else {
                AppSettings.vaultPinFails.set(fails)
                PinResult.WRONG
            }
        }
    }

    // ---------------- otisk prstu (Android 9+) ----------------
    // DEK zabaleny klicem z Keystore, ktery se da pouzit jen po overeni otiskem (CryptoObject).
    // Kdyz se v telefonu prida novy otisk, klic se zneplatni a je potreba PIN / 12 slov.

    private const val BIO_ALIAS = "atb_vault_bio"

    fun hasBio(context: Context): Boolean = isCreated(context) && AppSettings.vaultBioBlob.get().isNotEmpty()

    private fun bioNewKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        runCatching { ks.deleteEntry(BIO_ALIAS) }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(BIO_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return gen.generateKey()
    }

    /** Sifra pro zapnuti otisku (novy klic). Vyhodi vyjimku, kdyz v telefonu zadny otisk neni. Pak authenticate(cipher) a bioFinishEnable. */
    @RequiresApi(28)
    fun bioEnableCipher(): Cipher {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, bioNewKey())
        return c
    }

    /** Po uspesnem overeni otiskem: zabali DEK a ulozi. Trezor musi byt odemceny. */
    fun bioFinishEnable(context: Context, cipher: Cipher) {
        AppSettings.init(context)
        val key = synchronized(mutex) { dek?.copyOf() } ?: throw IllegalStateException("Trezor je zamčený")
        try {
            AppSettings.vaultBioBlob.set(b64(cipher.iv + cipher.doFinal(key)))
        } finally {
            key.fill(0)
        }
    }

    /** Sifra pro odemceni otiskem (vyhodi vyjimku, kdyz je klic zneplatneny). */
    @RequiresApi(28)
    fun bioUnlockCipher(): Cipher {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val k = ks.getKey(BIO_ALIAS, null) as? SecretKey ?: throw IllegalStateException("Klíč otisku neexistuje")
        val blob = unb64(AppSettings.vaultBioBlob.get())
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        return c
    }

    /** Po uspesnem overeni otiskem: odemkne trezor. VOLAT Z POZADI VLAKNA (cte soubor). */
    fun bioFinishUnlock(context: Context, cipher: Cipher): Boolean {
        return try {
            val blob = unb64(AppSettings.vaultBioBlob.get())
            val key = cipher.doFinal(blob, 12, blob.size - 12)
            val list = parse(VaultCrypto.decrypt(key, file(context).readBytes()))
            synchronized(mutex) {
                dek?.fill(0)
                dek = key
                entries.clear()
                entries.addAll(list)
            }
            true
        } catch (e: Exception) {
            noteError(e)
            false
        }
    }

    fun removeBio(context: Context) {
        AppSettings.init(context)
        AppSettings.vaultBioBlob.set("")
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(BIO_ALIAS) }
    }

    // ---------------- kontrola hesel (slabá / opakovaná) ----------------

    private val COMMON_PASSWORDS = setOf(
        "password", "passw0rd", "123456", "1234567", "12345678", "123456789", "1234567890", "qwerty", "qwertyuiop",
        "abc123", "111111", "000000", "123123", "654321", "letmein", "admin", "welcome", "iloveyou", "monkey",
        "dragon", "master", "login", "heslo", "heslo123", "zaq12wsx", "asdfgh", "asdfghjkl", "password1", "qwerty123"
    )

    private fun isWeak(p: String): Boolean {
        if (p.length < 10) return true
        val classes = listOf(p.any { it.isLowerCase() }, p.any { it.isUpperCase() }, p.any { it.isDigit() }, p.any { !it.isLetterOrDigit() }).count { it }
        return classes < 2 || p.lowercase() in COMMON_PASSWORDS || p.toSet().size <= 3
    }

    /** id zaznamu -> text problemu ("slabé", "opakované", nebo oboje). Jen zaznamy s problemem; zamceny trezor = prazdno. */
    fun audit(): Map<Long, String> = synchronized(mutex) {
        if (dek == null) emptyMap() else {
            val repeated = entries.groupBy { it.password }.filter { it.value.size > 1 }.keys
            val out = HashMap<Long, String>()
            for (e in entries) {
                val issues = ArrayList<String>()
                if (isWeak(e.password)) issues.add("slabé")
                if (e.password in repeated) issues.add("opakované")
                if (issues.isNotEmpty()) out[e.id] = issues.joinToString(", ")
            }
            out
        }
    }
}
