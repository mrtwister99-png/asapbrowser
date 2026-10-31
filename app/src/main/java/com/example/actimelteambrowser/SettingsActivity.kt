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
import android.widget.Toast
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
import com.example.actimelteambrowser.core.settings.BackupManager
import com.example.actimelteambrowser.ui.ExportChooserDialog
import com.example.actimelteambrowser.ui.Route
import com.example.actimelteambrowser.ui.SettingsApp
import java.io.File
import java.io.FileNotFoundException
import kotlin.concurrent.thread

/** Okno Nastavení (Compose): Záložky, Historie, Export, Import. */
class SettingsActivity : ComponentActivity() {

    // Export: systémový dialog "Uložit jako"
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) doExport(uri)
        }

    // Import: systémový výběr souboru
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) doImport(uri)
        }

    // true = ukazuje se dialog "Uložit do telefonu / Odeslat na e-mail"
    private var showExportChooser by mutableStateOf(false)

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
                    onResetAll = { resetEverything() },
                    onButtonConfig = { openButtonConfig() },
                    onGestureConfig = { openGestureConfig() },
                    onClose = { finish() }
                )
                if (showExportChooser) {
                    ExportChooserDialog(
                        onSave = {
                            showExportChooser = false
                            exportLauncher.launch(EXPORT_FILE)
                        },
                        onEmail = {
                            showExportChooser = false
                            doExportEmail()
                        },
                        onDismiss = { showExportChooser = false }
                    )
                }
            }
        }

        // funkce tlacitka "Import" rovnou otevre vyber souboru
        if (startKey == START_IMPORT) window.decorView.post { importLauncher.launch(arrayOf("*/*")) }
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
            Toast.makeText(this, "Tuhle adresu nelze otevřít", Toast.LENGTH_SHORT).show()
            return
        }
        setResult(RESULT_OK, Intent().putExtra(EXTRA_URL, url))
        finish()
    }

    private fun doExport(uri: Uri) {
        thread(name = "export") {
            val msg = try {
                val json = BackupManager.exportJson(this)
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: throw IllegalStateException("Nelze zapsat do souboru")
                "Export hotový"
            } catch (e: Exception) {
                "Export selhal: " + (e.message ?: "neznámá chyba")
            }
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }
    }

    // Export na e-mail: soubor se zapise do cache a nabidne se sdileni (Gmail apod. ho pripoji jako prilohu)
    private fun doExportEmail() {
        thread(name = "export-mail") {
            try {
                val json = BackupManager.exportJson(this)
                val dir = File(cacheDir, ExportFileProvider.DIR)
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }   // stare exporty pryc
                File(dir, EXPORT_FILE).writeText(json, Charsets.UTF_8)
                val uri = ExportFileProvider.uriFor(this, EXPORT_FILE)
                val subject = "ASAP Browser – moje nastavení"
                val body = "Soubor otevři v ASAP Browseru: Nastavení → Import."
                // 1) rovnou e-mailova aplikace: novy e-mail s prilohou, uzivatel jen vyplni svou adresu a odesle
                val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                mail.putExtra(Intent.EXTRA_STREAM, uri)
                mail.putExtra(Intent.EXTRA_SUBJECT, subject)
                mail.putExtra(Intent.EXTRA_TEXT, body)
                mail.clipData = ClipData.newRawUri("", uri)
                mail.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // 2) zaloha: obecne sdileni, kdyby e-mailova aplikace prilohu neprijala
                val send = Intent(Intent.ACTION_SEND)
                send.type = "application/json"
                send.putExtra(Intent.EXTRA_STREAM, uri)
                send.putExtra(Intent.EXTRA_SUBJECT, subject)
                send.putExtra(Intent.EXTRA_TEXT, body)
                send.clipData = ClipData.newRawUri("", uri)
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runOnUiThread {
                    try {
                        startActivity(mail)
                    } catch (e: Exception) {
                        try {
                            startActivity(Intent.createChooser(send, "Odeslat nastavení"))
                        } catch (e2: Exception) {
                            Toast.makeText(this, "Nenašla se aplikace pro odeslání", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Export selhal: " + (e.message ?: "neznámá chyba"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Smaze VSECHNA data appky (nastaveni, databaze, soubory, cache, cookies) a appku ukonci.
    // Pri dalsim spusteni je appka jako nova. Stazene soubory ve slozce Stazene zustavaji.
    private fun resetEverything() {
        val ok = try {
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).clearApplicationUserData()
        } catch (e: Exception) {
            false
        }
        if (!ok) Toast.makeText(this, "Smazání se nepovedlo", Toast.LENGTH_LONG).show()
    }

    private fun doImport(uri: Uri) {
        thread(name = "import") {
            val msg = try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Nelze číst soubor")
                if (bytes.size > MAX_IMPORT_BYTES) throw IllegalArgumentException("Soubor je příliš velký")
                BackupManager.importJson(this, String(bytes, Charsets.UTF_8))
            } catch (e: Exception) {
                "Import selhal: " + (e.message ?: "neznámá chyba")
            }
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
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
        const val EXTRA_URL = "url"
        const val EXTRA_CONFIG_BUTTONS = "config_buttons"
        const val EXTRA_CONFIG_GESTURES = "config_gestures"
        const val EXTRA_PAGE_URL = "page_url"
        const val EXTRA_PAGE_TITLE = "page_title"
        private const val MAX_IMPORT_BYTES = 50 * 1024 * 1024
        const val EXPORT_FILE = "asap-browser-nastaveni.json"
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
