# To-do list

Co zbývá udělat. Hotové věci se přesouvají do [zapisovnik.md](zapisovnik.md).

## 1. VPN
- [ ] Skutečná VPN místo přepínače (tlačítko 8, odznak ON/OFF je hotový)
- [ ] `ActimelVpnService` + `VpnManager` (teď jen kostra)
- [ ] Nastavení VPN (server / země) a stav připojení
- [ ] VPN zahrnout do exportu a importu nastavení
- [ ] Ověřit oprávnění a pravidla pro VPN aplikace na Google Play

## 2. Profily
- [ ] Samostatné sady nastavení (rozložení tlačítek, gesta, AdBlock výjimky)
- [ ] Oddělené záložky a historie podle profilu
- [ ] Oddělené cookies / přihlášení (vlastní profil WebView jako u anonymních karet)
- [ ] Přepínání profilů + profil v exportu a importu

## 3. O aplikaci
- [x] Obrazovka „O aplikaci" v Nastavení (název, verze, popis) – hotovo; zbývá doplnit odkazy / kontakt
- [ ] Úvodní průvodce nastavením (intro)
- [x] Ukázkové animace pro uživatele (Nastavení → Tutoriály: tlačítka, gesta, složky karet + obecná ukázka u ostatních funkcí)
- [ ] Verze pro Google Play: odlehčená „příchuť" z plné verze

## 4. O poznamky
## 5. 0 panel oblibenych

## 6. Dluhy a vylepšení (dohodnuto 2026-10-30)
- [x] Baseline Profile (`profileinstaller` + ruční `baseline-prof.txt`) – hotovo; později vygenerovat přesnější z měření
- [x] Rozdělit `SettingsScreens.kt` na 9 souborů (hotovo)
- [x] Rozdělit `MainActivity.kt` (5 014 → ~970 řádků) – hotovo
- [x] Logika složek karet do `TabManager.kt` (hotovo; `ViewModel` ne: únik paměti přes `WebView`)
- [ ] Jednotkové testy `TabManager` do `app/src/test` (logika je čistá, test je připravený)
- [ ] Ověřit release APK (R8 pravidla, AdBlock přes Rust)
- [ ] Verze pro Google Play (odlehčená příchuť) – změnit `applicationId` (`com.example.*` Play nepřijme)

## 7. Hodnocení a hlášení chyb (server)
- [ ] Supabase: tabulka `feedback` (soukromá) + Edge Function `submit-feedback` (kontrola délky a četnosti, uložení, e-mail přes Resend)
- [ ] Doplnit `FeedbackConfig.FUNCTION_URL` a `ANON_KEY`; klíč od Resendu a servisní klíč jen v tajných proměnných funkce
- [ ] Rozhodnout: recenze jen pro autora, nebo později veřejné (se schvalováním)

