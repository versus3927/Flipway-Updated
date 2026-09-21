package com.flipway.game

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Бегун, вид со спины. Рисуется в локальных координатах тела (1 = единица мира,
 * центр хитбокса в нуле, ось Y вниз) и всегда стоит прямо на экране — крутится
 * мир, а не он. Во время переворота делает сальто.
 */
class PlayerRenderer(private val cam: Camera) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    var skin = Palette.skins[0]

    fun draw(c: Canvas, g: Game, rollDeg: Float, clock: Float) {
        if (g.state == Game.State.DEAD) return
        drawShadow(c, g)
        if (g.invuln > 0f && (clock * 12f).toInt() % 2 == 0) return   // мигание неуязвимости
        val mid = (g.py0 + g.py1) / 2f
        if (!cam.project(g.x, mid, 0f)) return
        val s = cam.scale
        c.save()
        c.translate(cam.sx, cam.sy)
        val spin = if (g.flipping) 360f * g.ease(g.flipT) else 0f
        c.rotate(-rollDeg + spin)
        c.scale(s, s)
        if (g.sliding) c.scale(1.15f, Config.SLIDE_H / Config.PLAYER_H)
        if (g.active(Power.SHIELD)) shield(c, clock)
        body(c, g)
        c.restore()
    }

    private fun drawShadow(c: Canvas, g: Game) {
        if (g.flipping) return
        if (!cam.project(g.x, worldY(g.side, 0.01f), 0f)) return
        val k = 1f / (1f + g.h * 1.3f)
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        val w = 0.36f * cam.scale * k
        c.drawOval(cam.sx - w, cam.sy - w * 0.3f, cam.sx + w, cam.sy + w * 0.3f, p)
    }

    private fun shield(c: Canvas, clock: Float) {
        val r = 0.85f + sin(clock * 6f) * 0.03f
        p.style = Paint.Style.FILL
        p.color = withAlpha(Palette.CYAN, 45)
        c.drawCircle(0f, 0f, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.04f
        p.color = withAlpha(Palette.CYAN, 200)
        c.drawCircle(0f, 0f, r, p)
    }

    private fun body(c: Canvas, g: Game) {
        val ph = g.runPhase
        val air = !g.onSurface
        val tuck = g.flipping
        val dark = lerpColor(skin.body, 0xFF000000.toInt(), 0.35f)
        val acc = skin.accent

        // аура
        p.style = Paint.Style.FILL
        p.color = withAlpha(acc, 38)
        c.drawOval(-0.42f, -0.72f, 0.42f, 0.7f, p)

        // ноги
        p.style = Paint.Style.STROKE
        for (sd in intArrayOf(-1, 1)) {
            val phase = ph + if (sd > 0) PI.toFloat() else 0f
            val lift = when { tuck -> 0.3f; air -> 0.16f; else -> max(0f, sin(phase)) * 0.24f }
            val hipX = sd * 0.1f
            val kneeX = sd * 0.17f
            val kneeY = 0.33f - lift * 0.85f
            val footY = 0.58f - lift
            p.color = dark; p.strokeWidth = 0.14f
            c.drawLine(hipX, 0.04f, kneeX, kneeY, p)
            c.drawLine(kneeX, kneeY, sd * 0.12f, footY, p)
            p.color = acc; p.strokeWidth = 0.12f        // светящиеся кроссовки
            c.drawLine(sd * 0.12f, footY, sd * 0.12f, footY + 0.03f, p)
        }

        // руки
        for (sd in intArrayOf(-1, 1)) {
            val sw = if (tuck) -0.12f else sin(ph + if (sd > 0) 0f else PI.toFloat()) * 0.14f
            p.color = dark; p.strokeWidth = 0.1f
            val ex = sd * 0.3f; val ey = -0.1f + sw * 0.5f
            c.drawLine(sd * 0.19f, -0.27f, ex, ey, p)
            c.drawLine(ex, ey, sd * 0.27f, 0.06f + sw, p)
        }

        // торс-куртка
        p.style = Paint.Style.FILL
        p.color = skin.body
        c.drawRoundRect(-0.21f, -0.34f, 0.21f, 0.1f, 0.1f, 0.1f, p)
        // рюкзак с неоновой полосой — «гравитационный двигатель»
        p.color = dark
        c.drawRoundRect(-0.14f, -0.29f, 0.14f, 0.0f, 0.05f, 0.05f, p)
        p.color = acc
        c.drawRoundRect(-0.035f, -0.25f, 0.035f, -0.04f, 0.03f, 0.03f, p)

        // голова: шлем, светящийся ободок и «уши»-наушники
        p.color = skin.body
        c.drawCircle(0f, -0.48f, 0.15f, p)
        p.style = Paint.Style.STROKE
        p.color = acc; p.strokeWidth = 0.035f
        c.drawArc(-0.15f, -0.63f, 0.15f, -0.33f, 200f, 140f, false, p)
        p.style = Paint.Style.FILL
        c.drawCircle(-0.155f, -0.48f, 0.045f, p)
        c.drawCircle(0.155f, -0.48f, 0.045f, p)
    }
}
