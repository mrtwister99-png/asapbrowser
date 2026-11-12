package com.example.actimelteambrowser

import android.graphics.Color
import android.os.SystemClock
import android.util.TypedValue
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.actimelteambrowser.data.VaultEntry
import androidx.core.net.toUri
import com.example.actimelteambrowser.core.vault.VaultManager
import org.json.JSONObject
import kotlin.concurrent.thread

// Trezor hesel v prohlizeci: nabidka "Ulozit heslo?" po odeslani prihlaseni a vyplneni hesla na znamych webech.
// Funguje jen na https strankach, v bezne (ne anonymni) karte a kdyz je trezor odemceny.
// Hesla se do stranky vkladaji jen z Kotlinu (evaluateJavascript); JS ve strance si zadne heslo vyzadat nemuze.

/** Ve strance zachyti odeslani formulare s heslem a preda jmeno + heslo mostu AtbVault (adresu si bere appka sama). */
private const val VAULT_SAVE_JS = """
(function () {
  if (window.__atbVault) return;
  window.__atbVault = 1;
  function grab(form) {
    try {
      var pws = [];
      var ps = form.querySelectorAll('input[type=password]');
      for (var k = 0; k < ps.length; k++) if (ps[k].value) pws.push(ps[k].value);
      if (!pws.length) return;
      var user = '';
      var all = form.querySelectorAll('input');
      for (var i = 0; i < all.length; i++) {
        if (all[i] === ps[0]) break;
        var t = (all[i].type || 'text').toLowerCase();
        if ((t === 'text' || t === 'email' || t === 'tel') && all[i].value) user = all[i].value;
      }
      var n = pws.length;
      if (n === 1) { AtbVault.submit(user, pws[0]); return; }
      // vic poli hesla: 3 = stare+nove+potvrzeni, 2 = nove+potvrzeni (shodna) nebo stare+nove
      var np = n >= 3 ? pws[1] : (pws[0] === pws[1] ? pws[0] : pws[1]);
      if (n >= 3 && pws[1] !== pws[2]) return;       // potvrzeni nesedi, neodeslano
      if (n === 2 && pws[0] === pws[1] && user) { AtbVault.submit(user, np); return; }   // registrace
      AtbVault.change(np);                           // zmena hesla
    } catch (e) {}
  }
  // uzivatel klepl do pole prihlaseni: az ted se nabidne vyplneni (jen opravdove klepnuti, ne skript)
  document.addEventListener('focusin', function (e) {
    var el = e.target;
    if (!e.isTrusted || !el || el.tagName !== 'INPUT') return;
    var t = (el.type || 'text').toLowerCase();
    if (t === 'password' || t === 'text' || t === 'email' || t === 'tel') AtbVault.focus();
  }, true);
  document.addEventListener('submit', function (e) { if (e.target && e.target.querySelector) grab(e.target); }, true);
  document.addEventListener('click', function (e) {
    var b = e.target && e.target.closest ? e.target.closest('button, input[type=submit]') : null;
    if (b && b.form) grab(b.form);
  }, true);
})();
"""

/** Vyplni prazdne pole hesla (a jmeno pred nim). Volani: (FILL)(jmeno, heslo). */
private const val VAULT_FILL_JS = """
(function (u, p) {
  var pw = document.querySelector('input[type=password]');
  if (!pw || pw.value) return;
  function put(el, v) {
    var d = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
    d.set.call(el, v);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }
  var usr = null;
  var all = document.querySelectorAll('input');
  for (var i = 0; i < all.length; i++) {
    if (all[i] === pw) break;
    var t = (all[i].type || 'text').toLowerCase();
    if (t === 'text' || t === 'email' || t === 'tel') usr = all[i];
  }
  if (usr && u && !usr.value) put(usr, u);
  put(pw, p);
})
"""

private var lastVaultKey = ""
private var lastVaultAt = 0L
private var lastLockedToastAt = 0L
private var vaultPickerShowing = false
private val vaultChoice = HashMap<String, Long>()   // web -> id zvoleneho uctu (jen v RAM)

/** Most pro JS: stranka muze jen nahlasit, ze se odeslalo prihlaseni. Nic necte. */
class VaultBridge(private val act: MainActivity, private val tab: BrowserTab) {
    @JavascriptInterface
    fun submit(user: String, pass: String) {
        if (pass.isEmpty() || user.length > 300 || pass.length > 300) return
        act.runOnUiThread { act.onVaultSubmit(tab, user, pass) }
    }

    /** Uzivatel klepl do pole prihlaseni (hesla nevraci, jen spusti nabidku vyplneni). */
    @JavascriptInterface
    fun focus() { act.runOnUiThread { act.onVaultFocus(tab) } }

    /** Formular zmeny hesla: stranka nahlasi jen nove heslo. */
    @JavascriptInterface
    fun change(newPass: String) {
        if (newPass.isEmpty() || newPass.length > 300) return
        act.runOnUiThread { act.onVaultChange(tab, newPass) }
    }
}

// Po nacteni stranky: nahodit zachytavani prihlaseni a vyplnit heslo, kdyz ho trezor zna
internal fun MainActivity.vaultOnPageFinished(tab: BrowserTab, view: WebView?, url: String?) {
    if (view == null || url == null || tab.incognito || !url.startsWith("https://")) return
    if (!VaultManager.isCreated(this)) return
    view.evaluateJavascript(VAULT_SAVE_JS, null)
    if (!VaultManager.isUnlocked) vaultChoice.clear()
}

// Klepnuti do pole prihlaseni: vyplnit (1 ucet), pouzit zapamatovany vyber, nebo nechat vybrat
internal fun MainActivity.onVaultFocus(tab: BrowserTab) {
    val view = tab.webView
    val url = view.url ?: return
    if (tab.incognito || !url.startsWith("https://") || !VaultManager.isUnlocked) return
    val host = runCatching { url.toUri().host }.getOrNull() ?: return
    val list = VaultManager.findAllForHost(host)
    if (list.isEmpty()) return
    if (list.size == 1) { vaultFill(view, list[0]); return }
    val remembered = list.firstOrNull { it.id == vaultChoice[host] }
    if (remembered != null) { vaultFill(view, remembered); return }
    // nabidku ukazat jen kdyz je na strance prazdne pole hesla
    view.evaluateJavascript("(function(){var p=document.querySelector('input[type=password]');return !!p && !p.value;})()") { r ->
        if (r == "true") vaultPick(view, host, list)
    }
}

/** Vlozi jmeno + heslo do stranky (JS nic neprepise, kdyz je pole hesla uz vyplnene). */
private fun vaultFill(view: WebView, e: VaultEntry) {
    view.evaluateJavascript("(" + VAULT_FILL_JS + ")(" + JSONObject.quote(e.username) + "," + JSONObject.quote(e.password) + ")", null)
}

/** Dialog "Vyber ucet" pro web s vice ulozenymi prihlasenimi. */
private fun MainActivity.vaultPick(view: WebView, host: String, list: List<VaultEntry>) {
    if (vaultPickerShowing) return
    vaultPickerShowing = true
    showStyledDialog(title = "Vyber účet pro $host", positiveText = null, negativeText = "Nevyplňovat") { dismiss ->
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        list.forEach { e ->
            val tv = TextView(this)
            tv.text = e.username.ifEmpty { "(bez jména)" }
            tv.setTextColor(Color.WHITE)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            tv.setPadding(0, 28, 0, 28)
            tv.setOnClickListener {
                vaultChoice[host] = e.id   // v teto relaci se uz neptat
                dismiss()
                vaultFill(view, e)
            }
            box.addView(tv)
        }
        box
    }
    // flag shodit az po zavreni (i pri "Nevyplnovat"): kratke zpozdeni brani opakovanemu dotazu pri dalsim nacteni
    view.postDelayed({ vaultPickerShowing = false }, 4000)
}

// Odeslano prihlaseni: nabidnout ulozeni / aktualizaci (adresa je z karty, ne ze stranky)
internal fun MainActivity.onVaultSubmit(tab: BrowserTab, user: String, pass: String) {
    if (tab.incognito) return
    val url = tab.webView.url ?: return
    if (!url.startsWith("https://")) return
    val host = runCatching { url.toUri().host }.getOrNull()?.removePrefix("www.")?.lowercase() ?: return
    if (!VaultManager.isCreated(this)) return

    val now = SystemClock.uptimeMillis()
    val key = "$host|$user|${pass.hashCode()}"
    if (key == lastVaultKey && now - lastVaultAt < 5000) return   // submit + klepnuti na tlacitko = jedno prihlaseni
    lastVaultKey = key
    lastVaultAt = now

    if (!VaultManager.isUnlocked) {
        if (now - lastLockedToastAt > 60_000) {
            lastLockedToastAt = now
            AppToast.show(this, "Trezor je zamčený: odemkni ho v Nastavení → Trezor hesel")
        }
        return
    }
    if (VaultManager.hasExact(host, user, pass)) return
    val update = VaultManager.hasLogin(host, user)
    showStyledDialog(
        title = if (update) "Aktualizovat heslo?" else "Uložit heslo?",
        positiveText = if (update) "Aktualizovat" else "Uložit",
        onPositive = {
            thread(name = "vault-save") {
                val ok = runCatching { VaultManager.save(this, host, user, pass) }.isSuccess
                runOnUiThread { AppToast.show(this, if (ok) "Uloženo do trezoru" else "Uložení se nepovedlo", !ok) }
            }
            true
        },
        negativeText = "Ne"
    ) { _ ->
        val tv = TextView(this)
        tv.text = if (update) "Změnit uložené heslo pro $host ($user)?"
        else "Uložit přihlášení pro $host" + (if (user.isNotEmpty()) " ($user)" else "") + " do trezoru?"
        tv.setTextColor(Color.WHITE)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        tv
    }
}

// Formular zmeny hesla: nabidnout aktualizaci uctu na tomto webu (jen kdyz web v trezoru uz je)
internal fun MainActivity.onVaultChange(tab: BrowserTab, newPass: String) {
    if (tab.incognito || !VaultManager.isUnlocked) return
    val url = tab.webView.url ?: return
    if (!url.startsWith("https://")) return
    val host = runCatching { url.toUri().host }.getOrNull()?.removePrefix("www.")?.lowercase() ?: return
    val list = VaultManager.findAllForHost(host)
    if (list.isEmpty() || list.any { it.password == newPass }) return
    val now = SystemClock.uptimeMillis()
    val key = "chg|$host|${newPass.hashCode()}"
    if (key == lastVaultKey && now - lastVaultAt < 5000) return   // submit + klepnuti na tlacitko = jedna zmena
    lastVaultKey = key
    lastVaultAt = now

    fun applyNew(e: VaultEntry) {
        thread(name = "vault-change") {
            val ok = runCatching { VaultManager.update(this, e.copy(password = newPass)) }.isSuccess
            runOnUiThread { AppToast.show(this, if (ok) "Heslo v trezoru aktualizováno" else "Aktualizace se nepovedla", !ok) }
        }
    }
    if (list.size == 1) {
        showStyledDialog(title = "Aktualizovat heslo?", positiveText = "Aktualizovat", onPositive = { applyNew(list[0]); true }, negativeText = "Ne") { _ ->
            val tv = TextView(this)
            tv.text = "Změnit uložené heslo pro $host" + (if (list[0].username.isNotEmpty()) " (${list[0].username})" else "") + "?"
            tv.setTextColor(Color.WHITE)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            tv
        }
    } else {
        // vic uctu na webu: uzivatel vybere, kteremu se heslo meni
        showStyledDialog(title = "Který účet změnit?", positiveText = null, negativeText = "Ne") { dismiss ->
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            list.forEach { e ->
                val tv = TextView(this)
                tv.text = e.username.ifEmpty { "(bez jména)" }
                tv.setTextColor(Color.WHITE)
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                tv.setPadding(0, 28, 0, 28)
                tv.setOnClickListener { dismiss(); applyNew(e) }
                box.addView(tv)
            }
            box
        }
    }
}
