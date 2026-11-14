package com.example.actimelteambrowser.core.adblock

// Automaticke odmitnuti cookie listy (z puvodniho LinkCleaner)
object CookieReject {

    private val selectors = listOf(
        "#onetrust-reject-all-handler",
        ".cookie-reject-all",
        "#didomi-notice-disagree-button",
        "button[aria-label='Reject all']",
        "button[data-testid='reject-all']",
        "#qc-cmp2-ui button[mode='secondary']",
        ".fc-button.fc-cta-do-not-consent"
        // "button#onetrust-pc-btn-handler" odebrano: to je "Nastaveni cookies" (otevre okno), ne odmitnuti
    )

    // Jeden skript na stranku: sam se zkusi hned, po 1,5 s, 4 s a 8 s (lista se casto objevi az pozdeji)
    // a po prvnim kliknuti skonci. Drive se z Kotlinu posilal 3x a klikal i potom, co uz lista zmizela.
    val script: String = """
        (function () {
          if (window.__atbCookie) return;
          window.__atbCookie = true;
          var sels = ${selectors.joinToString(prefix = "[", postfix = "]") { "\"" + it.replace("\"", "\\\"") + "\"" }};
          var waits = [0, 1500, 4000, 8000];
          function attempt(n) {
            for (var i = 0; i < sels.length; i++) {
              try {
                var el = document.querySelector(sels[i]);
                if (el && el.offsetParent !== null) { el.click(); return; }
              } catch (e) {}
            }
            if (n + 1 < waits.length) setTimeout(function () { attempt(n + 1); }, waits[n + 1] - waits[n]);
          }
          attempt(0);
        })();
    """.trimIndent()
}
