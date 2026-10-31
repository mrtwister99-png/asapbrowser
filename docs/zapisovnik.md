# Zápisovník

Deník projektu: po **každé session** sem přibude jeden zápis (nejnovější nahoře). Slouží jako rychlý přehled, co se kdy změnilo a co zbývá ověřit.

## Šablona zápisu

```
## RRRR-MM-DD – session N: krátký název
**Hotovo:** …
**Změněné soubory:** …
**Nová / změněná nastavení (export):** …
**Neověřeno / pozor:** …
**Další krok:** …
```

---

## 2026-10-31 – session 10: pozadí UFO Field, hodnocení a chyby (držení Nastavení), O aplikaci

**Hotovo**
- **`UfoFieldView.kt`:** nativní port animace z přiloženého archivu (`AsapUfoRandomField`, React/Canvas): noční mlhovina + teckovaná síť, 28 UFO ve 3 vrstvách s paralaxou kamery, stavový automat letu, srážky → barevný ohňostroj, klepnutí = vlastní ohňostroj, prst = odpuzovač (panika, červenožluté blikání), každý 10. vstup do zóny = portál, 2,5 % modrý stalker, vinětace. Vynecháno: počítadlo srážek, ukládání skóre na server, tlačítka Pauza / Rozptýlit / Reset a texty z webové verze. Rozměry v dp.
- **Držení tlačítka Nastavení** (tlačítko 4) otevírá obrazovku **Hodnocení a chyby** (`SettingsFeedback.kt`) s pozadím UFO Field: hvězdičky, Recenze / Chyba, text (min. 5 znaků), nepovinný e-mail, Odeslat. Nová funkce `ButtonFunction.FEEDBACK` (ikona `ic_feedback.xml`), výchozí držení tlačítka 4 (`ButtonDefaults.long(4)`; `fixButtons()` ji nastaví i po aktualizaci).
- **Nastavení → O aplikaci** má stejné pozadí; karty jsou polopřůhledné (`ScreenScaffold` dostal parametr `containerColor`).
- Dotyky, které prvky obsahu nespotřebují, se předávají pozadí (`UfoBackground`).
- Odesílání: `postFeedback` posílá JSON (`rating`, `type`, `message`, `contact`, `app_version`, `android`, `device`) přes HTTPS na `FeedbackConfig.FUNCTION_URL` s hlavičkami `apikey` a `Authorization: Bearer <anon klíč>`. **Adresa a klíč jsou zatím prázdné**, obrazovka to hlásí a nic neodesílá.

**Změněné soubory:** nové `UfoFieldView.kt`, `ui/SettingsFeedback.kt`, `res/drawable/ic_feedback.xml`; změněné `core/settings/AppSettings.kt`, `MainActivity.kt`, `SettingsActivity.kt`, `ui/SettingsScreens.kt`, `ui/SettingsAbout.kt`, `ui/SettingsGestures.kt` (`StyleCard` je `internal`), `ui/SettingsTutorials.kt`, docs.

**Neověřeno / pozor:** plynulost animace na slabších telefonech (28 lodí + ohňostroje; při potížích snížit počty ve `buildFleet`), chování dotyků nad obsahem (klepnutí do prázdna = ohňostroj), klávesnice nad polem zprávy (`imePadding`).
**Čeká:** server pro hodnocení (Supabase Edge Function `submit-feedback`, tabulka `feedback`, e-mail přes Resend s klíčem v tajných proměnných), adresa projektu a anon klíč.

---

## 2026-10-30 – session 9: nová úvodní animace s hudbou

**Hotovo**
- **Nahrazena úvodní animace s planetou** (`IntroView.kt` přepsán): bílé pozadí, `sede_logo.png` se za ~1,3 s pomalu zviditelní, ~1,8 s glitch do `asap_logo_full`, ~3,0 s glitch zpět, ~4,0 s glitch podruhé a zůstane plné logo (celá animace 5 s + 1,7 s klid, tedy 6,7 s; hudba hraje po celou dobu). Glitch = vodorovné pasy posunuté do strany + červený a modrý „duch“; časování je v `IntroView.GLITCHES` a `DURATION_MS`.
- **Hudba** `res/raw/bez_zastaveni.mp3` hraje od začátku animace a na konci (nebo po Přeskočit) se za 0,4 s ztlumí; respektuje přepínač Zvuky v Konfiguraci, bez souboru je ticho.
- **Tlačítko Přeskočit** je vpravo dole (tmavý text na světlém podkladu).
- Odstraněn starý `showSplash()` (bílý splash s logem po planetě), nová animace končí tím samým záběrem a rovnou naváže animace tlačítka Nastavení.
- Přepínač v Konfiguraci: popis „Logo s hudbou při startu aplikace“.
- Soubory se hledají přes `getIdentifier` (jako zvuky gest): chybějící `sede_logo.png` = použije se plné logo, chybějící mp3 = ticho; appka se sestaví i bez nich.

**Změněné soubory:** `IntroView.kt`, `MainActivity.kt`, `ui/SettingsOther.kt`, `docs/FUNKCE.md`, `docs/struktura.md`, `docs/zapisovnik.md`.
**Neověřeno / pozor:** vzhled glitche a časování na zařízení (ladit v `IntroView.GLITCHES`); barevní „duchové“ používají MULTIPLY, na logu s průhledným pozadím vyjdou jen na logu, s bílým pozadím obarví celý pás. Hudba delší než 5 s se na konci ztlumí.
**Čeká:** pozadí při držení tlačítka Nastavení (přiložený zip nedorazil, chybí upřesnění).

---

## 2026-10-30 – session 8: refaktor B2a – screenshoty, gesta, AdBlock z MainActivity

Čistý přesun kódu, **žádná změna chování**. Bez možnosti sestavit; po nasazení sestavit a vyzkoušet screenshoty (všechny 3 druhy), konfigurační mód gest a AdBlock (včetně YouTube a kosmetických filtrů).

**Hotovo**
- Z `MainActivity.kt` (4 433 → 3 831 řádků) přesunuty funkce do souborů s **rozšířeními** `MainActivity` (`internal fun MainActivity.xxx()`): `MainScreenshots.kt` (11 funkcí), `MainGestures.kt` (8), `MainAdBlock.kt` (10).
- Stav (proměnné, `inner class CosmeticBridge`, nastavení WebView) zůstal v `MainActivity`. Členy, které přesunuté funkce používají, se změnily z `private` na `internal`.
- Uvnitř přesunutých funkcí by `this@MainActivity` přešlo na `this@<název funkce>` (ve skutečnosti se nevyskytovalo).

**Kontrola:** všechny zdroje zkompilovány Kotlin kompilátorem před i po, množina chyb (jen chybějící Android/Compose knihovny) je shodná; kontrola odhalila 2 členy s anotací (`isAdBlockOn`, `adblockReady`), které zůstaly `private`, opraveno. Změnily se jen hlavičky funkcí a viditelnost členů.

**Změněné soubory:** `MainActivity.kt`, `MainScreenshots.kt`, `MainGestures.kt`, `MainAdBlock.kt` (nové), `docs/struktura.md`, `docs/zapisovnik.md`.
**Další krok:** B2b – informace o stránce / sdílení / oprávnění, vstup a našeptávač, dialogy; pak animace, tlačítka, karty.

---

## 2026-10-30 – session 7: refaktor B1 – samostatné třídy z MainActivity.kt

Čistý přesun kódu, **žádná změna chování**. Bez možnosti sestavit; po nasazení sestavit a vyzkoušet: screenshot výřezu, hvězda Oblíbených, kulička gest, úvodní animace, plnění tlačítka při stahování.

**Hotovo**
- Z `MainActivity.kt` (5 014 → 4 433 řádků) přesunuto do vlastních souborů ve stejném balíčku: `RegionSelectView`, `StarBurstView` (dříve vnořené `private class`, teď `internal class`), `GestureOrbView`, `IntroView` (včetně shaderu AGSL), `DownloadFillDrawable`.
- Každý nový soubor má plnou sadu původních importů (nepoužité jen hlásí varování).

**Kontrola:** všechny zdroje zkompilovány Kotlin kompilátorem před i po, množina chyb (jen chybějící Android/Compose knihovny) je shodná. Přesunuté třídy nepoužívají žádného člena `MainActivity`. Žádný původní řádek se neztratil (jen `private class` → `internal class` u dvou vnořených).

**Změněné soubory:** `MainActivity.kt`, 5 nových (`RegionSelectView`, `StarBurstView`, `GestureOrbView`, `IntroView`, `DownloadFillDrawable`), `docs/struktura.md`, `docs/zapisovnik.md`.
**Další krok:** refaktor B2 – funkce `MainActivity` podle oblastí do souborů s rozšířeními (nejdřív menší oblasti).

---

## 2026-10-30 – session 6: refaktor A – rozdělení SettingsScreens.kt

Čistý přesun kódu, **žádná změna chování**. Bez možnosti sestavit; po nasazení sestavit a projít všechny obrazovky Nastavení.

**Hotovo**
- `ui/SettingsScreens.kt` (3 490 řádků) rozdělen na 9 souborů: `SettingsScreens` (kořen + společné prvky), `SettingsBookmarks`, `SettingsHistory`, `SettingsDownloads`, `SettingsButtons`, `SettingsGestures`, `SettingsOther`, `SettingsTutorials`, `SettingsAbout`.
- Funkce a hodnoty používané napříč soubory změněny z `private` na `internal` (obrazovky, `ScreenScaffold`, `StyledDialog`, `SettingsRow`, `SwitchRow`, `GlyphIcon`, `BigButton`, `EmptyText`, `SiteThumb`, `FunctionChoice`, `FunctionSearchField`, `GESTURE_GREEN`). Barvy (`BG`, `PANEL`, `ROW`, `ACCENT`, `MUTED`, `DANGER`) zůstaly soukromé a jsou zkopírované v každém souboru (v `TabsScreen.kt` existují stejně pojmenované).
- Každý soubor má plnou sadu původních importů (nepoužité jen hlásí varování).

**Kontrola:** všechny zdroje zkompilovány Kotlin kompilátorem před i po rozdělení, množina chyb (jen chybějící Android/Compose knihovny) je shodná, žádná nová chyba. Žádný původní řádek se neztratil (kromě jednoho komentáře-oddělovače).

**Změněné soubory:** 9× `ui/Settings*.kt`, `docs/struktura.md`, `docs/zapisovnik.md`, `docs/todolist.md`.
**Další krok:** refaktor B krok 1 (samostatné třídy z `MainActivity.kt`).

---

## 2026-10-30 – session 5: úprava adresy, chování polí, panel gest

Opět **bez možnosti sestavit** (jen kontrola syntaxe) – vyzkoušet na telefonu.

**Hotovo**
- **Tlačítko „✎ Upravit“** na zeleném řádku aktuální stránky (vedle Kopírovat): vloží adresu stránky do pole, ve kterém se píše (horní i dolní), kurzor na konec, klávesnice zůstane (`editCurrentUrl`, `prefillCurrentUrl`).
- **Chování polí:** horní pole při klepnutí začíná prázdné (psaní od nuly). Dolní pole ukáže adresu aktuální stránky s kurzorem na konci a jde upravit. Na nové kartě / interní stránce je dolní pole prázdné. Při vložení adresy `prefillingUrl` vypne watchery, takže se nepustí našeptávač ani doplňování; zelený řádek a nejnavštěvovanější zůstanou, dokud uživatel nezačne psát.
- **Panel gest (konfigurační mód):** tlačítko ▲/▼ v hlavičce panelu minimalizuje panel na hlavičku (název, ▲, „Uložit a zavřít nastavení“); plocha gest je pak plně vidět a jde pohodlně upravit.

**Změněné soubory:** `MainActivity.kt`, `ui/SearchSuggestions.kt`, `ui/SettingsScreens.kt`, `res/drawable/ic_edit_url.xml` (nový), `docs/zapisovnik.md`.

**Neověřeno / pozor:** funkce „Hledání (otevřít klávesnici)“ i klepnutí na dolní lištu teď taky vloží adresu stránky (stejná cesta jako klepnutí do pole). Ikona Upravit je vektor `res/drawable/ic_edit_url.xml` (stejný styl jako ostatní, čára 0,8).

---

## 2026-10-30 – session 4: bezpečnost release a rychlost

Opět **bez možnosti sestavit** (jen kontrola syntaxe) – po nasazení sestavit, hlavně vyzkoušet **release APK**.

**Hotovo**
- **Záloha:** `android:allowBackup="false"` – cookies, přihlášení a historie nejdou do Google zálohy (přenos zůstává přes Export / Import).
- **R8 / release:** `keepRules/rules.keep` – ponechán `RustBridge` (JNI), všechny `native` metody a metody s `@JavascriptInterface` (`CosmeticBridge`). Bez toho mohl R8 v release přejmenovat JNI a AdBlock přes Rust by se tiše vypnul.
- **AdBlock rychlost:** `guessResourceType` už nekompiluje `Regex` na každý požadavek, používá konstanty `IMAGE_EXTS` / `MEDIA_EXTS` (`endsWith`).
- **Ukládání karet:** `saveTabs()` zapíše JSON hned, náhledy (JPEG) se zapisují na pozadí (`writePreviewsAsync`, jedno vlákno) a jen ty, které se změnily; přebytečné `p<i>.jpg` se smažou. Vlákno se ukončí v `onDestroy`.
- **WebView:** odebráno `setLayerType(LAYER_TYPE_HARDWARE)` (méně GPU paměti na kartu).

- **Baseline Profile (dodatek):** nový `app/src/main/baseline-prof.txt` (ruční, s hvězdičkami pro balíček appky) a závislost `androidx.profileinstaller:profileinstaller:1.4.1` v `app/build.gradle.kts`.

**Změněné soubory:** `AndroidManifest.xml`, `keepRules/rules.keep`, `MainActivity.kt`, `app/build.gradle.kts`, `app/src/main/baseline-prof.txt`, `docs/zapisovnik.md`, `docs/todolist.md`.

**Neověřeno / pozor:** plynulost bez hardware layer (porovnat na zařízení, při problému vrátit řádek v `buildWebView`); release APK s adblockem (musí blokovat přes Rust i kosmetické filtry); při zabití procesu uprostřed zápisu může jedna karta chvíli ukázat starý náhled.
**Další krok:** Baseline Profile (čeká na souhlas s novým souborem), pak rozdělení `MainActivity.kt` a `SettingsScreens.kt`.

---

## 2026-10-30 – session 3: Tutoriály a O aplikaci v Nastavení

Opět **bez možnosti sestavit** (jen kontrola syntaxe Kotlin kompilátorem) – po nasazení sestavit a vyzkoušet.

**Hotovo**
- **Nastavení → Tutoriály:** seznam 10 tutoriálů (Tlačítka klepnutí/držení, Gesta, Karty a složky, Anonymní karta, AdBlock, Boční lišta, Screenshot, Adresní řádek/sdílení/QR, Oblíbené a Záložky, Export a import). Detail = animovaná ukázka + číslovaný postup + tip. Ukázky jsou kreslené v Compose (`TapHoldDemo`, `GestureDemo`, `FolderDemo`, `IconDemo`), bez externích souborů. Zpět v detailu vrací na seznam tutoriálů.
- **Nastavení → O aplikaci:** logo, název, verze (`versionName` + build z `PackageManager`), popis, co umí, co se připravuje (VPN, profily), soukromí, použité součásti (EasyList/EasyPrivacy, ZXing, AndroidX/Compose/Material, WebView, Rust jádro).
- Nové texty tutoriálů jsou v seznamu `TUTORIALS` – nový tutoriál = jeden záznam.

**Změněné soubory:** `ui/SettingsScreens.kt` (jen přidané řádky: importy, `Route.Tutorials`/`Route.About`, dva řádky v kořeni Nastavení, nové obrazovky na konci souboru), `docs/zapisovnik.md`, `docs/todolist.md`, `docs/struktura.md`.

**Nová / změněná nastavení (export):** žádná.

**Neověřeno / pozor:** vzhled a plynulost ukázek na zařízení; texty tutoriálů jsou psané podle kódu a dokumentace, ne podle běhu appky (zkontrolovat, zda sedí výchozí gesta a tlačítka). Do „O aplikaci“ zatím nejsou odkazy ani kontakt (chybí podklady).
**Další krok:** zbývá úvodní průvodce (intro) a verze pro Google Play – viz [todolist.md](todolist.md).

---

## 2026-10-30 – session 2: rychlost, vyhledávač, Konfigurace

Pokračování téhož dne. Opět **bez možnosti sestavit** (jen kontrola syntaxe Kotlin kompilátorem) – po nasazení sestavit a vyzkoušet.

**Hotovo**
- **Rychlejší start s mnoha kartami:** WebView se vytváří líně (`BrowserTab.webView` je `lazy`, stavba je v `buildWebView`). Obnovené karty na pozadi WebView nemají, vznikne až při prvním zobrazení. `disposeTab` a `onDestroy` WebView, který nikdy nevznikl, nevytvářejí.
- **Náhledy stránek:** fotí a ukládají se jen, když je uložený náhled domény starší než 3 dny (nebo chybí) – `thumbStale`.
- **Našeptávač:** při psaní pauza 120 ms a historie/záložky/nejnavštěvovanější se čtou z DB jen jednou za 15 s (při prázdném poli vždy).
- **Verze pro počítač podle webu:** `SiteRules.desktopOn/setDesktop`, nastavení `desktop_sites`; při přechodu na jiný web se použije jeho uložená volba (jen běžné karty).
- **Skrývání lišt při posouvání:** posun dolů schová horní a dolní lištu i všechna boční tlačítka (5, 6 a přidaná), posun nahoru nebo začátek stránky je vrátí. Nepracuje při psaní, v konfiguračních módech ani s otevřenou levou lištou. Vypíná se v Konfiguraci.
- **Vyhledávač:** Google / Seznam / DuckDuckGo (`SearchEngines`); volba v Konfiguraci i při držení tlačítka Domů; používá se při hledání z pole i v našeptávači.
- **Nastavení → Konfigurace** (nové tlačítko dole): animace tlačítek, úvodní animace, zvuky, vibrace, skrývání lišt, vyhledávač, smazání uložených náhledů a ikon, zapomenutí webů s verzí pro počítač. Vypínače animací, zvuků a vibrací platí globálně (`playRawSound`, `gestureTick`, `buzz`, animace tlačítek).

- **Náhledy a ikony webů jdou vypnout** (Konfigurace): vypnuto = neukládají se a nezobrazují se (nová karta, Historie, Oblíbené). Konfigurace ukazuje, kolik místa uložené náhledy a ikony zabírají (soubory a MB).

**Změněné soubory:** `BrowserTabs.kt`, `MainActivity.kt`, `AppSettings.kt`, `GestureHandler.kt`, `SearchSuggestions.kt`, `SettingsScreens.kt`.

**Nová nastavení (v exportu):** `thumbs_on`, `animations_on`, `intro_on`, `sounds_on`, `vibration_on`, `hide_bars_on_scroll`, `search_engine`, `desktop_sites`.

**Neověřeno / pozor**
- Líné WebView: zkontrolovat obnovu karet po restartu, přepínání mezi kartami, zavření karty bez WebView a anonymní karty.
- Skrývání lišt: chování s klávesnicí a s přidanými bočními tlačítky (kulička gest se má schovat spolu s tlačítkem).
- Verze pro počítač: při prvním přechodu na uložený web se stránka načte dvakrát (nejdřív v mobilní verzi, pak přepnutá).

**Další krok:** sestavit a projít; pak VPN, Profily, O aplikaci ([todolist.md](todolist.md)).

### Návrh: rozdělení `MainActivity.kt` (zatím nezačato)
`MainActivity.kt` má přes 4 900 řádků, `SettingsScreens.kt` přes 3 000. Cíl: žádný soubor nad ~800 řádků, `MainActivity` jen kostra (životní cyklus, propojení view). Postupovat po malých krocích; **jeden krok = jedno sestavení a krátký test**, nikdy nepřesouvat a měnit logiku zároveň.

1. **Pojistka:** sestavit a otestovat současný stav, commit (tag `pred-refaktorem`).
2. **Samostatné třídy (bez rizika, ~800 řádků):** `GestureOrbView`, `IntroView`, `DownloadFillDrawable` z konce souboru, `StarBurstView`, `RegionSelectView`, `TabGroup`, `MenuRow` → `ui/views/` (jen `private` → `internal`).
3. **Logika bez obrazovek:** složky karet a ukládání/obnova karet (`TabGroup`, `pruneGroups`, `mergeTabs`, `saveTabs`, `restoreTabs`) → `core/tabs/`; ukládání náhledů a ikon webů (`site_thumbs`, `site_icons`, `thumbStale`) → jedno společné místo (dnes je podobný kód i v `SettingsScreens.kt`).
4. **Funkční celky jako „controllery"** (třída dostane `MainActivity` nebo malé rozhraní se `webView`, `root`, `dp()`, `showStyledDialog`, `performFunction`): screenshoty (~450 řádků), informace o stránce + oprávnění webů (~570), konfigurační mód tlačítek (~330), konfigurační mód gest (~440), nabídky odkazu a obrázku, skrývání lišt.
5. **Karty a WebView:** `createTab` / `buildWebView` / `switchTo` / zavírání do `TabsController`; `WebViewClient` a `WebChromeClient` do vlastních tříd (AdBlock, ikony, oprávnění, posouvání).
6. **`SettingsScreens.kt`:** rozdělit po obrazovkách (`Buttons`, `Gestures`, `Bookmarks`, `History`, `Other`, `Downloads`).

Rizika: `private` členy se musí zpřístupnit; Compose lambdy drží staré hodnoty (hlídat `rememberUpdatedState`); pořadí inicializace (`lateinit`). Protože tu nejsou automatické testy, po každém kroku projít ručně: otevřít karty, složky, gesta, screenshot, Export/Import. Odhad: 5–6 krátkých sessions.


---

## 2026-10-30 – session 1: ikony, tlačítka, karty, screenshoty, gesta

Velká session. Vše bylo napsáno **bez možnosti sestavit projekt** (prostředí nemělo Android SDK), takže před dalšími úpravami: **sestavit, spustit a opravit případné chyby překladu**. Zkontrolované byly jen závorky, XML a vzhled nových ikon.

### Hotovo

**Ikony a vzhled**
- Nahrazena sada ikon (37 bílých vektorů, čára 0,8): funkce tlačítek, výběr funkcí, náhled tlačítek, levá lišta, výchozí ikony tlačítek 1, 3, 5, 7, 8.
- Ikona Sdílet přepracována na klasické tři propojené body (`ic_share_url`).
- Přepracovány 4 sporné ikony: Duplikovat kartu (okno s plusem), Hledání (pole s lupou a kurzorem), Vrátit zavřenou kartu (šipka zpět), Rychle do oblíbených (hvězda s plusem).
- Výběr funkcí (tlačítka, gesta) má pod názvem krátký popis, co funkce dělá (`ButtonFunction.description()`).
- Nabídka odkazu: zeleně zvýrazněná Nová karta, nová položka „Uložit do Oblíbených".
- Nabídka odkazu: krátké názvy (Nová karta, Anonymní karta, Kopírovat text/adresu, Sdílet) s adresou pod nimi; ikona Sdílet v adresním řádku zvětšena.
- Nabídky při dlouhém podržení na odkazu a obrázku mají ikony (Sdílet = tři propojené body).
- Nové ikony: foťáček (`ic_screenshot`), rychlý screenshot (`ic_screenshot_quick`); Nastavení a Domovská stránka přepracovány na ozubené kolečko (místo sluníčka).
- Vypnuta systémová úvodní ikona Androidu 12+ (`themes.xml`); zůstává jen úvodní animace.

**Tlačítka a funkce**
- Tlačítka 1–4 pevná (funkce nejdou měnit); všechna mají animace: Domů 2× zeleně (klepnutí) / zelená ikona (držení), Karty otočení 360° / 720° + plus, Oblíbené žlutá hvězda s jiskrami (klepnutí i držení), Nastavení logo → kolečko (a zpět po návratu).
- Tlačítka 7 a 8 jen zap/vyp funkce (AdBlock, VPN, Verze pro počítač).
- Gesta a Boční lišta max 1× (přesun při přiřazení), ve výběru nahoře, zelené dokud nejsou použité.
- Zámek proti dvojitému otevření oken (0,9 s).
- Držení Domů: tlačítko „Přidat aktuální stránku".
- **Konfigurační mód tlačítek:** Nastavení → Tlačítka otevře hlavní plochu, klepnutí na tlačítko otevře jeho nastavení v okně, „+" pro přidání bočního tlačítka, zelené přerušované „Uložit a zavřít nastavení".

**Adresní řádek**
- Zámek: zelený/červený/šedý podle HTTPS/HTTP; klepnutí otevře **Informace o stránce** (zabezpečení, certifikát, AdBlock s výjimkou pro web, oprávnění webu poloha/kamera/mikrofon, smazání cookies, varování u HTTP). Tečka na zámku = AdBlock na webu vypnutý.
- Pravá ikona = **Sdílet** (odkaz / QR kód, Sdílet i Kopírovat).
- Našeptávač: zelený řádek s aktuální stránkou (ikona webu, Kopírovat → „Zkopírováno ✓"), nejnavštěvovanější jen 3 řádky se scrollem; u dolního pole je aktuální stránka dole, u horního nahoře.

**Karty**
- Přehled karet bez hlavičky; držení za tělo = výběr více karet, červené „Zavřít (N)" s potvrzením.
- Držení za hlavičku + tah: na hlavičku karty = **složka**, na tělo/vedle = přesun. Složky: barva (8 barev), otevření, vyjmutí tlačítkem i tahem nad mřížku, animace zániku, ukládání po restartu.

**Nová karta**
- Počítadlo zablokovaných reklam celkem (40 % šířky) + přepínač Nejnavštěvovanější / Oblíbené; dlaždice s náhledem stránky a ikonou webu; nejnavštěvovanějších je 12.
- Náhledy a ikony ukládány při návštěvě; zobrazeny i v Historii a Oblíbených.

**Screenshot**
- Nabídka: celá obrazovka / výběr části (s úchyty na rozích) / celá stránka (scrollovací). Nové funkce pro tlačítka a gesta: „Screenshot" a „Rychlý screenshot (celá stránka)". Po uložení malý náhled otevírající galerii. Foťáček v liště při klepnutí bliká zeleně.

**Gesta – konfigurační mód (tato session, na konci)**
- Nastavení → Gesta zavře Nastavení; hlavní okno ukáže jen kuličku Gesta a plochu gest s uchyty na okrajích a rozích (velikost i poloha podle libosti), shora se vysune scrollovací panel: styl plochy (číslice / kolečka), viditelnost plochy 0–100 %, odezva (zabrnění, zvuk), vrácení výchozí polohy, seznam gest (přidat, přenahrát, smazat, obnovit).
- Plocha gest umí být obdélník (ne jen čtverec).

### Změněné soubory (nejdůležitější)
`MainActivity.kt`, `SettingsScreens.kt`, `TabsScreen.kt`, `SearchSuggestions.kt`, `AppSettings.kt`, `SettingsActivity.kt`, `BrowserTabs.kt`, `AndroidManifest.xml`, `activity_main.xml`, `themes.xml` (+ night), `res/drawable/ic_*.xml` (sada ikon + 2 nové).

### Nová nastavení (zahrnuta v exportu)
`adblock_exceptions`, `site_permissions`, `total_blocked`, `start_show_favorites`, `gesture_rect`, `gesture_opacity`.

### Nová oprávnění v manifestu
Poloha, kamera, mikrofon (jen kdyby o ně požádal web a uživatel povolil). Pozor při publikaci na Google Play (formulář o oprávněních).

### Oprava chyby překladu (17:00)
Překlad hlásil `Unresolved reference 'ic_screenshot'` – chyběl soubor `res/drawable/ic_screenshot.xml` (byl jen v dřívějším zipu). Poslán kompletní zip všech změn (`ASAP-komplet.zip`). Zdroje navíc prohnány Kotlin kompilátorem: bez syntaktických chyb (zbývají jen nerozpoznané Android třídy, protože chyběl SDK).

### Neověřeno / pozor
- Celý překlad a běh – nic nebylo zkoušeno na zařízení.
- Rizikové části: animace hvězdy (`StarBurstView`), přetahování v přehledu karet, scrollovací screenshot (fixní hlavičky se opakují, stránky s vlastním scrollem), kopie QR do schránky, plocha gest s obdélníkem (klávesy na nečtvercové ploše), konfigurační módy (skrývání prvků).
- `docs/ICONS.md` je zastaralý (popisuje emoji ikony).
- Složka `rust-enginee/rust/` vypadá jako kopie `rust-enginee/src` – ověřit.

### Dodatek (17:00–17:20): opravy a drobnosti
- Chyba překladu `Unresolved reference 'ic_screenshot'`: chyběl soubor ikony, poslán kompletní zip a zvlášť zip s ikonami (`ASAP-ikonky.zip`).
- Výběr funkcí (tlačítka, gesta): **pole „Hledat funkci"** (bez ohledu na diakritiku, hledá v názvu i popisu) a **krátké popisy** (max. jeden řádek).
- Nabídka odkazu: Nová karta zeleně, přidáno „Uložit do Oblíbených"; všechny položky mají podtitulek s adresou.

### Nápady na zrychlení a pohodlí (zatím nezařazené)
- **Rychlost:** vytvářet WebView jen pro aktivní kartu (ostatní až při prvním zobrazení); náhledy stránek ukládat jen když jsou starší než pár dní; našeptávač s cache a krátkým zpožděním při psaní; úvodní animaci zapínat jen při prvním spuštění / volitelně vypnout.
- **Pohodlí:** volba vyhledávače (Google / Seznam / DuckDuckGo); skrývání lišt při posouvání stránky; pamatování verze pro počítač podle webu; tmavý režim stránek; dvojklepnutí na dolní lištu = nahoru na stránce; čtecí režim.
- **Údržba:** rozdělit `MainActivity.kt` (4 700 řádků) na menší části (karty, tlačítka, screenshoty, informace o stránce).

### Další krok
Sestavit, projít všechny nové obrazovky a opravit chyby; pak podle [todolist.md](todolist.md): VPN, profily, O aplikaci.
