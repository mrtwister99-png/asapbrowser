package com.example.actimelteambrowser.core.adblock

import android.net.Uri

// Pravidla a skripty pro blokovani reklam
object AdBlockRules {

    // Zatim nepouzivano - misto pro EasyList (30k pravidel)
    var rules: List<String> = emptyList()

    fun loadFromAssets() {
        // TODO: nacist z assets/easylist.txt
    }

    // true = stranka je YouTube (youtube.com, m.youtube.com, youtu.be)
    fun isYoutube(url: String?): Boolean {
        if (url == null) return false
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be"
    }

    // JavaScript vstrikovany do YouTube: vyrizne reklamy z dat prehravace,
    // skryje reklamni prvky a kdyz se reklama presto spusti, preskoci ji
    val youtubeScript: String = """
(function () {
  if (window.__atbYt) return;
  window.__atbYt = true;

  // 1) Vyrez reklam z odpovedi YouTube (JSON.parse, fetch, prvni nacteni videa)
  var AD_KEYS = ['adPlacements', 'playerAds', 'adSlots'];

  function strip(o) {
    try {
      if (!o || typeof o !== 'object') return o;
      for (var i = 0; i < AD_KEYS.length; i++) {
        if (AD_KEYS[i] in o) {
          try { delete o[AD_KEYS[i]]; } catch (e) {}
        }
      }
      if (o.playerResponse && typeof o.playerResponse === 'object') strip(o.playerResponse);
    } catch (e) {}
    return o;
  }

  var origParse = JSON.parse;
  JSON.parse = function () {
    return strip(origParse.apply(this, arguments));
  };

  var API = ['/youtubei/v1/player', '/youtubei/v1/next', '/youtubei/v1/reel'];
  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function (input) {
      var url = (typeof input === 'string') ? input : ((input && input.url) || '');
      var p = origFetch.apply(this, arguments);
      var hit = false;
      for (var i = 0; i < API.length; i++) {
        if (url.indexOf(API[i]) !== -1) hit = true;
      }
      if (!hit) return p;
      return p.then(function (res) {
        return res.clone().text().then(function (t) {
          try {
            var j = strip(origParse(t));
            return new Response(JSON.stringify(j), {
              status: res.status,
              statusText: res.statusText,
              headers: res.headers
            });
          } catch (e) {
            return res;
          }
        });
      });
    };
  }

  try {
    var ipr;
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
      configurable: true,
      get: function () { return ipr; },
      set: function (v) { ipr = strip(v); }
    });
  } catch (e) {}

  // 2) Skryti reklamnich prvku na strance
  var CSS = [
    'ytm-promoted-sparkles-web-renderer',
    'ytm-promoted-video-renderer',
    'ytm-companion-ad-renderer',
    'ytm-ad-slot-renderer',
    'ytm-in-feed-ad-layout-renderer',
    'ytm-banner-promo-renderer',
    'ytm-statement-banner-renderer',
    'ytm-mealbar-promo-renderer',
    'ytm-brand-video-singleton-renderer',
    'ytd-ad-slot-renderer',
    'ytd-display-ad-renderer',
    'ytd-promoted-sparkles-web-renderer',
    '#masthead-ad',
    '#player-ads',
    '.ytp-ad-overlay-container',
    '.ytp-ad-overlay-slot',
    '.ytp-ad-image-overlay',
    '.ytp-ad-text-overlay',
    '.video-ads'
  ].join(',') + '{display:none !important}';

  function addCss() {
    if (document.getElementById('__atb_css')) return;
    var root = document.head || document.documentElement;
    if (!root) return;
    var s = document.createElement('style');
    s.id = '__atb_css';
    s.textContent = CSS;
    root.appendChild(s);
  }

  // 3) Zaloha: kdyz se reklama presto prehrava, preskocit ji
  function tick() {
    addCss();
    var video = document.querySelector('video');
    var player = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
    var adShowing = !!(player && player.classList.contains('ad-showing')) || !!document.querySelector('.ad-showing');

    var skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-skip-button-container button');
    if (skip) {
      try { skip.click(); } catch (e) {}
    }

    if (!video) return;
    if (adShowing) {
      if (!video.__atbPrev) video.__atbPrev = { muted: video.muted, rate: video.playbackRate };
      video.muted = true;
      video.playbackRate = 16;
      if (isFinite(video.duration) && video.duration > 0 && video.currentTime < video.duration - 0.1) {
        video.currentTime = video.duration;
      }
    } else if (video.__atbPrev) {
      video.muted = video.__atbPrev.muted;
      video.playbackRate = video.__atbPrev.rate;
      video.__atbPrev = null;
    }
  }
  setInterval(tick, 300);
})();
""".trimIndent()
}
