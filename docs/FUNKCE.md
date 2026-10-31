# ASAP Browser – přehled funkcí

## Prohlížení
- Rychlé WebView s JavaScriptem a DOM storage, hardwarová akcelerace.
- Adresní pole nahoře (zobrazuje doménu) a vyhledávací pole dole (adresa nebo hledání), **našeptávač** z historie a záložek.
- **Karty**: přehled s náhledy, přesouvání tažením, zavřít vše, vrácení poslední zavřené, duplikace, přepínání předchozí/další, obnovení karet po spuštění (líné načítání).
- **Nová karta** = úvodní stránka: Normální surfování / Anonymní + mřížka nejnavštěvovanějších webů.
- **Anonymní karty**: vlastní profil WebView (oddělené cookies a úložiště od normálních karet, po zavření poslední anonymní karty se smaže), bez historie, bez cache a automatického vyplňování, zákaz screenshotů a náhledu v seznamu aplikací, neukládají se po zavření appky, fialová adresa. Na starším WebView bez podpory profilů se cookies sdílejí.
- Verze pro počítač (zap/vyp pro každou kartu), přiblížení/oddálení, nahoru/dolů na stránce, obnovit, zpět/vpřed.
- **Najít na stránce** (text).
- Celoobrazovkové video.
- Pád rendereru stránky se řeší automaticky (karta se načte znovu), karty na pozadí se pozastavují.

## Dlouhé podržení na stránce
- **Odkaz**: otevřít na nové kartě, v anonymní kartě, zkopírovat text odkazu, zkopírovat adresu, sdílet odkaz.
- **Obrázek**: otevřít na nové kartě, stáhnout, sdílet.
- **Text**: systémový výběr textu.

## Ochrana
- **AdBlock**: EasyList + EasyPrivacy v Rust enginu, vlastní Kotlin pravidla, kosmetické filtry (skrývání reklamních prvků), speciální režim pro YouTube, počítadlo zablokovaných požadavků na stránce, automatická aktualizace seznamů.
- **Automatické odmítnutí cookie lišt.**
- **VPN**: zatím jen přepínač (viz dokumentace VPN – napojení se připravuje).
- Anti‑fingerprint skripty jsou připravené v Rust jádru, ale hlavní okno je zatím nepoužívá.

## Tlačítka a ovládání
- Tlačítka 1–4 (stálá; Nastavení je pevné, Domů má výběr z navigace a Gesta, Karty a Oblíbené ze skupiny Záložky, historie, hledání), 5–8 (lze skrýt), 9–12 (přidaná boční, max. 2 vlevo a 2 vpravo).
- Každé tlačítko: funkce pro **klepnutí** a pro **držení**; boční tlačítka (5, 6, 9–12) jsou **trvale zasunutá** za okraj (50 %, v nastavení tlačítka jde 70 %); klepnutí i držení spustí funkci rovnou. Tlačítka 5–8 jdou vypnout; vypnutá poznáš v nastavení podle velkého čísla.
- Pojistka: aspoň jedno zobrazené tlačítko vždy otevírá Nastavení.
- **Boční lišta** (funkce „Boční lišta" jen na tlačítku 5 = vlevo nebo 6 = vpravo, otevře se na straně tlačítka): screenshot, sdílet odkaz, QR kód, verze pro počítač, najít na stránce.
- **Gesta**: kulička s ohňostrojem je na tlačítku, které má funkci Gesta (výchozí 6, jde dát na kteroukoliv pozici kromě 7 a 8; vždy jen jedna), plocha 3×3 (číselná / kuličky), vlastní kódy gest, zkušební plocha; zabrnění a zvuk po úspěšném gestu (vypínatelné). Funkce Gesta a Boční lišta nesmí být na jednom tlačítku zároveň.
- Animace tlačítka Nastavení po spuštění (kolečko → logo), animace tlačítek Domů/Karty/Oblíbené při držení.

## Záložky, historie, stahování
- Záložky ve složkách a podsložkách, rychlé přidání do Oblíbených, přidání do vybrané složky, ukládání karet do složky.
- Historie s mazáním (dnes, včera, posledních X dní, vše), nejnavštěvovanější weby.
- Stahování přes systémový DownloadManager. Vlastní obrazovka **Nastavení → Stahování**: průběh, otevření souboru, zrušení, odebrání ze seznamu, smazání souboru.
- **Průběh na tlačítkách:** 1. stahování plní zdola nahoru tlačítko 1 (0–100 % zeleně), 2. tlačítko 2 atd. (jen zobrazená tlačítka, bez tlačítka s kuličkou gest).

## Export / Import
- Jeden soubor `asap-browser-nastaveni.json`: nastavení, tlačítka a gesta, záložky, historie, otevřené karty (s náhledy), stažené seznamy reklam.
- Export do telefonu nebo e-mailem (otevře nový e-mail s přílohou); import z libovolného souboru.

## Další
- Úvodní animace při startu (bílé pozadí, šedé logo, glitch do plného loga, hudba, tlačítko Přeskočit vpravo dole), screenshot s probliknutím a zvukem, QR kód aktuální adresy.
- Export a import spolu přenáší celé nastavení na nový telefon.

## Připravuje se
- **Profily** (samostatné sady nastavení, záložek a – s oddělenými cookies – i přihlášení).
- Skutečná **VPN**.

## Všechny funkce, které lze přiřadit tlačítku nebo gestu

| Ikona | Funkce | Skupina |
|---|---|---|
| 🏠 | Domů (domovská stránka) | Navigace a stránka |
| ◀ | Zpět | Navigace a stránka |
| ▶ | Vpřed | Navigace a stránka |
| ⟳ | Obnovit stránku | Navigace a stránka |
| ⬆️ | Nahoru na stránce | Navigace a stránka |
| ⬇️ | Dolů na stránce | Navigace a stránka |
| 🖥 | Verze pro počítač (zap/vyp) | Navigace a stránka |
| 📤 | Sdílet adresu | Navigace a stránka |
| 📋 | Kopírovat adresu | Navigace a stránka |
| 🔎 | Přiblížit | Navigace a stránka |
| 🔍 | Oddálit | Navigace a stránka |
| ▣ | Přehled karet | Karty |
| ➕ | Nová karta | Karty |
| 🕶 | Nové anonymní okno | Karty |
| ✖️ | Zavřít aktuální kartu | Karty |
| 🗑 | Zavřít všechny karty | Karty |
| ⏮ | Předchozí karta | Karty |
| ⏭ | Další karta | Karty |
| ⧉ | Duplikovat kartu | Karty |
| ↩️ | Vrátit poslední zavřenou kartu | Karty |
| ⭐ | Otevřít Záložky | Záložky, historie, hledání |
| 🔖 | Rychle přidat do Oblíbených | Záložky, historie, hledání |
| 📁 | Přidat stránku do složky (výběr) | Záložky, historie, hledání |
| 🕘 | Historie | Záložky, historie, hledání |
| ⌨️ | Hledání (otevřít klávesnici) | Záložky, historie, hledání |
| 🏡 | Nastavení domovské stránky | Záložky, historie, hledání |
| 🛡 | AdBlock zap/vyp | Nástroje a systém |
| 📶 | VPN (zatím v rekonstrukci) | Nástroje a systém |
| ⚙️ | Nastavení | Nástroje a systém |
| 🎛 | Nastavení tlačítek | Nástroje a systém |
| 📦 | Export nastavení | Nástroje a systém |
| 📥 | Import nastavení | Nástroje a systém |
| 🔎 | Najít na stránce | Nástroje a systém |
| ✋ | Gesta (otevřít plochu) | Nástroje a systém |
| ☰ | Boční lišta (otevřít/zavřít) | Nástroje a systém |
| ⏻ | Ukončit aplikaci | Nástroje a systém |

## Výchozí gesta

Kód: čísla 1–9 na mřížce 3×3 (1 2 3 / 4 5 6 / 7 8 9), začíná se devítkou vpravo dole.

| Kód | Funkce |
|---|---|
| 98741 | Nová karta |
| 98747 | Zavřít aktuální kartu |
| 987412369 | Rychle přidat do Oblíbených |
| 9856 | Vpřed |
| 9854 | Zpět |
| 98523 | Další karta |
| 98521 | Předchozí karta |
| 963654147 | Historie |
| 98752 | Nové anonymní okno |

## Hodnocení a hlášení chyb
- Držení tlačítka Nastavení otevře obrazovku s hvězdičkami (1–5), volbou Recenze / Chyba, textem a nepovinným e-mailem; na pozadí běží animace UFO Field (klepnutí = ohňostroj, prst = odpuzovač UFO). Odesílá se na server (Supabase), viz `FeedbackConfig`.
- Stejné pozadí je i v Nastavení → O aplikaci.

## Výchozí prohlížeč
- Nastavení → Výchozí prohlížeč: ASAP Browser se dá nastavit jako výchozí; odkazy http/https z jiných aplikací se pak otevřou na nové kartě.
