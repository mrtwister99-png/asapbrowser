# ASAP Browser

Rychlý, příjemný a hezký prohlížeč pro Android, se kterým se pracuje jednoduše – a přitom se dá
do detailu nastavit. Postavený na WebView (Kotlin) s vlastním jádrem v Rustu pro blokování reklam.

**Hlavní myšlenka:** jednoduché ovládání na povrchu, propracovaná konfigurace pod ním.

## Hlavní vlastnosti

| Oblast | Co umí |
|---|---|
| **AdBlock** | EasyList + EasyPrivacy v Rust enginu, kosmetické filtry (skrývání reklam na stránce), speciální vyřezávání reklam na YouTube, automatické odmítnutí cookie lišt, automatická aktualizace seznamů |
| **VPN** | Přepínač ve vrchní liště (zatím v rekonstrukci – napojení na skutečnou VPN se připravuje) |
| **Gesta** | Plocha pro gesta vyvolaná tlačítkem 6 (kulička). Kód se zadává přejetím i klepáním na mřížku 3×3 (číselná klávesnice nebo „kuličky"). Každé gesto spouští libovolnou funkci. Po úspěšném gestu drobné zabrnění a zvuk (`orb.mp3`) – obojí jde vypnout |
| **Plně přizpůsobitelné UI** | Funkce tlačítek (klepnutí i podržení), přidání bočních tlačítek, vysouvací/pevná tlačítka, vzhled a velikost plochy gest, vlastní kódy gest |
| **Karty** | Přehled karet s náhledy, anonymní karty (bez historie), přesouvání karet, obnovení karet po spuštění, líné načítání obnovených karet |
| **Záložky a historie** | Složky a podsložky, rychlé přidání do oblíbených, hledání, našeptávač |
| **Nová karta** | Úvodní stránka: zelené tlačítko „Normální surfování", modré „Anonymní" a mřížka nejnavštěvovanějších webů (z historie, doplněná obvyklými weby) |
| **Stahování** | Soubory ze stránek se stahují přes systémový DownloadManager (notifikace, složka Stažené). Seznam: Nastavení → Stahování |
| **Dlouhé podržení na stránce** | Na obrázku: otevřít na nové kartě, stáhnout, sdílet. Na odkazu: nová karta, anonymní karta, zkopírovat text / adresu, sdílet |
| **Úvodní logo** | Po spuštění se krátce ukáže logo aplikace a plynule se schová |
| **Levá lišta** | Screenshot (do galerie, s probliknutím a zvukem), sdílení odkazu, QR kód aktuální adresy, verze pro počítač (zap/vyp), najít na stránce |
| **Tlačítka 5–8** | Dají se skrýt. Tlačítka 1–4 jsou stálá. Funkce „Boční lišta" jde jen na tlačítko 5 nebo 6, funkce „Gesta" (kulička) jen na 6; s jinou funkcí je tlačítko 6 běžné boční tlačítko |
| **Pojistka tlačítek** | Aspoň jedno tlačítko vždy otevírá Nastavení – poslední takové nejde přepsat ani odebrat |
| **Export / Import** | Veškeré nastavení, záložky, historie, otevřené karty a seznamy reklam do jednoho souboru. Export do telefonu nebo odeslání na e-mail |

## Přenos na nový telefon

1. **Nastavení → Export → Odeslat na e-mail** – otevře se nový e-mail s přiloženým souborem `asap-browser-nastaveni.json`, stačí vyplnit vlastní adresu a odeslat.
2. Na novém telefonu soubor stáhni.
3. **Nastavení → Import** a vyber soubor.
4. Pokračuješ tam, kde jsi naposledy skončil.

> Soubor obsahuje i historii a otevřené karty – ber ho jako osobní data.

## Gesta

- Tlačítko 6 (kulička) otevře průhlednou plochu vpravo dole.
- Kód: čísla 1–9 (3×3, devítka vpravo dole). Výchozí gesta začínají devítkou.
- Správa: **Nastavení → Gesta** (přidání, přenahrání, zkušební plocha, odezva po gestu).
- Odezva: zabrnění (vibrace) a zvuk `orb.mp3`, každé zvlášť zapnutelné.

## Plánováno

- **Profily** – samostatné sady nastavení (tlačítka, gesta, AdBlock, domovská stránka, záložky, karty), mezi kterými se dá rychle přepínat (např. Práce / Soukromé / YouTube). Každý profil půjde exportovat a importovat zvlášť.
- Skutečné napojení **VPN**.

## Struktura projektu

```
app/src/main/java/com/example/actimelteambrowser/
  MainActivity.kt          hlavní okno, karty, tlačítka, levá lišta
  SettingsActivity.kt      okno Nastavení, export/import, sdílení souborů
  ui/                      Compose obrazovky (nastavení, karty, gesta, našeptávač)
  core/adblock/            pravidla AdBlocku, aktualizace seznamů, cookie lišty
  core/engine/RustBridge   most do Rust enginu (JNI)
  core/gestures/           odezva po gestu (vibrace, zvuk)
  core/settings/           AppSettings (všechna nastavení), BackupManager (export/import)
  data/                    záložky, historie (SQLite)
jedna/                     zdrojáky Rust enginu (build_android.sh / .ps1)
```

## Sestavení

- Android Studio, `minSdk 24`, Kotlin + Compose.
- Rust engine: `jedna/build_android.sh` (nebo `.ps1`) vytvoří `libmyytbrowser_engine.so` do `app/src/main/jniLibs/`.
- Release build používá R8 – pravidla jsou v `app/src/main/keepRules/rules.keep`. Po změnách v JNI nebo JavaScript rozhraní vždy otestuj release build.

## Zvuky

Soubory patří do `app/src/main/res/raw/` (názvy malými písmeny, bez mezer). Když chybí, použije se systémové kliknutí.

- `orb.mp3` – úspěšně dokončené gesto
- `gotmyphoto.mp3` – screenshot (při probliknutí obrazovky)
