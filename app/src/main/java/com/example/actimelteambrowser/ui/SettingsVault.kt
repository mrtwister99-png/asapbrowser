package com.example.actimelteambrowser.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import javax.crypto.Cipher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.actimelteambrowser.AppToast
import com.example.actimelteambrowser.core.vault.PinResult
import com.example.actimelteambrowser.core.vault.VaultBackup
import com.example.actimelteambrowser.core.vault.VaultCrypto
import com.example.actimelteambrowser.core.vault.VaultManager
import com.example.actimelteambrowser.data.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Trezor hesel: zalozeni (12 slov + kontrola), odemknuti, seznam, detail, pridani / uprava, obnova ze zalohy.
// Hesla jsou jen v zasifrovanem souboru; po odemceni jsou v pameti, po zamknuti zmizi.

private val V_PANEL = Color(0xFF1E1E1E)
private val V_ROW = Color(0xFF2A2A2A)
private val V_ACCENT = Color(0xFF00B055)
private val V_ON_ACCENT = Color(0xFF06281D)
private val V_MUTED = Color(0xFF888888)
private val V_DANGER = Color(0xFFB91C1C)

private enum class VaultStage { INTRO, WORDS, CHECK, LOCKED, LIST }

@Composable
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    // 12 slov a hesla nejdou nafotit ani zaznamenat obrazovku
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var stage by remember {
        mutableStateOf(
            if (!VaultManager.isCreated(context)) VaultStage.INTRO
            else if (VaultManager.isUnlocked) VaultStage.LIST
            else VaultStage.LOCKED
        )
    }
    // po automatickem zamknuti (15 min na pozadi) se z odemceneho seznamu vrati na zamek
    DisposableEffect(Unit) {
        val lc = (context as? androidx.activity.ComponentActivity)?.lifecycle
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_START && stage == VaultStage.LIST && !VaultManager.isUnlocked) stage = VaultStage.LOCKED
        }
        lc?.addObserver(obs)
        onDispose { lc?.removeObserver(obs) }
    }
    var phrase by remember { mutableStateOf("") }
    var introError by remember { mutableStateOf<String?>(null) }

    when (stage) {
        VaultStage.INTRO -> VaultIntro(
            error = introError,
            onBack = onBack,
            onStart = {
                try {
                    phrase = VaultCrypto.generatePhrase(context)
                    introError = null
                    stage = VaultStage.WORDS
                } catch (e: Exception) {
                    introError = e.message ?: "Nepodařilo se vytvořit slova"
                }
            }
        )
        VaultStage.WORDS -> VaultWords(phrase, onBack = { phrase = ""; stage = VaultStage.INTRO }, onNext = { stage = VaultStage.CHECK })
        VaultStage.CHECK -> VaultCheck(phrase, onBack = { stage = VaultStage.WORDS }, onDone = { phrase = ""; stage = VaultStage.LIST })
        VaultStage.LOCKED -> VaultLocked(onBack = onBack, onUnlocked = { stage = VaultStage.LIST })
        VaultStage.LIST -> VaultList(onBack = onBack, onLock = { VaultManager.lock(); stage = VaultStage.LOCKED })
    }
}

@Composable
private fun VaultIntro(error: String?, onBack: () -> Unit, onStart: () -> Unit) {
    ScreenScaffold(title = "Trezor hesel", onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Hesla se ukládají jen v tomto telefonu a jsou zašifrovaná. Klíčem je 12 slov, která ti za chvíli ukážu. " +
                    "Opiš si je na papír. Bez nich se k heslům nedostane nikdo, ani ty.",
                color = Color(0xFFCCCCCC), fontSize = 15.sp
            )
            if (error != null) Text(error, color = Color(0xFFEF5350), fontSize = 14.sp)
            BigButton("Vytvořit trezor", V_ACCENT, V_ON_ACCENT, onClick = onStart)
        }
    }
}

@Composable
private fun VaultWords(phrase: String, onBack: () -> Unit, onNext: () -> Unit) {
    val words = phrase.split(" ")
    ScreenScaffold(title = "Tvých 12 slov", onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Opiš si je na papír ve správném pořadí. Nikde se neukládají a nejdou obnovit. Bez nich o hesla přijdeš.",
                color = V_MUTED, fontSize = 14.sp
            )
            for (i in words.indices step 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (k in i..minOf(i + 1, words.size - 1)) {
                        Text(
                            "${k + 1}. ${words[k]}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(V_PANEL).padding(horizontal = 14.dp, vertical = 14.dp)
                        )
                    }
                }
            }
            BigButton("Opsal jsem na papír", V_ACCENT, V_ON_ACCENT, onClick = onNext)
        }
    }
}

@Composable
private fun VaultCheck(phrase: String, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val words = phrase.split(" ")
    val n = remember { (1..VaultCrypto.WORD_COUNT).random() }
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    ScreenScaffold(title = "Kontrola", onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Jaké bylo slovo č. $n?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it; error = null },
                label = { Text("Slovo č. $n") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) Text(error ?: "", color = Color(0xFFEF5350), fontSize = 14.sp)
            BigButton(if (busy) "Vytvářím trezor…" else "Dokončit", V_ACCENT, V_ON_ACCENT) {
                if (!busy) {
                    if (VaultCrypto.normalize(answer) != words[n - 1]) {
                        error = "Nesedí, zkontroluj papír."
                    } else {
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.Default) {
                                runCatching { VaultManager.create(context, phrase) }.isSuccess
                            }
                            busy = false
                            if (ok) onDone() else error = "Trezor se nepodařilo vytvořit."
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultLocked(onBack: () -> Unit, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var usePin by remember { mutableStateOf(VaultManager.hasPin(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val activity = context as? Activity
    val hasBio = remember { Build.VERSION.SDK_INT >= 28 && VaultManager.hasBio(context) }
    // odemceni otiskem prstu (systemovy dialog); novy otisk v telefonu klic zneplatni
    fun startBio() {
        if (Build.VERSION.SDK_INT < 28 || activity == null) return
        val cipher = runCatching { VaultManager.bioUnlockCipher() }.getOrNull()
        if (cipher == null) {
            VaultManager.removeBio(context)
            error = "Otisk už neplatí, použij PIN nebo 12 slov."
            return
        }
        bioAuthenticate(
            activity, cipher, "Odemknout trezor",
            onOk = { c ->
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { VaultManager.bioFinishUnlock(context, c) }
                    if (ok) onUnlocked() else error = "Odemknutí otiskem se nepovedlo."
                }
            },
            onFail = { msg -> if (msg.isNotEmpty()) error = msg }
        )
    }
    LaunchedEffect(Unit) { if (hasBio) startBio() }
    if (usePin) {
        ScreenScaffold(title = "Trezor hesel", onBack = onBack) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Trezor je zamčený. Zadej PIN.", color = Color(0xFFCCCCCC), fontSize = 15.sp)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8); error = null },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error ?: "", color = Color(0xFFEF5350), fontSize = 14.sp)
                BigButton(if (busy) "Odemykám…" else "Odemknout PINem", V_ACCENT, V_ON_ACCENT) {
                    if (!busy && pin.length >= 4) {
                        busy = true
                        scope.launch {
                            val r = withContext(Dispatchers.Default) { VaultManager.unlockWithPin(context, pin) }
                            busy = false
                            pin = ""
                            when (r) {
                                PinResult.OK -> onUnlocked()
                                PinResult.WRONG -> error = "Špatný PIN. Zbývá pokusů: ${VaultManager.pinTriesLeft()}."
                                PinResult.TOO_MANY -> { usePin = false; error = "Příliš mnoho pokusů, PIN byl smazán. Zadej 12 slov." }
                            }
                        }
                    }
                }
                if (hasBio) BigButton("Odemknout otiskem prstu", V_ROW, Color.White) { startBio() }
                BigButton("Zadat 12 slov místo PINu", V_ROW, Color.White) { usePin = false; error = null }
            }
        }
        return
    }
    ScreenScaffold(title = "Trezor hesel", onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Trezor je zamčený. Zadej svých 12 slov (oddělená mezerou).", color = Color(0xFFCCCCCC), fontSize = 15.sp)
            if (hasBio) BigButton("Odemknout otiskem prstu", V_ROW, Color.White) { startBio() }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                label = { Text("12 slov") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
            )
            if (error != null) Text(error ?: "", color = Color(0xFFEF5350), fontSize = 14.sp)
            BigButton(if (busy) "Odemykám…" else "Odemknout", V_ACCENT, V_ON_ACCENT) {
                if (!busy) {
                    val valid = runCatching { VaultCrypto.isValidPhrase(context, text) }.getOrDefault(false)
                    if (!valid) {
                        error = "Zadej přesně 12 slov ze seznamu."
                    } else {
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.Default) { VaultManager.unlock(context, text) }
                            busy = false
                            if (ok) { text = ""; onUnlocked() } else error = "Tahle slova k trezoru nepatří."
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultList(onBack: () -> Unit, onLock: () -> Unit) {
    var list by remember { mutableStateOf(VaultManager.all()) }
    var selected by remember { mutableStateOf<VaultEntry?>(null) }
    var editing by remember { mutableStateOf<VaultEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pinDialog by remember { mutableStateOf(false) }

    val audit = remember(list) { VaultManager.audit() }   // slaba / opakovana hesla

    // zpet: z upravy zpet do detailu / seznamu, z detailu do seznamu
    BackHandler(enabled = selected != null || editing != null || adding) {
        if (editing != null || adding) { editing = null; adding = false } else selected = null
    }

    val ed = editing
    val sel = selected
    when {
        adding || ed != null -> VaultEdit(
            entry = ed,
            onBack = { editing = null; adding = false },
            onSaved = { editing = null; adding = false; selected = null; list = VaultManager.all() }
        )
        sel != null -> VaultDetail(
            entry = sel,
            onBack = { selected = null },
            onEdit = { editing = sel },
            onDeleted = { selected = null; list = VaultManager.all() }
        )
        else -> ScreenScaffold(title = "Trezor hesel", onBack = onBack) {
            if (list.isEmpty()) {
                EmptyText("Zatím tu nejsou žádná hesla. Přidej první tlačítkem níže.")
            }
            if (audit.isNotEmpty()) {
                Text(
                    "⚠ Hesla k vylepšení: ${audit.size} (slabá nebo opakovaná). Zkus je změnit.",
                    color = Color(0xFFFFB74D), fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { e ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(V_PANEL)
                            .clickable { selected = e }.padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Text(e.domain, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        if (e.username.isNotEmpty()) Text(e.username, color = V_MUTED, fontSize = 13.sp)
                        audit[e.id]?.let { Text("⚠ $it heslo", color = Color(0xFFFFB74D), fontSize = 12.sp) }
                    }
                }
            }
            BigButton("+ Přidat přihlášení", V_ACCENT, V_ON_ACCENT) { adding = true }
            BigButton("Rychlé odemykání (PIN, otisk)", V_ROW, Color.White) { pinDialog = true }
            BigButton("Zamknout trezor", V_ROW, Color.White, onClick = onLock)
        }
    }
    if (pinDialog) VaultPinDialog(onDismiss = { pinDialog = false })
}

@Composable
private fun VaultField(label: String, value: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(V_PANEL).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = V_MUTED, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
private fun VaultDetail(entry: VaultEntry, onBack: () -> Unit, onEdit: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    ScreenScaffold(title = entry.domain, onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultField("Uživatel", entry.username.ifEmpty { "—" })
            VaultField("Heslo", if (show) entry.password else "••••••••••")
            if (entry.note.isNotEmpty()) VaultField("Poznámka", entry.note)
            BigButton("Kopírovat heslo", V_ACCENT, V_ON_ACCENT) { copySecret(context, entry.password) }
            BigButton("Kopírovat uživatele", V_ROW, Color.White) { copySecret(context, entry.username) }
            BigButton(if (show) "Skrýt heslo" else "Zobrazit heslo", V_ROW, Color.White) { show = !show }
            BigButton("Upravit", V_ROW, Color.White, onClick = onEdit)
            BigButton("Smazat", V_DANGER, Color.White) { confirm = true }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Smazat heslo?",
            text = entry.domain,
            noLabel = "Zrušit",
            yesLabel = "Smazat",
            onNo = { confirm = false },
            onYes = {
                confirm = false
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { VaultManager.remove(context, entry.id) } }
                    onDeleted()
                }
            }
        )
    }
}

// Z adresy nechat jen domenu (seznam.cz), bez https:// , cesty a "www."
private fun cleanDomain(s: String): String =
    s.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore('?').removePrefix("www.")

@Composable
private fun VaultEdit(entry: VaultEntry?, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var domain by remember { mutableStateOf(entry?.domain ?: "") }
    var username by remember { mutableStateOf(entry?.username ?: "") }
    var password by remember { mutableStateOf(entry?.password ?: "") }
    var note by remember { mutableStateOf(entry?.note ?: "") }
    var show by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ScreenScaffold(title = if (entry == null) "Přidat přihlášení" else "Upravit přihlášení", onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = domain, onValueChange = { domain = it; error = null },
                label = { Text("Web (např. seznam.cz)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("Jméno / e-mail") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it; error = null },
                label = { Text("Heslo") }, singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VaultSmallButton(if (show) "Skrýt" else "Zobrazit", Modifier.weight(1f)) { show = !show }
                VaultSmallButton("Vygenerovat heslo", Modifier.weight(1f)) { password = VaultCrypto.generatePassword(); show = true; error = null }
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Poznámka") },
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) Text(error ?: "", color = Color(0xFFEF5350), fontSize = 14.sp)
            BigButton("Uložit", V_ACCENT, V_ON_ACCENT) {
                val d = cleanDomain(domain)
                if (d.isEmpty() || password.isEmpty()) {
                    error = "Vyplň web a heslo."
                } else {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching {
                                if (entry == null) VaultManager.save(context, d, username.trim(), password, note)
                                else VaultManager.update(context, entry.copy(domain = d, username = username.trim(), password = password, note = note))
                            }.isSuccess
                        }
                        if (ok) onSaved() else error = "Uložení se nepovedlo (je trezor odemčený?)."
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultSmallButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(V_ROW).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

// Zkopiruje text oznaceny jako citlivy (Android 13+ ho neukazuje v nahledu schranky); po minute se schranka vycisti
private fun copySecret(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("trezor", text)
    if (Build.VERSION.SDK_INT >= 24) {
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    }
    cm.setPrimaryClip(clip)
    Handler(Looper.getMainLooper()).postDelayed({
        runCatching {
            val now = cm.primaryClip?.getItemAt(0)?.text?.toString()
            if (now == text) {
                if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }, 60_000)
}

/** Import: soubor obsahuje trezor, zadej 12 slov. Zasifrovane hesla se obnovi (nahradi stavajici trezor). */
@Composable
fun VaultRestoreDialog(backup: VaultBackup, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(V_PANEL).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Trezor hesel", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("V souboru je zašifrovaný trezor. Zadej svých 12 slov. Stávající trezor v tomto telefonu se nahradí.", color = V_MUTED, fontSize = 13.sp)
            OutlinedTextField(
                value = text, onValueChange = { text = it; error = null },
                label = { Text("12 slov") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp)
            )
            if (error != null) Text(error ?: "", color = Color(0xFFEF5350), fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                Text(
                    "Zrušit", color = Color(0xFFCCCCCC), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(V_ROW).clickable { if (!busy) onDismiss() }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
                Text(
                    if (busy) "Obnovuji…" else "Obnovit", color = V_ON_ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(V_ACCENT).clickable {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                val n = withContext(Dispatchers.Default) { VaultManager.restore(context, text, backup) }
                                busy = false
                                if (n >= 0) onResult("Trezor obnoven: $n hesel") else error = "Tahle slova k souboru nepatří."
                            }
                        }
                    }.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/** Nastaveni PINu (4-8 cislic): odemkne trezor v tomto telefonu bez 12 slov; po 5 spatnych pokusech se smaze. */
@Composable
private fun VaultPinDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val has = remember { VaultManager.hasPin(context) }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var bioOn by remember { mutableStateOf(VaultManager.hasBio(context)) }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(V_PANEL).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Rychlé odemykání", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (Build.VERSION.SDK_INT >= 28) {
                Text(
                    if (bioOn) "Vypnout otisk prstu" else "Zapnout otisk prstu", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(V_ROW).clickable {
                        if (bioOn) {
                            VaultManager.removeBio(context)
                            bioOn = false
                            AppToast.show(context, "Otisk vypnut")
                        } else {
                            val act = context as? Activity
                            val cipher = runCatching { VaultManager.bioEnableCipher() }.getOrNull()
                            if (act == null || cipher == null) {
                                error = "V telefonu není nastavený otisk prstu."
                            } else {
                                bioAuthenticate(
                                    act, cipher, "Zapnout odemykání otiskem",
                                    onOk = { c ->
                                        scope.launch {
                                            val ok = withContext(Dispatchers.Default) { runCatching { VaultManager.bioFinishEnable(context, c) }.isSuccess }
                                            if (ok) { bioOn = true; AppToast.show(context, "Otisk zapnut") } else error = "Zapnutí se nepovedlo."
                                        }
                                    },
                                    onFail = { m -> if (m.isNotEmpty()) error = m }
                                )
                            }
                        }
                    }.padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }
            Text(if (has) "Změnit PIN" else "Nastavit PIN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("PIN (4 až 8 číslic) odemkne trezor jen v tomto telefonu. Po 5 špatných pokusech se smaže a bude potřeba 12 slov.", color = V_MUTED, fontSize = 13.sp)
            OutlinedTextField(
                value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8); error = null },
                label = { Text("PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pin2, onValueChange = { pin2 = it.filter { c -> c.isDigit() }.take(8); error = null },
                label = { Text("PIN znovu") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) Text(error ?: "", color = Color(0xFFEF5350), fontSize = 13.sp)
            if (has) {
                Text(
                    "Zrušit PIN", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(V_DANGER).clickable {
                        VaultManager.removePin(context)
                        AppToast.show(context, "PIN zrušen")
                        onDismiss()
                    }.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                Text(
                    "Zrušit", color = Color(0xFFCCCCCC), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(V_ROW).clickable { if (!busy) onDismiss() }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
                Text(
                    if (busy) "Ukládám…" else "Uložit", color = V_ON_ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(V_ACCENT).clickable {
                        if (!busy) {
                            if (pin.isEmpty() && pin2.isEmpty()) {
                                onDismiss()   // PIN se nemeni (treba jen zapnuty otisk)
                            } else if (pin.length < 4) {
                                error = "PIN musí mít aspoň 4 číslice."
                            } else if (pin != pin2) {
                                error = "PINy se neshodují."
                            } else {
                                busy = true
                                scope.launch {
                                    val ok = withContext(Dispatchers.Default) {
                                        runCatching { VaultManager.setPin(context, pin) }.isSuccess
                                    }
                                    busy = false
                                    if (ok) { AppToast.show(context, "PIN nastaven"); onDismiss() } else error = "PIN se nepodařilo nastavit."
                                }
                            }
                        }
                    }.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}

// Systemovy dialog otisku prstu (Android 9+) navazany na sifru z Keystore; onFail("") = uzivatel zrusil
@RequiresApi(28)
private fun bioAuthenticate(activity: Activity, cipher: Cipher, title: String, onOk: (Cipher) -> Unit, onFail: (String) -> Unit) {
    val executor = activity.mainExecutor
    val prompt = android.hardware.biometrics.BiometricPrompt.Builder(activity)
        .setTitle(title)
        .setNegativeButton("Zrušit", executor) { _, _ -> onFail("") }
        .build()
    prompt.authenticate(
        android.hardware.biometrics.BiometricPrompt.CryptoObject(cipher),
        android.os.CancellationSignal(),
        executor,
        object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (c != null) onOk(c) else onFail("Ověření se nepovedlo.")
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                onFail(errString?.toString() ?: "")
            }
        }
    )
}
