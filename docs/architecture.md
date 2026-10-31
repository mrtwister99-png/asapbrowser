# Architektura – ASAP Browser

Jak aplikace funguje uvnitř. Kde co leží je v [struktura.md](struktura.md).

## 1. Celkový obraz

```
┌──────────────────────────── MainActivity (View + ConstraintLayout) ────────────────────────────┐
│  horní lišta   │  web (WebView ve webContainer) / úvodní stránka (Compose)  │  dolní lišta       │
│  boční tlačítka 5, 6 + přidaná 9–12  │  ComposeView: plocha gest, přehled karet, našeptávač      │
└──────────┬─────────────────────────────────────────────────────────────┬───────────────────────┘
           │ Settings (SettingsActivity, Compose)                          │ core/ + data/
           ▼                                                               ▼
   Záložky · Historie · Stahování · Export/Import          AppSettings · BackupManager · BrowserDb
                                                           AdBlocker (Kotlin) ──JNI──▶ Rust jádro
```

- **UI je smíšené:** lišty, tlačítka a web jsou klasické View (XML + kód v `MainActivity`), nové obrazovky (Nastavení, přehled karet, úvodní stránka, našeptávač, plocha gest) jsou v Compose a vkládají se přes `ComposeView`.
- **Stav je v paměti** (seznam karet, skupiny, stavy Compose) a **v SharedPreferences** (nastavení). Záložky a historie jsou v SQLite.
- **Vlákna:** UI vlákno pro vše viditelné; SQLite, soubory a síť běží v `thread { }`. `shouldInterceptRequest` (AdBlock) běží na vlákně WebView.

## 2. Karty

- `BrowserTab` = jeden `WebView` + metadata. `BrowserTabs` drží `list` a `activeId`; zobrazená je vždy jedna karta (její WebView je v `webContainer`).
- **Líné WebView:** `BrowserTab.webView` se vytvoří až při prvním použití (`buildWebView`); obnovené karty na pozadí WebView nemají. Kód, který by ho jen kvůli úklidu vytvořil, kontroluje `webViewCreated`.
- **Anonymní karta:** vlastní profil WebView (`INCOGNITO_PROFILE`), bez historie, bez náhledů, `FLAG_SECURE`; po zavření poslední se data smažou.
- **Ukládání a obnova:** `saveTabs()` (v `onPause`) zapíše adresy, názvy, náhledy a **složky**; `restoreTabs()` karty vytvoří líně (`pendingUrl`) a složky obnoví.
- **Složky karet:** `TabGroup(id, members, color)` v paměti; v přehledu se složka ukáže jako jedna dlaždice na místě své první karty. Složka s méně než 2 kartami zaniká (`pruneGroups`).
- **Přehled karet** (`TabsScreen`): dlouhé držení za hlavičku = tažení (na hlavičku jiné karty vznikne složka, na tělo se karta přesune, nad mřížkou ve složce se vyjme), držení za tělo = výběr více karet a hromadné zavření s potvrzením.

## 3. Tlačítka a funkce

- `ButtonFunction` (enum v `AppSettings.kt`) je **jediný seznam funkcí**, které jde dát tlačítku nebo gestu. Každá má klíč, popisek, emoji (záloha) a vektorovou ikonu (`iconRes`).
- `MainActivity.performFunction(fn)` funkci provede. „Otevírací" funkce (Nastavení, Karty, Domů…) jdou přes **zámek proti dvojitému otevření** (0,9 s).
- Tlačítka **1–4 jsou pevná** (výchozí funkce, vlastní animace při klepnutí a držení). 5–8 lze skrýt, 7 a 8 mají jen zap/vyp funkce, 9–12 jsou přidaná boční (max 2 + 2).
- **Pravidla** hlídá `AppSettings.fixButtons()` (volá se při startu a po importu) a `ButtonConfigModal` při přiřazení: Gesta a Boční lišta max 1×, Boční lišta jen na 5/6, Gesta nikdy na 7/8, Gesta a Boční lišta ne na jednom tlačítku.
- **Konfigurační mód tlačítek:** Nastavení → Tlačítka zavře Nastavení a hlavní okno přejde do módu (ztmavení, tlačítka reagují na klepnutí otevřením modalu, „+" nad bočními tlačítky, uprostřed zelené přerušované „Uložit a zavřít nastavení").

## 4. Gesta

- Plocha 3×3 (`GestureSurface`): kód je posloupnost kláves 1–9 (9 vpravo dole). Dotyk může začít na kuličce (tlačítko s funkcí Gesta) a pokračovat na ploše (`ExternalTouch`).
- Kuličku kreslí `GestureOrbView` pod tlačítkem; samotné tlačítko je průhledné.
- **Poloha a velikost plochy:** `AppSettings.gestureRect` = `"l,t,r,b"` v tisícinách okna (prázdné = výchozí místo vpravo dole). **Viditelnost:** `gestureOpacity` (0–100 %, gesta fungují i při 0).
- **Konfigurační mód gest:** Nastavení → Gesta zavře Nastavení; hlavní okno schová vše kromě webu, kuličky a plochy. `GestureAreaEditor` kreslí plochu s uchyty (8 bodů na okrajích a rozích; tah za uchyt = velikost, tah uvnitř = poloha), shora se vysune scrollovací `GesturePanel` (styl, viditelnost, odezva, seznam gest).

## 5. AdBlock

1. `shouldInterceptRequest` → `adBlockFor(pageUrl)` (zapnuto a pro web není výjimka) → `shouldBlockRequest` (Kotlin pravidla + Rust `shouldBlock` nad EasyList/EasyPrivacy) → prázdná odpověď a započtení do počítadel (karta + celkem).
2. Kosmetické filtry: po načtení se vloží CSS z Rustu (`cosmeticCss`) a skript, který podle tříd/id doptává `CosmeticBridge`.
3. YouTube: samostatný skript (`youtubeScript`). Cookie lišty: `CookieReject`.
4. **Výjimky podle webu:** `SiteRules.adBlockOff(host)`, nastavují se v Informacích o stránce (zámek); na zámku svítí tečka.
5. Seznamy se aktualizují přes `ListUpdater` (soubory ve `filesDir` mají přednost před `assets`).

## 6. Adresní řádek a stránka

- **Zámek:** zelený (HTTPS), červený (HTTP), šedý (interní). Klepnutí otevře **Informace o stránce**: zabezpečení a certifikát, AdBlock (zablokováno, výjimka), oprávnění webu (poloha, kamera, mikrofon), smazání cookies.
- **Sdílet** (vpravo): přepínač Odkaz / QR kód, Sdílet a Kopírovat.
- **Oprávnění webů:** `WebChromeClient.onPermissionRequest` / `onGeolocationPermissionsShowPrompt` → `handleSitePermission` (uložená volba v `SiteRules`, jinak dotaz) → systémové oprávnění Androidu až ve chvíli žádosti.
- **Našeptávač:** při prázdném poli je nahoře (u horního pole) nebo dole (u dolního pole) zelený řádek s aktuální stránkou, ikonou webu a kopírováním; nejnavštěvovanější ukazují 3 řádky a posouvají se.

- **Skrývání lišt:** `onWebScrolled` / `setChromeHidden` schová při čtení horní a dolní lištu i boční tlačítka; **vyhledávač** je v `SearchEngines`; **verze pro počítač** se pamatuje podle webu (`SiteRules.desktopOn`).
- **Konfigurace** (Nastavení, dole): animace, zvuky, vibrace, vyhledávač, skrývání lišt, úklid náhledů.

## 7. Nová karta (úvodní stránka)

`StartPage` (Compose) se vkládá do `webContainer`: tlačítka Normální / Anonymní, počítadlo zablokovaných reklam (celkem, 40 % šířky), přepínač Nejnavštěvovanější / Oblíbené a dlaždice s náhledem a ikonou webu.

- Náhledy: `capturePreview` (PixelCopy) → `site_thumbs/<domena>.jpg`, voláno při odchodu z karty a 3 s po načtení stránky.
- Ikony: `onReceivedIcon` → `site_icons/<domena>.png`.
- Počítadlo: `totalBlocked` (paměť) se do nastavení ukládá po 20 a při pauze.

## 8. Screenshoty

Nabídka (`showScreenshotChooser`): **celá obrazovka** (PixelCopy okna), **výběr části** (`RegionSelectView`: nakreslit, doladit úchyty, Uložit), **celá stránka** (`captureFullPage`: posouvá WebView po kouscích, každý vyfotí PixelCopy a slepí; max 12 000 px). Ukládá se do `Pictures/ASAP Browser`; po uložení se ukáže malý náhled, který otevře galerii.

## 9. Nastavení, export a import

- Všechna nastavení jsou `Setting<T>` v `AppSettings`; seznam `all` určuje, co se **exportuje**. Nové nastavení = jeden řádek definice + přidat do `all`.
- `BackupManager.exportJson` zapisuje: nastavení, složky a záložky, historii, otevřené karty (s náhledy), stažené seznamy reklam. Import bere jen známé klíče se správným typem.
- Do exportu patří i `adblock_exceptions`, `site_permissions`, `total_blocked`, `start_show_favorites`, `gesture_rect`, `gesture_opacity`.

## 10. Rust jádro

`rust-enginee` se překládá přes `cargo-ndk` do `libmyytbrowser_engine.so` (3 architektury) a kopíruje do `app/src/main/jniLibs`. Kotlin ho volá přes `RustBridge` (JNI); když knihovna chybí, použijí se jen Kotlin pravidla. Moduly pro vlastní vykreslování (HTML/CSS/JS) zatím nejsou napojené.

## 11. Zásady při úpravách

- Měnit jen domluvené místo; nové věci jako nové řádky v `AppSettings`, ne roztroušeně.
- Volání SQLite a souborů vždy z pozadí.
- Nové ikony funkcí: vektor 24×24, bílá čára 0,8, kulaté konce → `res/drawable`, odkaz v `ButtonFunction`.
- Po úpravě přidat zápis do [zapisovnik.md](zapisovnik.md).
