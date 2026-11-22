package com.example.gestureanim

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs
import kotlin.math.max

class GestureService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: GestureService? = null
    }

    private class Active(
        val zone: Zone, val x0: Float, val y0: Float, val cands: List<Rule>
    ) {
        var recognized = false
    }

    private class Pending(
        val owner: Active, val rules: MutableList<Rule>,
        val xf: Float, val yf: Float, val dir: String, var expireAt: Long
    )

    private lateinit var wm: WindowManager
    private var view: EffectView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { view?.visibility = View.GONE }
    private val lastFire = HashMap<String, Long>()
    private var launcherPkg: String? = null
    private var lastPkg: String? = null
    private val hist = ArrayList<String>()
    private var inHome = false

    private var rules: List<Rule> = emptyList()
    private val strips = HashMap<Zone, StripView>()
    private var stripKey = ""
    private var active: Active? = null
    private var pending: Pending? = null

    private val reloadRunnable = Runnable { reload() }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.removeCallbacks(reloadRunnable)
        handler.postDelayed(reloadRunnable, 400)
    }

    // ---------------------------------------------------------------- setup

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        launcherPkg = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName

        view?.let { runCatching { wm.removeView(it) } }
        val v = EffectView(this)
        v.visibility = View.GONE
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        wm.addView(v, lp)
        view = v

        inHome = rootInActiveWindow?.packageName?.toString() == launcherPkg
        getSharedPreferences("cfg", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener)
        reload()
    }

    private fun reload() {
        rules = RuleStore.load(this).filter { it.enabled }
        refreshStrips()
    }

    @Suppress("DEPRECATION")
    private fun screen(): IntArray {
        val dm = DisplayMetrics()
        val d = (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        d.getRealMetrics(dm)
        return intArrayOf(dm.widthPixels, dm.heightPixels)
    }

    private fun ctxOk(c: Ctx, home: Boolean) =
        c == Ctx.ANY || (c == Ctx.HOME && home) || (c == Ctx.APP && !home)

    private fun bounds(z: Zone, w: Int, h: Int, t: Int): IntArray = when (z) {
        Zone.TOP_LEFT -> intArrayOf(0, 0, (w * 0.4f).toInt(), t)
        Zone.TOP_RIGHT -> intArrayOf((w * 0.6f).toInt(), 0, w - (w * 0.6f).toInt(), t)
        Zone.LEFT_EDGE -> intArrayOf(0, t, t, h - 2 * t)
        Zone.RIGHT_EDGE -> intArrayOf(w - t, t, t, h - 2 * t)
        Zone.BOTTOM_LEFT -> intArrayOf(0, h - t, w / 3, t)
        Zone.BOTTOM_CENTER -> intArrayOf(w / 3, h - t, w / 3, t)
        Zone.BOTTOM_RIGHT -> intArrayOf(2 * w / 3, h - t, w - 2 * w / 3, t)
    }

    /** सिर्फ वही ज़ोन की पट्टी लगती है जिसका कोई चालू रूल मौजूदा संदर्भ (होम/ऐप) में लागू है */
    private fun refreshStrips() {
        val s = screen()
        val w = s[0]
        val h = s[1]
        val t = dp(Tuning.stripDp(this))
        val wanted = rules.filter { ctxOk(it.ctx, inHome) }.map { it.zone }.toSet()
        val key = "$w x $h x $t"
        if (key != stripKey) {
            strips.values.forEach { runCatching { wm.removeView(it) } }
            strips.clear()
            stripKey = key
        }
        strips.keys.filter { it !in wanted }.forEach { z ->
            strips.remove(z)?.let { runCatching { wm.removeView(it) } }
        }
        wanted.filter { it !in strips }.forEach { z ->
            val sv = StripView(this, z)
            val b = bounds(z, w, h, t)
            val lp = WindowManager.LayoutParams(
                b[2], b[3],
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = b[0]
            lp.y = b[1]
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            runCatching {
                wm.addView(sv, lp)
                strips[z] = sv
            }
        }
    }

    private fun setHome(b: Boolean) {
        if (b != inHome) {
            inHome = b
            refreshStrips()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        handler.postDelayed({ refreshStrips() }, 300)
    }

    // ------------------------------------------------- पट्टी पर टच / जेस्चर पहचान

    inner class StripView(ctx: Context, private val zone: Zone) : View(ctx) {
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> onStripDown(zone, e.rawX, e.rawY)
                MotionEvent.ACTION_MOVE -> onStripMove(e.rawX, e.rawY)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onStripEnd(e.rawX, e.rawY)
            }
            return true
        }
    }

    private fun onStripDown(zone: Zone, x: Float, y: Float) {
        val cands = rules.filter { it.zone == zone && ctxOk(it.ctx, inHome) }
        active = Active(zone, x, y, cands)
    }

    private fun onStripMove(x: Float, y: Float) {
        val a = active ?: return
        if (a.recognized) return
        val dist = max(abs(x - a.x0), abs(y - a.y0))
        if (dist >= dp(Tuning.sensDp(this))) recognize(a, x - a.x0, y - a.y0)
    }

    private fun onStripEnd(x: Float, y: Float) {
        val a = active ?: return
        // सिस्टम का अपना जेस्चर (back/home) टच को CANCEL कर देता है — तब तक की दूरी से पहचान लेते हैं
        if (!a.recognized && max(abs(x - a.x0), abs(y - a.y0)) >= dp(8)) recognize(a, x - a.x0, y - a.y0)
        pending?.let { if (it.owner === a) it.expireAt = SystemClock.uptimeMillis() + 1200 }
        active = null
    }

    private fun recognize(a: Active, dx: Float, dy: Float) {
        a.recognized = true
        val key = if (abs(dy) >= abs(dx)) (if (dy < 0) "up" else "down") else (if (dx < 0) "left" else "right")
        val s = screen()
        val xf = (a.x0 / s[0]).coerceIn(0f, 1f)
        val yf = (a.y0 / s[1]).coerceIn(0f, 1f)
        val matched = a.cands.filter { it.dir == Dir.ANY || it.dir.key == key }
        var forwarded = false
        val waiting = mutableListOf<Rule>()
        for (r in matched) {
            if (r.forward != Forward.NONE && !forwarded) {
                forwarded = true
                forward(r.forward)
            }
            if (r.outcome == Outcome.ANY) fireRule(r, xf, yf, key) else waiting.add(r)
        }
        pending = if (waiting.isEmpty()) null else Pending(a, waiting, xf, yf, key, Long.MAX_VALUE)
    }

    private fun forward(f: Forward) {
        val action = when (f) {
            Forward.BACK -> GLOBAL_ACTION_BACK
            Forward.HOME -> GLOBAL_ACTION_HOME
            Forward.RECENTS -> GLOBAL_ACTION_RECENTS
            Forward.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            Forward.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
            Forward.NONE -> return
        }
        performGlobalAction(action)
    }

    /** स्वाइप के बाद कोई नतीजा (Home / Recents / ...) दिखा — इंतज़ार करते रूल्स चलाओ */
    private fun onResult(o: Outcome) {
        val p = pending ?: return
        if (SystemClock.uptimeMillis() > p.expireAt) {
            pending = null
            return
        }
        val hit = p.rules.filter { it.outcome == o }
        if (hit.isEmpty()) return
        p.rules.removeAll(hit.toSet())
        hit.forEach { fireRule(it, p.xf, p.yf, p.dir) }
        if (p.rules.isEmpty()) pending = null
    }

    private fun fireRule(r: Rule, xf: Float, yf: Float, dir: String) {
        val now = SystemClock.uptimeMillis()
        val k = "rule:" + r.id
        if (now - (lastFire[k] ?: 0L) < 300L) return
        lastFire[k] = now
        SoundPlayer.play(this, r.cfg.snd)
        if (r.cfg.visual) show(r.cfg.duration) { it.playRaw("RULE", dir, r.cfg, xf, yf, r.zone.name, r.outcome.name) }
    }

    private fun show(duration: Int, play: (EffectView) -> Unit) {
        val v = view ?: return
        handler.removeCallbacks(hideRunnable)
        v.visibility = View.VISIBLE
        play(v)
        handler.postDelayed(hideRunnable, duration + 400L)
    }

    // ---------------------------------------------- Accessibility इवेंट (सामान्य इवेंट + नतीजे)

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        e ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val dm = resources.displayMetrics
                val r = Rect()
                e.source?.getBoundsInScreen(r)
                if (r.isEmpty) {
                    fire(GestureType.TOUCH)
                } else {
                    fire(
                        GestureType.TOUCH,
                        (r.exactCenterX() / dm.widthPixels).coerceIn(0f, 1f),
                        (r.exactCenterY() / dm.heightPixels).coerceIn(0f, 1f)
                    )
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (Build.VERSION.SDK_INT >= 28) {
                    val dx = e.scrollDeltaX
                    val dy = e.scrollDeltaY
                    if (dy != 0 && abs(dy) >= abs(dx)) {
                        fire(if (dy > 0) GestureType.SWIPE_UP else GestureType.SWIPE_DOWN)
                    } else if (dx != 0) {
                        fire(if (dx > 0) GestureType.SWIPE_LEFT else GestureType.SWIPE_RIGHT)
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindow(e)
        }
    }

    private fun handleWindow(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        val cls = e.className?.toString() ?: ""

        if (pkg != "com.android.systemui" && pkg != packageName &&
            (cls.contains("Dialog", true) || cls.contains("Popup", true))
        ) {
            playSystemSound(SKind.POPUP)
            return
        }

        if (cls.contains("Recents", true) || cls.contains("Overview", true)) {
            fire(GestureType.RECENTS)
            onResult(Outcome.RECENTS)
            onResult(Outcome.WINDOW)
            return
        }
        if (pkg == "com.android.systemui") {
            onResult(Outcome.SHADE)
            onResult(Outcome.WINDOW)
            return
        }
        if (pkg == packageName) return
        if (cls.startsWith("android.") || cls.startsWith("androidx.")) return
        if (pkg.contains("inputmethod") || pkg.contains("keyboard")) return

        if (pkg == launcherPkg) {
            if (lastPkg != pkg) {
                fire(GestureType.HOME)
                onResult(Outcome.HOME)
            }
            onResult(Outcome.WINDOW)
            setHome(true)
            lastPkg = pkg
            hist.clear()
            return
        }

        val id = "$pkg/$cls"
        if (pkg != lastPkg) {
            fire(GestureType.APP_OPEN)
            onResult(Outcome.APP_OPEN)
            hist.clear()
        } else if (hist.size >= 2 && hist[hist.size - 2] == id && hist.last() != id) {
            // एक ही ऐप में पिछली स्क्रीन पर लौटना = Back (अंदाज़ा)
            fire(GestureType.BACK)
            onResult(Outcome.BACK)
            onResult(Outcome.WINDOW)
            hist.removeAt(hist.size - 1)
            return
        }
        onResult(Outcome.WINDOW)
        setHome(false)
        lastPkg = pkg
        if (hist.lastOrNull() != id) hist.add(id)
        while (hist.size > 6) hist.removeAt(0)
    }

    private fun playSystemSound(k: SKind) {
        val now = SystemClock.uptimeMillis()
        if (now - (lastFire["snd:" + k.name] ?: 0L) < 600L) return
        lastFire["snd:" + k.name] = now
        val r = SoundStore.load(this).firstOrNull { it.kind == k && it.snd.enabled } ?: return
        SoundPlayer.play(this, r.snd)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            fire(GestureType.BACK)
            onResult(Outcome.BACK)
        }
        return false
    }

    fun fire(t: GestureType, xf: Float = t.xf, yf: Float = t.yf) {
        val cfg = Prefs.get(this, t)
        if (!cfg.enabled) return
        val now = SystemClock.uptimeMillis()
        val gap = if (t == GestureType.TOUCH) 150L else 500L
        if (now - (lastFire[t.name] ?: 0L) < gap) return
        lastFire[t.name] = now
        SoundPlayer.play(this, cfg.snd)
        if (cfg.visual) show(cfg.duration) { it.play(t, cfg, xf, yf) }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        getSharedPreferences("cfg", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
        strips.values.forEach { runCatching { wm.removeView(it) } }
        strips.clear()
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        instance = null
        super.onDestroy()
    }
}
