package com.example.actimelteambrowser.core.settings

import android.annotation.SuppressLint
import android.content.Context

/**
 * Ochrana odmen: pocet zablokovanych reklam a odemcene vzhledy (Ctverecky, Hvezdicky, Barvy).
 *
 *  - V exportu je chrani kontrolni kod (SettingsCodes, "podpis").
 *  - V telefonu je chrani podpis ulozeny vedle nastaveni: HMAC z hodnot + ID zarizeni, klic je jen v aplikaci.
 *    Kdo by hodnoty prepsal primo v souboru nastaveni (root, zaloha prefs z jineho telefonu), podpis nesedi
 *    a odmeny se pri dalsim spusteni vrati na vychozi stav.
 *
 * Vsechny zapisy techto hodnot musi jit pres tento objekt (jinak by se podpis rozesel).
 */
object RewardGuard {
    private val sig = AppSettings.StringSetting("reward_guard", "")              // podpis v telefonu (neexportuje se)
    private val migrated = AppSettings.BoolSetting("reward_guard_v1", false)     // jednorazove prevzeti hodnot ze starsi verze
    private var device = ""
    private var checked = false
    private val lock = Any()

    // tajny klic je jen v aplikaci (poskladany z casti, aby nebyl v souboru appky jako jeden citelny text)
    private fun key(): ByteArray {
        val a = intArrayOf(0x41, 0x53, 0x41, 0x50, 0x2d, 0x62, 0x72, 0x77)
        val b = intArrayOf(0x7e, 0x13, 0x9c, 0x5a, 0xe1, 0x37, 0x08, 0xd4, 0x6b, 0xa2, 0x3f, 0xc9, 0x51, 0x8e, 0x24, 0xf0)
        val c = intArrayOf(0x92, 0x1d, 0x6e, 0xb7, 0x0c, 0x45, 0xfa, 0x38)
        return (a + b.map { it xor 0x5c } + c.reversed()).map { it.toByte() }.toByteArray()
    }

    /** HMAC-SHA256 zkraceny na [bytes] bajtu (hex). */
    fun hmac(msg: String, bytes: Int = 12): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key(), "HmacSHA256"))
        return mac.doFinal(msg.toByteArray()).take(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun b(v: Boolean) = if (v) 1 else 0

    private fun localSig(): String = hmac(
        "asap-local|$device|b=${AppSettings.totalBlocked.get()}|s=${b(AppSettings.squareStyleOwned.get())}" +
            "|h=${b(AppSettings.starStyleOwned.get())}|c=${b(AppSettings.gestureColorsOwned.get())}", 16
    )

    private fun isDefault() = AppSettings.totalBlocked.get() == 0 && !AppSettings.squareStyleOwned.get() &&
        !AppSettings.starStyleOwned.get() && !AppSettings.gestureColorsOwned.get()

    private fun resign() = sig.set(localSig())

    /** Kontrola pri startu (jednou za beh aplikace). Vola AppSettings.init. */
    @SuppressLint("HardwareIds")   // ID zarizeni jen jako soucast podpisu, nikam se neposila
    internal fun check(context: Context) = synchronized(lock) {
        if (checked) return@synchronized
        checked = true
        device = runCatching {
            android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val saved = sig.get()
        when {
            saved.isNotEmpty() && saved == localSig() -> Unit
            isDefault() -> resign()
            // prvni spusteni teto verze: hodnoty ze starsi verze se prevezmou a podepisou
            saved.isEmpty() && !migrated.get() -> resign()
            else -> {
                // podpis nesedi: hodnoty nekdo prepsal mimo aplikaci -> zpet na vychozi
                AppSettings.totalBlocked.set(0)
                AppSettings.squareStyleOwned.set(false)
                AppSettings.starStyleOwned.set(false)
                AppSettings.gestureColorsOwned.set(false)
                resign()
            }
        }
        migrated.set(true)
    }

    /** Ulozit pocet zablokovanych reklam (z pocitadla AdBlocku). */
    fun setBlocked(n: Int) = synchronized(lock) {
        if (AppSettings.totalBlocked.get() == n) return@synchronized
        AppSettings.totalBlocked.set(n)
        resign()
    }

    /** Odemknout vzhled: koupe za reklamy, tajny kod nebo platny import. */
    fun unlock(square: Boolean = false, star: Boolean = false, colors: Boolean = false) = synchronized(lock) {
        if (square) AppSettings.squareStyleOwned.set(true)
        if (star) AppSettings.starStyleOwned.set(true)
        if (colors) AppSettings.gestureColorsOwned.set(true)
        resign()
    }
}
