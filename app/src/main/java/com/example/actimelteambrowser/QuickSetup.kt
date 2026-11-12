package com.example.actimelteambrowser

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.example.actimelteambrowser.core.settings.AppSettings
import com.example.actimelteambrowser.core.settings.ButtonFunction

/**
 * Po uvodni zprave: dotaz na vychozi prohlizec a pak volba
 *  - Prejit rychle nastaveni = vse v zakladu zlute (zivy tutorial),
 *  - Rychle nastaveni po etapach: 1) domovska stranka + karty po startu, 2) gesta (ukazka + nechat tlacitko?),
 *    3) pridatelna tlacitka (klepnuti / drzeni), 4) bocni lista + pravidlo lista / gesta, 5) export a import.
 *    (Trezor bude dalsi etapa pozdeji.)
 */

// 0 = nic, 2 = ceka se na klepnuti na tlacitko gest, 21 = bezi ukazka gest
internal var quickSetupStage = 0

private fun MainActivity.bodyText(t: String): TextView {
    val tv = TextView(this)
    tv.text = t
    tv.setTextColor(Color.WHITE)
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    tv.setLineSpacing(0f, 1.15f)
    return tv
}

// Okno s textem a tlacitky pod sebou (prvni = hlavni zelene); kazde tlacitko okno zavre a spusti svou akci
private fun MainActivity.choiceDialog(title: String, text: String, extra: View? = null, vararg buttons: Pair<String, () -> Unit>) {
    showStyledDialog(title = title, negativeText = null) { dismiss ->
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.addView(bodyText(text))
        if (extra != null) {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14)
            body.addView(extra, lp)
        }
        buttons.forEachIndexed { i, (label, action) ->
            val b = styledButton(label, i == 0)
            b.setOnClickListener { dismiss(); action() }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(if (i == 0) 18 else 10)
            body.addView(b, lp)
        }
        body
    }
}

// Po "Okay" v uvodni zprave
internal fun MainActivity.afterWelcome() {
    choiceDialog(
        "Výchozí prohlížeč",
        "ASAP si můžete nastavit jako váš výchozí prohlížeč. Odkazy z jiných aplikací se pak otevřou tady. Chcete to?",
        null,
        "Ano" to { requestDefaultBrowser { askQuickSetup() } },
        "Ne" to { askQuickSetup() }
    )
}

// Systemovy dotaz "vychozi prohlizec" (Android 10+), jinak Vychozi aplikace; po navratu pokracuje next
private fun MainActivity.requestDefaultBrowser(next: () -> Unit) {
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val rm = getSystemService(android.app.role.RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
            var launcher: androidx.activity.result.ActivityResultLauncher<Intent>? = null
            launcher = activityResultRegistry.register("asap_default_browser", ActivityResultContracts.StartActivityForResult()) {
                launcher?.unregister()
                window.decorView.postDelayed({ next() }, 300)
            }
            runCatching { launcher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)) }
                .onFailure { launcher.unregister(); next() }
            return
        }
        if (rm != null && rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
            AppToast.show(this, "ASAP už je výchozí prohlížeč")
            next(); return
        }
    }
    runCatching { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    window.decorView.postDelayed({ next() }, 600)
}

private fun MainActivity.askQuickSetup() {
    choiceDialog(
        "Rychlé nastavení",
        "Chceš si v pár krocích projít hlavní možnosti prohlížeče? Nebo to přeskoč – všechno zůstane ve výchozí podobě a nové věci ti ukážou žluté obrysy.",
        null,
        "Projdu si rychlé nastavení možností prohlížeče" to { quickStage1() },
        "Přeskočit rychlé nastavení" to { startLiveTutorial() }
    )
}

// 1) domovska stranka + jak otevrit karty pri startu
private fun MainActivity.quickStage1() {
    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    box.addView(dialogLabel("Domovská stránka"))
    val url = EditText(this)
    url.setText(AppSettings.homeUrl.get())
    url.setTextColor(Color.WHITE)
    url.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    url.isSingleLine = true
    url.inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI or android.text.InputType.TYPE_CLASS_TEXT
    val ubg = GradientDrawable()
    ubg.setColor("#2A2A2A".toColorInt()); ubg.cornerRadius = dp(12).toFloat()
    url.background = ubg
    url.setPadding(dp(12), dp(10), dp(12), dp(10))
    val ulp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    ulp.topMargin = dp(6); ulp.bottomMargin = dp(14)
    box.addView(url, ulp)
    box.addView(dialogLabel("Při startu prohlížeče"))
    var restore = AppSettings.startupRestoreTabs.get()
    val opts = listOf(true to "Obnovit poslední karty", false to "Jedna nová karta s domovskou stránkou")
    val views = ArrayList<TextView>()
    fun paint() {
        for ((i, v) in views.withIndex()) {
            val on = opts[i].first == restore
            val g = GradientDrawable()
            g.setColor((if (on) "#14382A" else "#2A2A2A").toColorInt()); g.cornerRadius = dp(12).toFloat()
            if (on) g.setStroke(dp(2), "#00B055".toColorInt())
            v.background = g
        }
    }
    for ((value, label) in opts) {
        val v = TextView(this)
        v.text = label
        v.setTextColor(Color.WHITE)
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        v.setPadding(dp(14), dp(12), dp(14), dp(12))
        v.setOnClickListener { restore = value; paint() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(6)
        box.addView(v, lp)
        views.add(v)
    }
    paint()
    choiceDialog(
        "1/5  Domovská stránka a karty",
        "Kam se má prohlížeč vracet tlačítkem Domů a co otevřít po spuštění.",
        box,
        "OK" to {
            var u = url.text.toString().trim()
            if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
            if (u.isNotEmpty() && runCatching { u.toUri().host }.getOrNull()?.contains('.') == true) AppSettings.homeUrl.set(u)
            AppSettings.startupRestoreTabs.set(restore)
            quickStage2()
        }
    )
}

// 2) gesta: cela plocha zlute (zivy tutorial), tlacitko gest ceka na klepnuti -> ukazka -> nechat tlacitko?
private fun MainActivity.quickStage2() {
    startLiveTutorial()
    if (gestureButtonN() == 0) { quickStage3(); return }
    quickSetupStage = 2
    AppToast.show(this, "2/5  Gesta: klepni na blikající tlačítko gest", true, AppToast.Kind.INFO)
}

/** Volano z endDemo: skoncila ukazka gest v rychlem nastaveni. */
internal fun MainActivity.onQuickSetupDemoEnded() {
    if (quickSetupStage != 21) return
    quickSetupStage = 0
    window.decorView.postDelayed({
        choiceDialog(
            "Tlačítko gest",
            "Chceš tlačítko pro gesta nechat?",
            null,
            "Ano – tlačítko gest zkusím" to { quickStage3() },
            "✕ – Kdyžtak si ho pak zapnu" to {
                val n = gestureButtonN()
                if (n in 5..8) AppSettings.buttonEnabled[n - 1].set(false)
                else if (n != 0) AppSettings.buttonTap[n - 1].set(ButtonFunction.NONE.key)
                updateButtonVisuals()
                quickStage3()
            }
        )
    }, 400)
}

// 3) pridatelna tlacitka: 2 + 2, kazde umi klepnuti i drzeni
private fun MainActivity.quickStage3() {
    choiceDialog(
        "3/5  Tlačítka",
        "Na každou stranu můžeš přidat až 2 další tlačítka – žluté „+“ nad bočními tlačítky.\n\nKaždé tlačítko umí dvě věci: klepnutí a podržení. Obojí si nastavíš v Nastavení → Tlačítka.",
        null,
        "Dále" to { quickStage4() }
    )
}

// 4) bocni lista + pravidlo lista / gesta (ukazka listy)
private fun MainActivity.quickStage4() {
    choiceDialog(
        "4/5  Boční lišta",
        "Lišta vyjíždí z tlačítka a může být jen na spodních pozicích vlevo nebo vpravo (tlačítko 5 nebo 6).\n\nTlačítko gest může být kdekoli jinde. Lišta a gesta nemůžou být na jednom tlačítku.",
        null,
        "Ukázat lištu" to {
            toggleBarFrom(defaultBarOrigin())
            window.decorView.postDelayed({
                if (leftBarOpen) closeLeftBarAndHide()
                window.decorView.postDelayed({ quickStage5() }, 700)
            }, 3000)
        },
        "Dále" to { quickStage5() }
    )
}

// 5) export a import
private fun MainActivity.quickStage5() {
    choiceDialog(
        "5/5  Záloha nastavení",
        "Veškeré nastavení si uložíš a můžeš zálohovat – nebo ho jednoduše pošleš na e-mail. Pak si ho stáhneš do telefonu a kdyžtak nahraješ zpět.\n\nNajdeš to v Nastavení → Export a Import.",
        null,
        "Hotovo" to { AppToast.show(this, "Rychlé nastavení hotové") }
    )
}
