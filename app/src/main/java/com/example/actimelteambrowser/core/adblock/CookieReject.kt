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
        ".fc-button.fc-cta-do-not-consent",
        "button#onetrust-pc-btn-handler"
    )

    // Kdy po nacteni stranky skript spustit (lista se casto objevi az pozdeji)
    val delaysMs = longArrayOf(0L, 1500L, 4000L)

    // Idempotentni - klikne jen na viditelna tlacitka
    val script: String = """
        (function () {
          var sels = ${selectors.joinToString(prefix = "[", postfix = "]") { "\"" + it.replace("\"", "\\\"") + "\"" }};
          for (var i = 0; i < sels.length; i++) {
            try {
              var el = document.querySelector(sels[i]);
              if (el && el.offsetParent !== null) el.click();
            } catch (e) {}
          }
        })();
    """.trimIndent()
}
