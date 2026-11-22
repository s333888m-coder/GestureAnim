package com.example.gestureanim

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

object C {
    val BG = 0xFF0E1224.toInt()
    val CARD = 0xFF1A2040.toInt()
    val CARD2 = 0xFF252D5C.toInt()
    val ACCENT = 0xFF00E5FF.toInt()
    val ACCENT2 = 0xFF7C4DFF.toInt()
    val TEXT = 0xFFFFFFFF.toInt()
    val SUB = 0xFF9AA3C7.toInt()
    val OK = 0xFF69F0AE.toInt()
    val WARN = 0xFFFFD740.toInt()
    val BAD = 0xFFFF5252.toInt()
}

fun Context.rounded(color: Int, r: Int = 16, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable {
    val d = GradientDrawable()
    d.setColor(color)
    d.cornerRadius = dp(r).toFloat()
    if (stroke > 0) d.setStroke(dp(stroke), strokeColor)
    return d
}

fun Context.gradient(c1: Int, c2: Int, r: Int = 16): GradientDrawable {
    val d = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2))
    d.cornerRadius = dp(r).toFloat()
    return d
}

fun Context.vbox(pad: Int = 0): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.VERTICAL
    l.setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    return l
}

fun Context.hbox(): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.HORIZONTAL
    l.gravity = Gravity.CENTER_VERTICAL
    return l
}

fun Context.text(t: String, size: Float = 14f, color: Int = C.TEXT, bold: Boolean = false): TextView {
    val v = TextView(this)
    v.text = t
    v.textSize = size
    v.setTextColor(color)
    if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
    return v
}

fun LinearLayout.add(v: View, top: Int = 0, bottom: Int = 0): View {
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.topMargin = context.dp(top)
    lp.bottomMargin = context.dp(bottom)
    addView(v, lp)
    return v
}

fun Context.card(): LinearLayout {
    val l = vbox(16)
    l.background = rounded(C.CARD, 18)
    return l
}

fun Context.sectionTitle(t: String, sub: String? = null): LinearLayout {
    val b = vbox()
    b.addView(text(t, 17f, C.TEXT, true))
    if (sub != null) b.addView(text(sub, 12f, C.SUB))
    return b
}

fun Context.chip(t: String, color: Int): TextView {
    val v = text(t, 11f, color, true)
    v.setPadding(dp(10), dp(4), dp(10), dp(4))
    v.background = rounded((color and 0x00FFFFFF) or 0x33000000, 12)
    return v
}

fun Context.primaryButton(t: String, onClick: () -> Unit): TextView {
    val v = text(t, 14f, Color.WHITE, true)
    v.gravity = Gravity.CENTER
    v.setPadding(dp(16), dp(14), dp(16), dp(14))
    v.background = gradient(C.ACCENT2, 0xFF2979FF.toInt(), 14)
    v.setOnClickListener { onClick() }
    return v
}

fun Context.ghostButton(t: String, color: Int = C.ACCENT, onClick: () -> Unit): TextView {
    val v = text(t, 13f, color, true)
    v.gravity = Gravity.CENTER
    v.setPadding(dp(14), dp(11), dp(14), dp(11))
    v.background = rounded(0x00000000, 14, 1, color)
    v.setOnClickListener { onClick() }
    return v
}

fun Context.listItem(
    icon: String, title: String, sub: String, badge: String?, badgeColor: Int, onClick: () -> Unit
): View {
    val row = hbox()
    row.setPadding(dp(12), dp(12), dp(12), dp(12))
    row.background = rounded(C.CARD, 16)
    val ic = text(icon, 20f)
    ic.gravity = Gravity.CENTER
    ic.background = rounded(C.CARD2, 14)
    row.addView(ic, LinearLayout.LayoutParams(dp(44), dp(44)))
    val col = vbox()
    col.addView(text(title, 15f, C.TEXT, true))
    col.addView(text(sub, 12f, C.SUB))
    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    lp.leftMargin = dp(12)
    lp.rightMargin = dp(8)
    row.addView(col, lp)
    if (badge != null) row.addView(chip(badge, badgeColor))
    row.isClickable = true
    row.setOnClickListener { onClick() }
    return row
}

fun Context.toggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
    val row = hbox()
    val col = vbox()
    col.addView(text(title, 15f, C.TEXT, true))
    if (sub != null) col.addView(text(sub, 12f, C.SUB))
    row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    val sw = Switch(this)
    sw.isChecked = checked
    sw.setOnCheckedChangeListener { _, c -> onChange(c) }
    row.addView(sw)
    return row
}

fun Context.banner(title: String, sub: String?): View {
    val b = vbox(20)
    b.background = gradient(C.ACCENT2, 0xFF00B8D4.toInt(), 22)
    b.addView(text(title, 24f, Color.WHITE, true))
    if (sub != null) {
        val s = text(sub, 13f, 0xCCFFFFFF.toInt())
        s.setPadding(0, dp(4), 0, 0)
        b.addView(s)
    }
    return b
}

/** पूरी स्क्रीन: स्क्रॉल + बैनर, और कंटेंट वाला LinearLayout लौटाता है */
fun Activity.scaffold(title: String, sub: String? = null): LinearLayout {
    val sv = ScrollView(this)
    sv.setBackgroundColor(C.BG)
    val root = vbox()
    root.setPadding(dp(16), dp(28), dp(16), dp(32))
    sv.addView(root)
    root.add(banner(title, sub), 0, 14)
    setContentView(sv)
    return root
}

fun gestureIcon(t: GestureType): String = when (t) {
    GestureType.TOUCH -> "👆"
    GestureType.SWIPE_UP -> "⬆️"
    GestureType.SWIPE_DOWN -> "⬇️"
    GestureType.SWIPE_LEFT -> "⬅️"
    GestureType.SWIPE_RIGHT -> "➡️"
    GestureType.BACK -> "↩️"
    GestureType.HOME -> "🏠"
    GestureType.RECENTS -> "🗂️"
    GestureType.APP_OPEN -> "🚀"
}
