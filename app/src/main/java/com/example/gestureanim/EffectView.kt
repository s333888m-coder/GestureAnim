package com.example.gestureanim

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/**
 * HTML टेम्पलेट चलाने वाला WebView.
 *
 * टेम्पलेट में window.onGesture(g) हो तो हर इवेंट पर वही कॉल होगा, g में:
 *   type (TOUCH/SWIPE_UP/.../RULE), dir (up/down/left/right/none — उंगली की दिशा),
 *   xf, yf (0..1 स्क्रीन पोज़िशन), size (1.0 = 100%), duration (ms), color (#RRGGBB),
 *   opacity (0..1), zone (रूल वाले इवेंट में शुरुआती ज़ोन), outcome (रूल का नतीजा)
 * अगर टेम्पलेट में onGesture नहीं है, तो उसे "सादा एनिमेशन" मानकर हर इवेंट पर दोबारा लोड किया जाता है
 * और Size/Opacity ऐप के सेटिंग से लगाए जाते हैं।
 */
@SuppressLint("SetJavaScriptEnabled")
class EffectView(ctx: Context) : WebView(ctx) {
    private var loadedKey: Int? = null
    private var ready = false
    private var pending: String? = null
    private val base = "https://local.app/"

    init {
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                ready = true
                pending?.let { dispatch(it) }
                pending = null
            }
        }
    }

    fun play(type: GestureType, cfg: EventConfig, xf: Float, yf: Float) =
        playRaw(type.name, type.dir, cfg, xf, yf, "", "")

    fun playRaw(
        typeName: String, dir: String, cfg: EventConfig,
        xf: Float, yf: Float, zone: String, outcome: String
    ) {
        val html = Templates.load(context, cfg.template)
        val plain = !html.contains("onGesture")
        if (plain) {
            alpha = cfg.opacity / 100f
            val s = cfg.size / 100f
            scaleX = s
            scaleY = s
            pivotX = xf * width
            pivotY = yf * height
            ready = false
            loadedKey = null
            loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
            return
        }
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
        val json = JSONObject()
            .put("type", typeName)
            .put("dir", dir)
            .put("xf", xf.toDouble())
            .put("yf", yf.toDouble())
            .put("size", cfg.size / 100.0)
            .put("duration", cfg.duration)
            .put("color", cfg.color)
            .put("opacity", cfg.opacity / 100.0)
            .put("zone", zone)
            .put("outcome", outcome)
            .toString()
        val key = html.hashCode()
        if (loadedKey != key) {
            loadedKey = key
            ready = false
            pending = json
            loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
        } else if (ready) {
            dispatch(json)
        } else {
            pending = json
        }
    }

    private fun dispatch(json: String) {
        evaluateJavascript("window.onGesture && window.onGesture($json)", null)
    }
}
