package com.example.actimelteambrowser

import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.actimelteambrowser.core.settings.AppSettings
import com.example.actimelteambrowser.core.settings.SettingsCodes
import com.example.actimelteambrowser.core.vault.VaultBackup
import com.example.actimelteambrowser.core.vault.VaultManager
import com.example.actimelteambrowser.ui.VaultRestoreDialog
import com.example.actimelteambrowser.ui.ImportChooserDialog
import com.example.actimelteambrowser.ui.ExportChooserDialog
import com.example.actimelteambrowser.ui.Route
import com.example.actimelteambrowser.ui.SettingsApp
import java.io.File
import java.io.FileNotFoundException
import kotlin.concurrent.thread
import androidx.core.net.toUri

/** Okno Nastavení (Compose): Záložky, Historie, Export, Import. */
class SettingsActivity : ComponentActivity() {

    private var exportParts: Set<String> = setOf("nas", "za", "ka", "hi")   // co uzivatel zaskrtl v dialogu Export

    // Export: systémový dialog "Uložit jako"
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) doExport(uri)
        }

    // Import: systémový výběr souboru
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) doImport(uri)
        }

    // Zaloha jen trezoru pred "Restartovat vse": soubor se zapise a teprve potom se smaze vsechno
    private val vaultKeepLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) {
                AppToast.show(this, "Smazání zrušeno, záloha trezoru se neuložila", true)
            } else {
                thread(name = "vault-keep") {
                    val ok = runCatching {
                        val text = SettingsCodes.export(this, setOf("va"))
                        contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                            ?: throw IllegalStateException("Nelze zapsat do souboru")
                    }.isSuccess
                    runOnUiThread {
                        if (ok) resetEverything() else AppToast.show(this, "Záloha trezoru se nepovedla, nic se nesmazalo", true)
                    }
                }
            }
        }

    // "Restartovat vse": kdyz je zaskrtnuta zaloha trezoru, nejdriv se ulozi do souboru (vyber mista); zrusene ukladani smazani zastavi
    private fun resetWithOptionalVaultBackup(keepVault: Boolean) {
        if (keepVault && VaultManager.isCreated(this)) vaultKeepLauncher.launch("asap-trezor.txt") else resetEverything()
    }

    // true = ukazuje se dialog "Uložit do telefonu / Odeslat na e-mail"
    private var showExportChooser by mutableStateOf(false)
    private var importPending by mutableStateOf<Pair<String, Set<String>>?>(null)   // nacteny text souboru + casti, ktere obsahuje
    private var vaultRestore by mutableStateOf<VaultBackup?>(null)   // trezor ze souboru ceka na 12 slov

    // true = ASAP Browser je vychozi prohlizec (obnovuje se po navratu z nastaveni Androidu)
    private var defaultBrowser by mutableStateOf(false)

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { defaultBrowser = isDefaultBrowser() }

    private fun isDefaultBrowser(): Boolean {
        val probe = Intent(Intent.ACTION_VIEW, "http://www.example.com".toUri())
        val ri = packageManager.resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == packageName
    }

    // Android 10+: systemovy dialog "Nastavit jako vychozi prohlizec"; starsi telefony: Vychozi aplikace v nastaveni
    private fun requestDefaultBrowser() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(android.app.role.RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) &&
                !rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)
            ) {
                roleLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER))
                return
            }
        }
        // nejdriv Vychozi aplikace, kdyz nejdou, obecne Nastaveni; kdyz nejde ani to, toast
        if (runCatching { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }.isFailure &&
            runCatching { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }.isFailure
        ) {
            AppToast.show(this, "Nastavení se nepodařilo otevřít")
        }
    }

    override fun onStart() {
        VaultManager.activityStarted()   // trezor: auto-zamek po 15 min na pozadi
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
        VaultManager.activityStopped()
    }

    override fun onResume() {
        super.onResume()
        defaultBrowser = isDefaultBrowser()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        // obsah nesmi zajizdet pod stavovy radek a navigacni listu (odsazeni resi Compose)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        val startKey = intent.getStringExtra(EXTRA_START)
        val start = when (startKey) {
            START_BOOKMARKS -> Route.Bookmarks
            START_HISTORY -> Route.History
            START_BUTTONS -> Route.Buttons
            START_FEEDBACK -> Route.Feedback
            START_VAULT -> Route.Vault
            else -> Route.Main
        }
        // tlacitka se nastavuji na hlavni plose: okno Nastaveni se hned zavre a hlavni okno prejde do konfiguracniho modu
        if (startKey == START_BUTTONS) {
            openButtonConfig()
            return
        }
        // aktualni stranka z hlavniho okna (tlacitko "pridat sem" ve slozce)
        val pageUrl = intent.getStringExtra(EXTRA_PAGE_URL)
        val pageTitle = intent.getStringExtra(EXTRA_PAGE_TITLE).orEmpty()
        // funkce tlacitka "Export" rovnou ukaze vyber (telefon / e-mail)
        if (startKey == START_EXPORT) showExportChooser = true

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00B055),
                    background = Color(0xFF0A0A0A),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                SettingsApp(
                    startRoute = start,
                    currentUrl = pageUrl,
                    currentTitle = pageTitle,
                    onOpenUrl = { url -> openUrlInBrowser(url) },
                    onExport = { showExportChooser = true },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onResetAll = { keepVault -> resetWithOptionalVaultBackup(keepVault) },
                    onButtonConfig = { openButtonConfig() },
                    onGestureConfig = { openGestureConfig() },
                    isDefaultBrowser = defaultBrowser,
                    onDefaultBrowser = { requestDefaultBrowser() },
                    onClose = { finish() }
                )
                importPending?.let { pending ->
                    ImportChooserDialog(
                        available = pending.second,
                        onImport = { parts, merge -> importPending = null; doImportParts(pending.first, parts, merge) },
                        onDismiss = { importPending = null }
                    )
                }
                vaultRestore?.let { backup ->
                    VaultRestoreDialog(
                        backup = backup,
                        onResult = { msg -> vaultRestore = null; AppToast.show(this, msg, true) },
                        onDismiss = { vaultRestore = null }
                    )
                }
                if (showExportChooser) {
                    ExportChooserDialog(
                        onSave = { parts ->
                            showExportChooser = false
                            exportParts = parts
                            exportLauncher.launch(EXPORT_FILE)
                        },
                        onEmail = { parts ->
                            showExportChooser = false
                            exportParts = parts
                            doExportEmail()
                        },
                        onDismiss = { showExportChooser = false }
                    )
                }
            }
        }

        // funkce tlacitka "Import" rovnou otevre vyber souboru
        if (startKey == START_IMPORT) window.decorView.post { importLauncher.launch(arrayOf("*/*")) }
        // ukazka prvniho tutorialu (Oblibene / Nastaveni / Hodnoceni): obrazovka se jen ukaze, nic se nemeni
        intent.getStringExtra(EXTRA_DEMO)?.let { demo -> DemoMarks.rects.clear(); window.decorView.postDelayed({ runDemo(demo) }, 450) }
    }

    // Konec ukazky: hlavni okno se dozvi, jestli pokracovat (done) nebo ukazku ukoncit (skip)
    private fun finishDemo(result: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_DEMO_RESULT, result))
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    private fun runDemo(demo: String) {
        if (isFinishing) return
        val st = DemoStage(this) { finishDemo(DEMO_SKIP) }
        // zpet behem ukazky = preskocit
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { st.end(); finishDemo(DEMO_SKIP) }
        })
        fun mark(k: String): android.graphics.RectF? = DemoMarks.rects[k]
        val screenH = resources.displayMetrics.heightPixels
        // sipka zpet: prst na ni klepne a ukazka se vrati do prohlizece
        fun goBack() {
            st.at(1300) { st.say("Šipkou zpět se vrátíš do prohlížeče"); mark("back")?.let { st.focus(it); st.moveTo(it, 600) } }
            st.at(900) { st.tap() }
            st.at(350) { st.end(); finishDemo(DEMO_DONE) }
        }
        when (demo) {
            DEMO_BOOKMARKS -> {
                st.at(0) { st.say("Oblíbené: tvoje složky a uložené stránky") }
                st.at(1800) {
                    st.say("+ Nová složka: vytvoříš si vlastní složku")
                    mark("new_folder")?.let { st.moveTo(it, 600); st.blink(it) }
                }
                goBack()
            }
            DEMO_SETTINGS -> {
                st.at(0) { st.say("Nastavení: všechno na jednom místě") }
                // radky se prolinkou zlute shora dolu (jen ty, ktere jsou videt)
                val rows = listOf("Domovská stránka", "Vyhledávač", "Záložky", "Lišta záložek", "Oblíbené", "Stažené položky", "Historie", "Gesta", "Tlačítka", "Trezor hesel", "Animace", "Zvuky", "Vibrace", "Export", "Import", "Tutoriály", "O aplikaci")
                var first = true
                for (r in rows) {
                    st.at(if (first) 900 else 420) {
                        val rect = mark("row:$r") ?: return@at
                        if (rect.bottom < screenH - 16 * resources.displayMetrics.density) st.blink(rect)
                    }
                    first = false
                }
                goBack()
            }
            DEMO_FEEDBACK -> {
                st.at(0) { st.say("Hodnocení a chyby: napiš recenzi nebo nahlas chybu") }
                st.at(1300) { mark("fb_review")?.let { st.moveTo(it, 500); st.blink(it) } }
                st.at(1300) { mark("fb_bug")?.let { st.moveTo(it, 500); st.blink(it) } }
                goBack()
            }
            else -> { st.end(); finishDemo(DEMO_SKIP) }
        }
    }

    // Zavre Nastaveni a pozada hlavni okno o konfiguracni mod gest (plocha + panel shora)
    private fun openGestureConfig() {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_CONFIG_GESTURES, true))
        finish()
    }

    // Zavre Nastaveni a pozada hlavni okno o konfiguracni mod tlacitek
    private fun openButtonConfig() {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_CONFIG_BUTTONS, true))
        finish()
    }

    // Vrátí adresu do hlavního okna. Povoleno jen http/https (ne javascript: apod.)
    private fun openUrlInBrowser(url: String) {
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            AppToast.show(this, "Tuhle adresu nelze otevřít")
            return
        }
        setResult(RESULT_OK, Intent().putExtra(EXTRA_URL, url))
        finish()
    }

    private fun doExport(uri: Uri) {
        thread(name = "export") {
            val msg = try {
                val json = SettingsCodes.export(this, exportParts)   // kratky textovy export s kody
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: throw IllegalStateException("Nelze zapsat do souboru")
                "Export hotový"
            } catch (e: Exception) {
                "Export selhal: " + (e.message ?: "neznámá chyba")
            }
            runOnUiThread { AppToast.show(this, msg, true) }
        }
    }

    // Export na e-mail: soubor se zapise do cache a nabidne se sdileni (Gmail apod. ho pripoji jako prilohu)
    private fun doExportEmail() {
        thread(name = "export-mail") {
            try {
                val json = SettingsCodes.export(this, exportParts)
                val dir = File(cacheDir, ExportFileProvider.DIR)
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }   // stare exporty pryc
                File(dir, EXPORT_FILE).writeText(json, Charsets.UTF_8)
                val uri = ExportFileProvider.uriFor(this, EXPORT_FILE)
                val subject = "ASAP Browser – moje nastavení"
                val body = "Soubor otevři v ASAP Browseru: Nastavení → Import."
                // 1) rovnou e-mailova aplikace: novy e-mail s prilohou, uzivatel jen vyplni svou adresu a odesle
                val mail = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
                mail.putExtra(Intent.EXTRA_STREAM, uri)
                mail.putExtra(Intent.EXTRA_SUBJECT, subject)
                mail.putExtra(Intent.EXTRA_TEXT, body)
                mail.clipData = ClipData.newRawUri("", uri)
                mail.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // 2) zaloha: obecne sdileni, kdyby e-mailova aplikace prilohu neprijala
                val send = Intent(Intent.ACTION_SEND)
                send.type = "text/plain"
                send.putExtra(Intent.EXTRA_STREAM, uri)
                send.putExtra(Intent.EXTRA_SUBJECT, subject)
                send.putExtra(Intent.EXTRA_TEXT, body)
                send.clipData = ClipData.newRawUri("", uri)
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runOnUiThread {
                    if (runCatching { startActivity(mail) }.isFailure &&
                        runCatching { startActivity(Intent.createChooser(send, "Odeslat nastavení")) }.isFailure
                    ) {
                        AppToast.show(this, "Nenašla se aplikace pro odeslání", true)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AppToast.show(this, "Export selhal: " + (e.message ?: "neznámá chyba"), true)
                }
            }
        }
    }

    // Smaze VSECHNA data appky (nastaveni, databaze, soubory, cache, cookies) a appku ukonci.
    // Pri dalsim spusteni je appka jako nova. Stazene soubory ve slozce Stazene zustavaji.
    private fun resetEverything() {
        val ok = runCatching {
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).clearApplicationUserData()
        }.getOrDefault(false)
        if (!ok) AppToast.show(this, "Smazání se nepovedlo", true)
    }

    // Soubor s kody: nejdriv se ukaze vyber casti (dialog), starsi JSON se nacte cely hned
    private fun doImport(uri: Uri) {
        thread(name = "import") {
            val msg: String? = try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Nelze číst soubor")
                if (bytes.size > MAX_IMPORT_BYTES) throw IllegalArgumentException("Soubor je příliš velký")
                val t = String(bytes, Charsets.UTF_8)
                if (SettingsCodes.looksLikeCodes(t)) {
                    val avail = SettingsCodes.sections(t)
                    if (avail.isEmpty()) throw IllegalArgumentException("V souboru není nic k načtení")
                    runOnUiThread { importPending = Pair(t, avail) }
                    null
                } else throw IllegalArgumentException("Neplatný soubor")
            } catch (e: Exception) {
                "Import selhal: " + (e.message ?: "neznámá chyba")
            }
            if (msg != null) runOnUiThread { AppToast.show(this, msg, true) }
        }
    }

    // Nacte jen vybrane casti textoveho souboru
    private fun doImportParts(text: String, parts: Set<String>, merge: Set<String>) {
        // trezor se nacita zvlast: nejdriv se zepta na 12 slov
        val rest = parts - "va"
        if ("va" in parts) VaultManager.parseBackup(text)?.let { vaultRestore = it }
        if (rest.isEmpty()) return
        thread(name = "import-parts") {
            val msg = try {
                SettingsCodes.import(this, text, rest, merge)
            } catch (e: Exception) {
                "Import selhal: " + (e.message ?: "neznámá chyba")
            }
            runOnUiThread { AppToast.show(this, msg, true) }
        }
    }

    companion object {
        const val EXTRA_START = "start"
        const val START_BOOKMARKS = "bookmarks"
        const val START_HISTORY = "history"
        const val START_BUTTONS = "buttons"
        const val START_EXPORT = "export"
        const val START_IMPORT = "import"
        const val START_FEEDBACK = "feedback"
        const val START_VAULT = "vault"
        const val EXTRA_URL = "url"
        const val EXTRA_DEMO = "demo"                 // ukazka prvniho tutorialu (jen se ukaze, nic se nemeni)
        const val EXTRA_DEMO_RESULT = "demo_result"
        const val DEMO_BOOKMARKS = "bookmarks"
        const val DEMO_SETTINGS = "settings"
        const val DEMO_FEEDBACK = "feedback"
        const val DEMO_DONE = "done"
        const val DEMO_SKIP = "skip"
        const val EXTRA_CONFIG_BUTTONS = "config_buttons"
        const val EXTRA_CONFIG_GESTURES = "config_gestures"
        const val EXTRA_PAGE_URL = "page_url"
        const val EXTRA_PAGE_TITLE = "page_title"
        private const val MAX_IMPORT_BYTES = 50 * 1024 * 1024
        const val EXPORT_FILE = "asap-browser-nastaveni.txt"
    }
}

/**
 * Pomocník pro sdílení souborů z cache (export na e-mail, screenshot na starších Androidech).
 * Dává ven JEN čtení souborů ze složky cache/exports; není exportovaný, přístup dostává
 * jen aplikace, které uživatel vybere ve sdílení (grantUriPermissions).
 */
class ExportFileProvider : ContentProvider() {

    private fun fileFor(uri: Uri): File? {
        val name = uri.lastPathSegment ?: return null
        if (name.contains('/') || name.contains("..")) return null
        val f = File(File(context?.cacheDir ?: return null, DIR), name)
        return if (f.isFile) f else null
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Jen ke čtení")
        val f = fileFor(uri) ?: throw FileNotFoundException()
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        val f = fileFor(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols)
        cursor.addRow(Array<Any?>(cols.size) { i ->
            when (cols[i]) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        })
        return cursor
    }

    override fun getType(uri: Uri): String {
        val n = uri.lastPathSegment.orEmpty().lowercase()
        return when {
            n.endsWith(".png") -> "image/png"
            n.endsWith(".gif") -> "image/gif"
            n.endsWith(".webp") -> "image/webp"
            n.endsWith(".jpg") || n.endsWith(".jpeg") -> "image/jpeg"
            n.endsWith(".txt") -> "text/plain"
            else -> "application/json"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val DIR = "exports"
        fun uriFor(context: android.content.Context, fileName: String): Uri =
            Uri.Builder().scheme("content").authority(context.packageName + ".exports").appendPath(fileName).build()
    }
}
