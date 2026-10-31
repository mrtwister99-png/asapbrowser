package com.example.actimelteambrowser.core.adblock

import android.net.Uri

object AdBlocker {

    // Reklamni a trackovaci site - blokuje se host i vsechny jeho subdomeny
    private val adHosts = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googletagmanager.com",
        "googleadservices.com",
        "googletagservices.com",
        "imasdk.googleapis.com",
        "2mdn.net",
        "criteo.net",
        "criteo.com",
        "hotjar.com",
        "taboola.com",
        "outbrain.com"
    )

    // Casti adresy, ktere se hledaji kdekoliv v URL
    private val adUrlParts = listOf(
        "adservice.google.",
        "facebook.net/tr",
        "facebook.com/tr"
    )

    // Klicova slova - hleda se jen v hostu a ceste, ne v parametrech za otaznikem
    private val adKeywords = listOf(
        "/ads/",
        "/ad_",
        "advert",
        "banner",
        "popup"
    )

    // YouTube a Google CDN - tady se obecna klicova slova NEPOUZIVAJI (rozbilo by to web),
    // blokuji se jen konkretni reklamni cesty
    private val youtubeHosts = listOf(
        "youtube.com",
        "youtu.be",
        "googlevideo.com",
        "ytimg.com",
        "ggpht.com",
        "googleusercontent.com"
    )

    private val youtubeAdPaths = listOf(
        "/pagead/",
        "/api/stats/ads",
        "/ptracking",
        "/get_midroll_",
        "/ad_data_204",
        "/ad_break"
    )

    private fun hostMatches(host: String, domain: String): Boolean {
        return host == domain || host.endsWith(".$domain")
    }

    fun isAd(url: String): Boolean {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path?.lowercase() ?: ""

        if (adHosts.any { hostMatches(host, it) }) return true
        if (adUrlParts.any { url.lowercase().contains(it) }) return true

        if (youtubeHosts.any { hostMatches(host, it) }) {
            return youtubeAdPaths.any { path.contains(it) }
        }

        val hostAndPath = host + path
        return adKeywords.any { hostAndPath.contains(it) }
    }
}
