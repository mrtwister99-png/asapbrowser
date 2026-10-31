# Ikonky v aplikaci ASAP Browser

Přehled všech ikon: kde jsou, jak vypadají a odkud se berou. Ikony funkcí tlačítek jsou emoji převedená na šedobílé odstíny (viz `GlyphIcons` v `AppSettings.kt`).

## 1) Vektorové ikony (`res/drawable/`)

| Soubor | Co je | Kde se používá |
|---|---|---|
| `ic_home.xml` | Domeček | Tlačítko 1 (Domů) ve spodní liště |
| `ic_bookmark.xml` | Záložka | Tlačítko 3 (Oblíbené) ve spodní liště |
| `ic_settings.xml` | Ozubené kolečko (bílé) | Úvodní animace tlačítka Nastavení (kolečko → logo) |
| `ic_shield_check.xml` | Štít s fajfkou | Tlačítko 7 – AdBlock (vlevo nahoře) |
| `ic_wifi.xml` | Wi‑Fi vlnky | Tlačítko 8 – VPN (vpravo nahoře) |
| `ic_lock.xml` | Zámek | Adresní řádek (zabezpečené spojení) |
| `ic_globe.xml` | Zeměkoule | Adresní řádek (pravá strana) |
| `ic_search.xml` | Lupa | Spodní vyhledávací pole |
| `ic_add.xml` | Plus | Zatím nepoužito v kódu |
| `ic_launcher_foreground.xml`, `ic_launcher_background.xml` | Pozadí/popředí ikony appky | Adaptivní ikona aplikace |

## 2) Pozadí tlačítek a polí (tvary)

| Soubor | Vzhled | Použití |
|---|---|---|
| `btn_circle_dark.xml` | Tmavý zaoblený čtverec (#2A2A2A) s bílým okrajem | Tlačítka 1–4, 7, 8 a tlačítka v levé liště |
| `side_button_bg.xml` | Tmavý zaoblený čtverec (#1E1E1E) s bílým okrajem | Boční tlačítka 5, 6 (když nejsou kulička) a přidaná 9–12 |
| `btn_adblock_on.xml` | Zelený rámeček AdBlocku | Tlačítko 7 |
| `bg_search_white.xml` | Světle šedé pole (#D9D9D9), zelený okraj při zaostření | Spodní vyhledávací pole a tlačítko Nastavení |
| `bg_url_dark.xml` | Tmavé pole adresy | Horní adresní řádek |
| `bg_badge.xml` | Malý odznak (ON/OFF, počet blokací) | AdBlock a VPN |
| `bg_bar_dark.xml` | Tmavé pozadí lišty | Zatím nepoužito v kódu |

## 3) Obrázky a loga

| Soubor | Co je | Použití |
|---|---|---|
| `drawable-nodpi/asap_logo.png` | Logo „A + planeta" | Tlačítko Nastavení (výchozí funkce) |
| `drawable-nodpi/asap_logo_full.png` | Celé logo ASAP BROWSER | Úvodní obrazovka po spuštění |
| `drawable-nodpi/ic_launcher_logo.png` | Logo pro ikonu appky | Adaptivní ikona (`mipmap-anydpi-v26`) |
| `mipmap-*/ic_launcher*.webp` | Ikona aplikace (různá rozlišení, kulatá i čtvercová) | Launcher |

## 4) Systémové ikony Androidu

| Ikona | Použití |
|---|---|
| `android.R.drawable.ic_media_play` (▶) | Výchozí ikona tlačítka 5 (boční lišta) |
| `android.R.drawable.ic_menu_info_details` | Základ tlačítka 6, když není kulička (zakryje ji ikona funkce) |

## 5) Ikony nakreslené kódem

- **Tlačítko 2 (Karty):** čtvereček s číslem karet (kreslí `tvTabCount`, rámeček `tabCountBg`).
- **Kulička gest (tlačítko 6):** živá animovaná kulička (`GestureOrbView`) – šedá → zelená, „dýchá", sršící jiskry.
- **Plocha gest:** číselná klávesnice 1–9 nebo kuličky 3×3.
- **Odznaky:** „ON/OFF" u AdBlocku a VPN, číslo zablokovaných požadavků.
- **Levá lišta (z emoji, bíle):** 📷 screenshot, 📤 sdílet odkaz, `QR` (text), 🖥 verze pro počítač, 🔎 najít na stránce.

## 6) Textové symboly v rozhraní

`✕` zavřít/smazat, `✓` vybráno / úspěch gesta, `✗` neznámé gesto, `›` pokračovat, `+` přidat / nová karta, `?` nápověda exportu.

## 7) Ikony funkcí tlačítek a gest (emoji → bílé)

Zobrazují se ve volbě funkce (Nastavení → Tlačítka / Gesta), v náhledu tlačítek a na tlačítkách s vlastní funkcí.

| Ikona | Funkce | Skupina |
|---|---|---|
| ∅ | Žádná funkce | – |
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
