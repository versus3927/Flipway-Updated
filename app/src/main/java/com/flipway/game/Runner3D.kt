package com.flipway.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 3D-модель бегуна из примитивов с иерархией суставов: бёдра → колени → кроссовки,
 * плечи → локти. Локальная система: центр хитбокса в нуле, Y — «вверх от
 * поверхности», Z — вперёд по бегу. Весь бегун поворачивается вместе с креном
 * камеры, поэтому на экране всегда стоит прямо; при перевороте крутит сальто.
 */
class Runner3D(private val s: Scene3D) {

    fun draw(g: Game, clock: Float) {
        if (g.state == Game.State.DEAD) return
        val m = s.m
        val rollDeg = Math.toDegrees(g.roll().toDouble()).toFloat()
        val mid = (g.py0 + g.py1) / 2f

        // тень на поверхности
        if (!g.flipping) {
            val k = 1f / (1f + g.h * 1.4f)
            s.reset()
            Mat4.translate(m, g.x, worldY(g.side, 0.015f), 0.05f)
            Mat4.scale(m, 0.8f * k, 0.01f, 0.6f * k)
            s.emit(MeshId.CYLINDER, 0xFF000000.toInt(), alpha = 0.5f)
        }
        if (g.invuln > 0f && (clock * 12f).toInt() % 2 == 0) return   // мигание неуязвимости

        s.reset()
        Mat4.translate(m, g.x, mid, 0f)
        Mat4.rotateZ(m, rollDeg)
        if (g.flipping) Mat4.rotateX(m, 360f * g.ease(g.flipT))     // сальто
        if (g.active(Power.SHIELD)) shield(clock)

        val skin = s.skin
        val body = skin.body
        val sh = skin.shine       // блеск: «Хром» заметно глянцевее остальных
        val dark = lerpColor(body, 0xFF000000.toInt(), 0.45f)
        val acc = skin.accent
        val p = g.runPhase

        // поза: углы суставов в градусах (отрицательный угол бедра — нога вперёд)
        val thigh = FloatArray(2); val knee = FloatArray(2); val arm = FloatArray(2)
        var elbow = -70f
        var lean = 10f
        when {
            g.sliding -> {
                // подкат — кувырок: сгруппировался в клубок и катится
                Mat4.rotateX(m, clock * 900f)
                Mat4.scale(m, 0.75f, 0.75f, 0.75f)
                Mat4.translate(m, 0f, -0.22f, 0f)
                thigh[0] = -110f; thigh[1] = -110f; knee[0] = 140f; knee[1] = 140f
                arm[0] = -40f; arm[1] = -40f; elbow = -100f; lean = 35f
            }
            g.isWallRunning -> {
                // Wall run: tilt sideways
                Mat4.rotateZ(m, g.lane * 90f)
                thigh[0] = -sin(p) * 48f; thigh[1] = -sin(p + PI.toFloat()) * 48f
                knee[0] = 12f + max(0f, -sin(p + 0.6f)) * 85f; knee[1] = 12f + max(0f, -sin(p + PI.toFloat() + 0.6f)) * 85f
                arm[0] = sin(p) * 45f; arm[1] = sin(p + PI.toFloat()) * 45f
                lean = 5f
            }
            g.flipping || !g.onSurface -> {
                thigh[0] = -55f; thigh[1] = -25f; knee[0] = 95f; knee[1] = 70f
                arm[0] = -120f; arm[1] = -100f; elbow = -40f; lean = 5f
            }
            else -> {
                for (i in 0..1) {
                    val ph = p + i * PI.toFloat()
                    thigh[i] = -sin(ph) * 48f
                    knee[i] = 12f + max(0f, -sin(ph + 0.6f)) * 85f
                    arm[i] = sin(ph) * 45f
                }
                Mat4.translate(m, 0f, abs(cos(p)) * 0.05f - 0.03f, 0f)
            }
        }

        // ноги от тазобедренных суставов
        for (i in 0..1) {
            val sx = if (i == 0) -0.1f else 0.1f
            s.push()
            Mat4.translate(m, sx, -0.08f, 0f)
            Mat4.rotateX(m, thigh[i])
            s.cube(0f, -0.14f, 0f, 0.15f, 0.3f, 0.15f, dark, spec = 0.3f)
            Mat4.translate(m, 0f, -0.28f, 0f)
            Mat4.rotateX(m, knee[i])
            s.cube(0f, -0.125f, 0f, 0.13f, 0.27f, 0.13f, dark, spec = 0.3f)
            s.cube(0f, -0.27f, 0.04f, 0.15f, 0.08f, 0.27f, Palette.WHITE, acc, spec = 0.6f)
            s.pop()
        }

        // корпус с наклоном вперёд
        s.push()
        Mat4.translate(m, 0f, -0.08f, 0f)
        Mat4.rotateX(m, lean)
        Mat4.translate(m, 0f, 0.08f, 0f)
        s.cube(0f, 0.17f, 0f, 0.42f, 0.46f, 0.25f, body, spec = 0.5f * sh, rim = 0.5f)
        s.cube(0f, -0.06f, 0f, 0.44f, 0.06f, 0.27f, dark)                        // пояс
        s.cube(0f, 0.19f, -0.17f, 0.3f, 0.32f, 0.12f, dark, spec = 0.6f)         // рюкзак
        s.cube(0f, 0.19f, -0.235f, 0.07f, 0.24f, 0.02f, 0, acc)                  // неоновая полоса
        for (dx in floatArrayOf(-0.09f, 0.09f)) {                               // сопла
            s.push(); Mat4.translate(m, dx, 0.0f, -0.2f); Mat4.scale(m, 0.07f, 0.08f, 0.07f)
            s.emit(MeshId.CYLINDER, dark, acc); s.pop()
        }

        // голова, наушники, гребень шлема
        s.push()
        Mat4.translate(m, 0f, 0.5f, 0f)
        s.push(); Mat4.scale(m, 0.3f, 0.3f, 0.3f); s.emit(MeshId.SPHERE, body, spec = 0.8f * sh, rim = 0.6f); s.pop()
        s.cube(0f, 0.02f, 0.13f, 0.24f, 0.09f, 0.06f, 0xFF10131F.toInt(), Lighten.visor(acc), spec = 1f)
        for (dx in floatArrayOf(-0.155f, 0.155f)) {
            s.push(); Mat4.translate(m, dx, 0f, 0f); Mat4.rotateZ(m, 90f); Mat4.scale(m, 0.12f, 0.06f, 0.12f)
            s.emit(MeshId.CYLINDER, dark, acc); s.pop()
        }
        s.cube(0f, 0.14f, -0.01f, 0.05f, 0.05f, 0.26f, 0, acc)
        s.pop()

        // руки от плеч
        for (i in 0..1) {
            val sx = if (i == 0) -0.265f else 0.265f
            s.push()
            Mat4.translate(m, sx, 0.34f, 0f)
            Mat4.rotateX(m, arm[i])
            s.cube(0f, -0.11f, 0f, 0.1f, 0.24f, 0.1f, body, spec = 0.4f * sh)
            Mat4.translate(m, 0f, -0.22f, 0f)
            Mat4.rotateX(m, elbow)
            s.cube(0f, -0.1f, 0f, 0.09f, 0.21f, 0.09f, body, spec = 0.4f * sh)
            s.push(); Mat4.translate(m, 0f, -0.22f, 0f); Mat4.scale(m, 0.11f, 0.11f, 0.11f)
            s.emit(MeshId.SPHERE, dark); s.pop()
            s.pop()
        }
        s.pop()
    }

    private fun shield(clock: Float) {
        s.push()
        val k = 1.75f + sin(clock * 6f) * 0.05f
        Mat4.scale(s.m, k, k, k)
        s.emit(MeshId.SPHERE, 0, Palette.CYAN, rim = 2f, alpha = 0.22f, additive = true)
        s.pop()
    }
}

/** Мелкие цветовые помощники для модели. */
object Lighten {
    /** Визор шлема светится цветом скина, но приглушённо. */
    fun visor(c: Int) = Scene3D.scale(c, 0.7f)
}
