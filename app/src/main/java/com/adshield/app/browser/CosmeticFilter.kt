package com.adshield.app.browser

import org.json.JSONObject

/**
 * Cosmetic filtering for the built-in browser: hides leftover ad slots, sponsored widgets and
 * consent walls that survive request blocking. The script is idempotent so it can be injected on
 * every page start and page finish.
 */
object CosmeticFilter {

    private const val STYLE_ID = "adshield-cosmetic"

    private val CSS = """
        [id^="div-gpt-ad"],[id*="google_ads"],[data-ad-slot],[data-ad-client],ins.adsbygoogle,.adsbygoogle,
        .google-ad,.google-ads,.googlead,.ad-slot,.ad-container,.ad-wrapper,.ad-placeholder,.ad-unit,
        [class^="ad-"],[class*=" ad-"],[class$="-ad"],[class*="-ad-"],[class*="ads-"],.ads,.advert,
        .advertisement,.advertising,.sponsored,.sponsored-post,.sponsored-content,.promoted,.partner-box,
        [class*="sponsored"],[class*="advert"],.taboola,.outbrain,.trc_related_container,.ob-widget,
        [id*="taboola"],[id*="outbrain"],[class*="cookie-consent"],[id*="cookie-consent"],
        [class*="cookie-banner"],[id*="cookie-banner"],[class*="cookiebanner"],#onetrust-banner-sdk,
        .cc-window,.cmp-container[aria-hidden="false"]{
            display:none!important;visibility:hidden!important;
            height:0!important;max-height:0!important;margin:0!important;padding:0!important;
        }
    """.trimIndent()

    private val SELECTORS = listOf(
        "ins.adsbygoogle",
        ".adsbygoogle",
        "[id^='div-gpt-ad']",
        "[id*='google_ads']",
        "[data-ad-slot]",
        "[data-ad-client]",
        "iframe[src*='doubleclick.net']",
        "iframe[src*='googlesyndication']",
        "iframe[src*='adservice']",
        "iframe[src*='/ads/']",
        "iframe[id*='google_ads']",
        ".google-ad",
        ".google-ads",
        ".ad-slot",
        ".ad-container",
        ".ad-wrapper",
        ".advertisement",
        "[class*='sponsored']",
        ".taboola",
        ".outbrain",
        ".trc_related_container",
        "[class*='cookie-banner']",
        "[id*='cookie-banner']",
        "#onetrust-banner-sdk",
        ".cc-window"
    )

    fun javascript(): String {
        val cssLiteral = JSONObject.quote(CSS)
        val selectorLiteral = JSONObject.quote(SELECTORS.joinToString(","))
        return """
            (function(){
              try {
                if (!document.getElementById(${JSONObject.quote(STYLE_ID)})) {
                  var style = document.createElement('style');
                  style.id = ${JSONObject.quote(STYLE_ID)};
                  style.type = 'text/css';
                  style.textContent = $cssLiteral;
                  (document.head || document.documentElement).appendChild(style);
                }
              } catch (e) {}
              var selectors = ($selectorLiteral).split(',');
              var sweep = function(){
                for (var i = 0; i < selectors.length; i++) {
                  try {
                    var nodes = document.querySelectorAll(selectors[i]);
                    for (var j = 0; j < nodes.length; j++) {
                      if (nodes[j] && nodes[j].style) {
                        nodes[j].style.setProperty('display', 'none', 'important');
                      }
                    }
                  } catch (e) {}
                }
              };
              sweep();
              if (!window.__adshieldObserver) {
                window.__adshieldObserver = true;
                try {
                  var pending = false;
                  var observer = new MutationObserver(function(){
                    if (pending) { return; }
                    pending = true;
                    setTimeout(function(){ pending = false; sweep(); }, 350);
                  });
                  observer.observe(document.documentElement || document, { childList: true, subtree: true });
                } catch (e) {}
              }
            })();
        """.trimIndent()
    }
}
