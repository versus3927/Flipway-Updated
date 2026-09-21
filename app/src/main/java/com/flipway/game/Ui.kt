package com.flipway.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/** Примитивы интерфейса: неоновые кнопки, текст с ореолом, иконка монеты. */
class Ui {
    class Button(val id: String, val rect: RectF, val enabled: Boolean)

    val buttons = ArrayList<Button>()
    var u = 1f                        // 1% ширины экрана
        private set

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    fun begin(w: Float) {
        u = w / 100f
        buttons.clear()
    }

    fun hit(x: Float, y: Float): String? =
        buttons.lastOrNull { it.enabled && it.rect.contains(x, y) }?.id

    fun dim(c: Canvas, alpha: Int) {
        fill.color = withAlpha(Palette.FOG, alpha)
        c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), fill)
    }

    fun panel(c: Canvas, r: RectF, color: Int = Palette.PINK) {
        fill.color = 0xE0100826.toInt()
        c.drawRoundRect(r, 3 * u, 3 * u, fill)
        stroke.color = withAlpha(color, 150); stroke.strokeWidth = 0.5f * u
        c.drawRoundRect(r, 3 * u, 3 * u, stroke)
    }

    fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
             align: Paint.Align = Paint.Align.CENTER, glow: Boolean = false) {
        txt.textAlign = align
        txt.textSize = size * u
        if (glow) {
            txt.style = Paint.Style.STROKE
            txt.strokeWidth = size * u * 0.18f
            txt.color = withAlpha(color, 70)
            c.drawText(s, x, y, txt)
            txt.style = Paint.Style.FILL
        }
        txt.color = color
        c.drawText(s, x, y, txt)
    }

    fun textWidth(s: String, size: Float): Float { txt.textSize = size * u; return txt.measureText(s) }

    fun button(c: Canvas, id: String, label: String, cx: Float, cy: Float, w: Float, h: Float,
               color: Int = Palette.CYAN, enabled: Boolean = true, size: Float = 5f) {
        val r = RectF(cx - w * u / 2f, cy - h * u / 2f, cx + w * u / 2f, cy + h * u / 2f)
        val col = if (enabled) color else 0xFF555A70.toInt()
        fill.color = withAlpha(col, 40)
        c.drawRoundRect(r, h * u / 2f, h * u / 2f, fill)
        stroke.color = withAlpha(col, 80); stroke.strokeWidth = 1.6f * u
        c.drawRoundRect(r, h * u / 2f, h * u / 2f, stroke)
        stroke.color = col; stroke.strokeWidth = 0.5f * u
        c.drawRoundRect(r, h * u / 2f, h * u / 2f, stroke)
        text(c, label, cx, cy + size * u * 0.36f, size, if (enabled) Palette.WHITE else 0xFF8A8FA8.toInt())
        buttons += Button(id, r, enabled)
    }

    fun coin(c: Canvas, x: Float, y: Float, r: Float) {
        fill.color = Palette.COIN_RIM
        c.drawCircle(x, y, r, fill)
        fill.color = Palette.COIN
        c.drawCircle(x, y, r * 0.72f, fill)
    }

    fun bar(c: Canvas, x: Float, y: Float, w: Float, h: Float, frac: Float, color: Int) {
        fill.color = 0x66000000
        c.drawRoundRect(x, y, x + w, y + h, h / 2f, h / 2f, fill)
        fill.color = color
        c.drawRoundRect(x, y, x + w * frac.coerceIn(0f, 1f), y + h, h / 2f, h / 2f, fill)
    }

    fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        fill.color = color
        c.drawCircle(x, y, r, fill)
    }
}
