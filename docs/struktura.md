# Struktura projektu – ASAP Browser

Přehled složek a souborů: **co kde leží**. Jak věci spolu fungují je v [architecture.md](architecture.md), co se kdy dělalo v [zapisovnik.md](zapisovnik.md), co zbývá v [todolist.md](todolist.md).

Balíček: `com.example.actimelteambrowser` · Kotlin · View (XML) + Jetpack Compose · WebView · Rust jádro (AdBlock) · minSdk 24.

## Kořen projektu

```
/ (root)
├─ build.gradle.kts, settings.gradle.kts, gradle.properties, gradlew(.bat)   Gradle
├─ local.properties            cesta k Android SDK (lokální, nedávat do gitu)
├─ README.md                   popis aplikace pro lidi
├─ docs/                       dokumentace (tento soubor, architecture, zapisovnik, todolist, FUNKCE, ICONS)
├─ app/                        Android modul (vše podstatné)
└─ rust-enginee/               Rust jádro (AdBlock, JNI most)
```

## app/ – Android modul

```
app/build.gradle.kts           závislosti, compileSdk/targetSdk
app/src/main/
├─ AndroidManifest.xml         oprávnění (internet, síť, vibrace; poloha/kamera/mikrofon jen na žádost webu), aktivity, ExportFileProvider
├─ assets/                     easylist.txt, easyprivacy.txt (výchozí seznamy reklam)
├─ jniLibs/<abi>/libmyytbrowser_engine.so   přeložené Rust jádro (arm64-v8a, armeabi-v7a, x86_64)
├─ keepRules/rules.keep        ProGuard/R8 pravidla
├─ java/com/example/actimelteambrowser/ …  (viz níže)
└─ res/                        layout, drawable, mipmap, raw (zvuky), values (barvy, texty, témata)
```

### Kotlin kód (`java/com/example/actimelteambrowser/`)

| Soubor | Co v něm je |
|---|---|
| `MainActivity.kt` (~3 830 řádků) | **Hlavní okno.** Karty a WebView, adresní řádek a našeptávač, tlačítka 1–12 a jejich funkce (`performFunction`), animace tlačítek, levá lišta, screenshoty, informace o stránce, sdílení, oprávnění webů, konfigurační módy tlačítek a gest, úvodní stránka nové karty, ukládání a obnova karet, úvodní animace |
| `RegionSelectView.kt` | Výběr části obrazovky pro screenshot (kreslení obdélníku s úchyty) |
| `StarBurstView.kt` | Animace hvězdy Oblíbených (zlatá hvězda + jiskry) |
| `GestureOrbView.kt` | Živá kulička na tlačítku s funkcí Gesta |
| `IntroView.kt` | Úvodní animace: šedé logo se zviditelní a 2× glitchne do plného loga (`sede_logo.png` → `asap_logo_full.png`) |
| `DownloadFillDrawable.kt` | Zelené plnění tlačítka podle průběhu stahování |
| `MainScreenshots.kt` | Screenshoty: nabídka, výřez, celá stránka, uložení a náhled (rozšíření `MainActivity`) |
| `MainGestures.kt` | Gesta: plocha, kulička a konfigurační mód gest (rozšíření `MainActivity`) |
| `MainAdBlock.kt` | AdBlock: pravidla, kosmetické filtry, YouTube, počítadlo, cookie lišty (rozšíření `MainActivity`) |
| `UfoFieldView.kt` | Animované pozadí „UFO Field“ (roj UFO, ohňostroje, odpuzovač prstu, portály); používá ho Hodnocení a chyby a O aplikaci |
| `BrowserTabs.kt` | `BrowserTab` (jedna karta: WebView, adresa, náhled, favicon, počítadlo reklam) a `BrowserTabs` (seznam karet + aktivní) |
| `SettingsActivity.kt` | Okno Nastavení (Compose host), export/import, `ExportFileProvider` (sdílení souborů z cache) |

### `ui/` – obrazovky v Compose

| Soubor | Co v něm je |
|---|---|
| `SettingsScreens.kt` | Kořen Nastavení: `Route`, `SettingsApp`, hlavní seznam, společné prvky (`ScreenScaffold`, `StyledDialog`, `SettingsRow`, `SwitchRow`, `GlyphIcon`) |
| `SettingsBookmarks.kt` | Záložky: seznam složek a detail složky |
| `SettingsHistory.kt` | Historie (s náhledy stránek), `SiteThumb` |
| `SettingsDownloads.kt` | Stahování |
| `SettingsButtons.kt` | Nastavení tlačítek, výběr funkce, `ButtonConfigModal` |
| `SettingsGestures.kt` | Plocha gest (`GestureSurface`, `GestureOverlay`), `GesturesScreen`, editor plochy a panel gest |
| `SettingsOther.kt` | Konfigurace (animace, zvuky, vibrace, vyhledávač, úklid) |
| `SettingsTutorials.kt` | Tutoriály (`TUTORIALS`) a ukázkové animace |
| `SettingsAbout.kt` | O aplikaci (s pozadím UFO Field) |
| `SettingsFeedback.kt` | Hodnocení a hlášení chyb (otevírá se držením tlačítka Nastavení), `UfoBackground`, `FeedbackConfig` (adresa serveru) |
| `TabsScreen.kt` | Přehled karet (výběr, složky, barvy, přetahování) a **úvodní stránka nové karty** (`StartPage`, počítadlo reklam, nejnavštěvovanější / oblíbené) |
| `SearchSuggestions.kt` | Našeptávač (aktuální stránka zeleně + kopírovat, nejnavštěvovanější, historie, záložky) |
| `components/`, `main/`, `favorites/` | Zatím prázdné zástupné soubory (`TopBar`, `BottomBar`, `SidePanel`, `BrowserViewModel`, `BrowserScreen`, `FavoritesManager`) |

### `core/` – logika bez obrazovek

| Složka / soubor | Co dělá |
|---|---|
| `settings/AppSettings.kt` | **Všechna nastavení** (SharedPreferences), seznam `ButtonFunction` (funkce tlačítek a gest), `ButtonDefaults`, `Gestures`, `SiteRules` (výjimky AdBlocku a oprávnění webů), `fixButtons()` (kontrola pravidel tlačítek) |
| `settings/BackupManager.kt` | Export a import všeho do jednoho JSON souboru (nastavení, záložky, historie, karty, seznamy reklam) |
| `adblock/AdBlocker.kt`, `AdBlockRules.kt` | Pravidla v Kotlinu (rychlé bloky, YouTube skript) |
| `adblock/ListUpdater.kt` | Stahování nových EasyList / EasyPrivacy |
| `adblock/CookieReject.kt` | Skript pro automatické odmítnutí cookie lišt |
| `engine/RustBridge.kt` | JNI most do Rust jádra (`libmyytbrowser_engine.so`) |
| `gestures/GestureHandler.kt` | Zabrnění a zvuk po úspěšném gestu |
| `vpn/VpnManager.kt`, `ActimelVpnService.kt` | Zatím jen kostra (VPN je v rekonstrukci) |

### `data/` – ukládání dat

| Soubor | Co dělá |
|---|---|
| `BrowserDb.kt` | SQLite (`actimelteam.db`): historie a záložky, složky |
| `BookmarksRepository.kt`, `HistoryRepository.kt` | Přístup k datům (volat z pozadí) |
| `Models.kt` | `HistoryVisit`, `Bookmark`, `BookmarkFolder`, `MostVisitedSite` |
| `DownloadsStore` (v `Models.kt`) | Seznam stahování |
| `AppDatabase.kt`, `Favorite.kt` | Pozůstatek po plánovaném Room, nepoužívá se |

## Prostředky (`res/`)

| Složka | Obsah |
|---|---|
| `layout/activity_main.xml` | Hlavní okno: horní lišta (AdBlock, adresa se zámkem a Sdílet, VPN), web, dolní lišta (Domů, hledání, Karty, Oblíbené, Nastavení), boční tlačítka 5 a 6, ComposeView plochy gest a přehledu karet |
| `drawable/ic_*.xml` | **Sada ikon (bílé vektory, 24×24, čára 0,8):** funkce tlačítek (`ic_home`, `ic_back`, `ic_adblock`, `ic_gestures`, …), `ic_screenshot`, `ic_screenshot_quick`. Starší ikony (`ic_bookmark`, `ic_shield_check`, `ic_wifi`, `ic_lock`, `ic_globe`, …) a tvary tlačítek (`btn_circle_dark`, `side_button_bg`, `bg_*`) |
| `drawable-nodpi/` | Loga: `asap_logo`, `asap_logo_full`, `ic_launcher_logo` |
| `mipmap-*/` | Ikona aplikace |
| `raw/` | `orb.mp3` (zvuk gesta), `gotmyphoto.mp3` (zvuk screenshotu) |
| `values/`, `values-night/` | Barvy, texty, téma (`themes.xml` – tady je vypnutá systémová úvodní ikona) |

## Data na zařízení (`filesDir`)

| Cesta | Co tam je |
|---|---|
| `tab_previews/` | Náhledy otevřených karet (přepisují se při každém uložení) |
| `site_thumbs/<domena>.jpg` | Náhledy stránek pro dlaždice nové karty, Historii a Oblíbené |
| `site_icons/<domena>.png` | Ikony webů (favicony) |
| SharedPreferences `actimelteam_settings` | Všechna nastavení (viz `AppSettings`) |
| `actimelteam.db` | Záložky a historie |

## rust-enginee/ – Rust jádro

```
rust-enginee/
├─ Cargo.toml, Cargo.lock
├─ build_android.ps1 / .sh        překlad přes cargo-ndk, kopie .so do app/src/main/jniLibs
└─ src/
   ├─ lib.rs                      vstup knihovny
   ├─ adblock.rs                  filtrování požadavků (EasyList), kosmetické filtry
   ├─ youtube_adblock.rs          CSS a skript pro YouTube
   ├─ network.rs, html_parser.rs, css_parser.rs, dom.rs, style.rs, layout.rs, js_engine.rs   základy vlastního vykreslování (zatím nepoužité)
   ├─ bridge/jni_bridge.rs        funkce volané z Kotlinu (RustBridge)
   └─ security/anti_fingerprint.rs  skripty proti sledování (připravené, zatím nepoužité)
```

> **Poznámka:** složka `rust-enginee/rust/` obsahuje další kopii stejných zdrojáků a build skriptů jako `rust-enginee/src`. Vypadá jako pozůstatek – před úklidem ověřit, která verze se opravdu překládá.

## Dokumentace (`docs/`)

| Soubor | Účel |
|---|---|
| `struktura.md` | Tento přehled |
| `architecture.md` | Jak aplikace funguje uvnitř |
| `zapisovnik.md` | Deník: co se udělalo po každé session |
| `todolist.md` | Co zbývá |
| `FUNKCE.md` | Seznam funkcí pro uživatele (přehled funkcí a výchozí gesta) |
| `ICONS.md` | Přehled ikon (**zastaralé** – ikony funkcí už nejsou emoji, ale vektory ze sady; aktualizovat) |
