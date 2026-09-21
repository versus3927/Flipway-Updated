package com.flipway.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.flipway.game.Config.CAM_DIST
import com.flipway.game.Config.DRAW_Z
import com.flipway.game.Config.NEAR_Z
import com.flipway.game.Config.TUNNEL_H
import com.flipway.game.Config.TUNNEL_HALF_W
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Туннель, препятствия, монеты, частицы. Всё рисуется в повёрнутом (крен) холсте. */
class WorldRenderer(private val cam: Camera) {
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val order = ArrayList<Any>()
    private val rings = ArrayList<Pair<Float, Boolean>>()
    private var glowW = 0f
    private var distanceNow = 0f   // для смешивания цветов локации (см. Biomes.kt)

    fun fog(c: Int, depth: Float): Int {
        val t = (depth / (DRAW_Z + CAM_DIST)).coerceIn(0f, 1f).let { it * it }
        val fogColor = Biomes.colorAt(distanceNow, depth - CAM_DIST) { it.fog }
        return lerpColor(c, fogColor, t)
    }

    // ------------------------------------------------------------------ туннель

    fun drawTunnel(c: Canvas, g: Game) {
        distanceNow = g.distance
        if (glowW != cam.w) {
            glowW = cam.w
            glow.shader = RadialGradient(0f, 0f, cam.w * 0.5f,
                intArrayOf(0x66FF2BD6, 0x2223F0FF, 0), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        }
        val seg = 4f
        val off = g.distance % seg
        val base = floor(g.distance / seg).toInt()
        val count = ceil((DRAW_Z + off) / seg).toInt()
        rings.clear()
        val hw = TUNNEL_HALF_W
        for (i in count downTo 0) {
            var z0 = i * seg - off
            val z1 = z0 + seg
            if (z1 < NEAR_Z) continue
            z0 = max(z0, NEAR_Z)
            val n = base + i
            val d = z0 + CAM_DIST
            val even = n and 1 == 0
            val bandK = if (even) 1.08f else 0.86f
            val floorC = fog(Scene3D.scale(Biomes.colorAt(distanceNow, z0) { it.floor }, bandK), d)
            val wallC = fog(Scene3D.scale(Biomes.colorAt(distanceNow, z0) { it.wall }, bandK), d)
            quad(c, -hw, 0f, z0, hw, 0f, z0, hw, 0f, z1, -hw, 0f, z1, floorC)
            quad(c, -hw, TUNNEL_H, z0, hw, TUNNEL_H, z0, hw, TUNNEL_H, z1, -hw, TUNNEL_H, z1, floorC)
            quad(c, -hw, 0f, z0, -hw, TUNNEL_H, z0, -hw, TUNNEL_H, z1, -hw, 0f, z1, wallC)
            quad(c, hw, 0f, z0, hw, TUNNEL_H, z0, hw, TUNNEL_H, z1, hw, 0f, z1, wallC)
            // пунктир разметки полос на полу и на потолке
            val dashC = fog(Palette.LANE_DASH, d)
            val dz = z0 + seg * 0.45f
            for (lx in floatArrayOf(-0.5f, 0.5f)) for (ly in floatArrayOf(0f, TUNNEL_H)) {
                quad(c, lx - 0.03f, ly, z0, lx + 0.03f, ly, z0, lx + 0.03f, ly, dz, lx - 0.03f, ly, dz, dashC)
            }
            // неоновые рамки-кольца каждые три сегмента
            if (n % 3 == 0 && i * seg - off >= NEAR_Z) rings += (i * seg - off) to (n % 6 == 0)
        }
        // неоновые кромки вдоль углов туннеля — ленты на полу и потолке, сужаются в перспективе
        val edgeNow = Biomes.current(distanceNow).edge
        for (ey in floatArrayOf(0f, TUNNEL_H)) for (ex in floatArrayOf(-hw, hw)) {
            val inX = if (ex < 0f) 1f else -1f
            quad(c, ex, ey, NEAR_Z, ex + inX * 0.3f, ey, NEAR_Z, ex + inX * 0.3f, ey, DRAW_Z, ex, ey, DRAW_Z, withAlpha(edgeNow, 0x40))
            quad(c, ex, ey, NEAR_Z, ex + inX * 0.07f, ey, NEAR_Z, ex + inX * 0.07f, ey, DRAW_Z, ex, ey, DRAW_Z, edgeNow)
        }
        // дымка «света в конце туннеля» поверх дальних сегментов
        if (cam.project(0f, TUNNEL_H / 2f, DRAW_Z)) {
            c.save(); c.translate(cam.sx, cam.sy); c.drawCircle(0f, 0f, cam.w * 0.5f, glow); c.restore()
        }
        for ((z, edge) in rings) ring(c, z, edge)
    }

    private fun ring(c: Canvas, z: Float, edge: Boolean) {
        val d = z + CAM_DIST
        val color = Biomes.colorAt(distanceNow, z) { if (edge) it.edge else it.ring }
        val col = fog(color, d)
        val hw = TUNNEL_HALF_W - 0.02f
        val ys = floatArrayOf(0.02f, TUNNEL_H - 0.02f)
        neon3(c, -hw, ys[0], z, hw, ys[0], z, col, 0.06f)
        neon3(c, -hw, ys[1], z, hw, ys[1], z, col, 0.06f)
        neon3(c, -hw, ys[0], z, -hw, ys[1], z, col, 0.06f)
        neon3(c, hw, ys[0], z, hw, ys[1], z, col, 0.06f)
    }

    // ------------------------------------------------------------------ сцена

    /** Рисует объекты от дальних к ближним; игрок вставляется на своей глубине. */
    fun drawScene(c: Canvas, g: Game, rollDeg: Float, player: (Canvas) -> Unit) {
        distanceNow = g.distance
        order.clear()
        for (o in g.obstacles) if (!o.destroyed && o.z < DRAW_Z) order += o
        for (k in g.coins) if (k.z < DRAW_Z && k.z > -1.5f) order += k
        for (p in g.powers) if (p.z < DRAW_Z && p.z > -1.5f) order += p
        order += g
        order.sortByDescending { key(it) }
        for (it in order) when (it) {
            is Obstacle -> drawObstacle(c, it)
            is Coin -> drawCoin(c, it)
            is PowerUp -> drawPower(c, it, rollDeg)
            is Game -> player(c)
        }
        for (p in g.particles) {
            if (p.z < -2.2f || !cam.project(p.x, p.y, p.z)) continue
            fill.color = p.color
            fill.alpha = (255 * (p.life / p.maxLife)).toInt().coerceIn(0, 255)
            c.drawCircle(cam.sx, cam.sy, max(1.5f, 0.05f * cam.scale), fill)
        }
        fill.alpha = 255
    }

    private fun key(o: Any): Float = when (o) {
        is Obstacle -> if (o.z < 0f && o.z + o.len > 0f) 0.01f else o.z
        is Coin -> o.z
        is PowerUp -> o.z
        else -> 0f
    }

    private fun drawObstacle(c: Canvas, o: Obstacle) {
        val x0 = o.lane * Config.LANE - Config.OBST_W / 2f
        val x1 = x0 + Config.OBST_W
        val z1 = o.z + o.len
        if (z1 < NEAR_Z) return
        val front = o.z >= NEAR_Z
        val z0 = max(o.z, NEAR_Z)
        when (o.type) {
            ObType.LOW -> {
                val barrier = Biomes.colorAt(distanceNow, o.z) { it.barrier }
                val trim = Biomes.colorAt(distanceNow, o.z) { it.barrierTrim }
                box(c, x0, o.wy0, z0, x1, o.wy1, z1, barrier, trim, Scene3D.scale(barrier, 0.6f), front)
                if (front) stripes(c, x0, o.wy0, x1, o.wy1, o.z, trim)
            }
            ObType.HIGH -> {
                // две стойки от поверхности до лазерной перекладины
                val sy0 = if (o.side == FLOOR) 0f else o.wy0
                val sy1 = if (o.side == FLOOR) o.wy1 else TUNNEL_H
                box(c, x0, sy0, z0, x0 + 0.09f, sy1, z1, Palette.POST, Palette.POST, Palette.POST, front)
                box(c, x1 - 0.09f, sy0, z0, x1, sy1, z1, Palette.POST, Palette.POST, Palette.POST, front)
                val laser = Biomes.colorAt(distanceNow, o.z) { it.laser }
                val my = (o.wy0 + o.wy1) / 2f
                if (front) neon3(c, x0, my, o.z, x1, my, o.z, laser, (o.wy1 - o.wy0) * 0.45f)
            }
            ObType.BLOCK, ObType.TRAIN -> {
                val train = o.type == ObType.TRAIN
                val body = Biomes.colorAt(distanceNow, o.z) { if (train) it.trainBody else it.blockBody }
                box(c, x0, o.wy0, z0, x1, o.wy1, z1, body, Scene3D.scale(body, 1.3f), Scene3D.scale(body, 0.6f), front)
                if (front) blockFace(c, o, x0, x1, train)
            }
        }
    }

    private fun blockFace(c: Canvas, o: Obstacle, x0: Float, x1: Float, train: Boolean) {
        val d = o.z + CAM_DIST
        val base = if (o.side == FLOOR) o.wy0 else o.wy1          // у своей поверхности
        val up = if (o.side == FLOOR) 1f else -1f
        val hh = o.wy1 - o.wy0
        // окна-панели
        val winSrc = Biomes.colorAt(distanceNow, o.z) { if (train) it.trainWin else it.blockTrim[0] }
        val winC = fog(winSrc, d)
        rect3(c, x0 + 0.12f, base + up * hh * 0.55f, x1 - 0.12f, base + up * hh * 0.85f, o.z, winC)
        // полоса-подсветка (у вагона — фары)
        if (train) {
            if (cam.project(x0 + 0.2f, base + up * 0.35f, o.z)) {
                fill.color = Palette.HEADLIGHT; c.drawCircle(cam.sx, cam.sy, 0.11f * cam.scale, fill)
            }
            if (cam.project(x1 - 0.2f, base + up * 0.35f, o.z)) c.drawCircle(cam.sx, cam.sy, 0.11f * cam.scale, fill)
        } else {
            val trim = Biomes.colorAt(distanceNow, o.z) { it.blockTrim[if (o.wall) 0 else it.blockTrim.size - 1] }
            neon3(c, x0 + 0.05f, base + up * 0.3f, o.z, x1 - 0.05f, base + up * 0.3f, o.z, fog(trim, d), 0.05f)
        }
    }

    private fun stripes(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, z: Float, color: Int) {
        val d = z + CAM_DIST
        val w = (x1 - x0) / 5f
        for (i in 0 until 5 step 2) rect3(c, x0 + i * w, y0 + (y1 - y0) * 0.2f, x0 + (i + 1) * w, y1 - (y1 - y0) * 0.2f, z, fog(color, d))
    }

    private fun drawCoin(c: Canvas, k: Coin) {
        if (!cam.project(k.x, k.y, k.z)) return
        val r = 0.2f * cam.scale
        val w = max(r * 0.18f, r * abs(cos(k.spin)))
        val d = k.z + CAM_DIST
        fill.color = fog(Palette.COIN_RIM, d)
        c.drawOval(cam.sx - w, cam.sy - r, cam.sx + w, cam.sy + r, fill)
        fill.color = fog(Palette.COIN, d)
        c.drawOval(cam.sx - w * 0.72f, cam.sy - r * 0.72f, cam.sx + w * 0.72f, cam.sy + r * 0.72f, fill)
    }

    private fun drawPower(c: Canvas, p: PowerUp, rollDeg: Float) {
        if (!cam.project(p.x, p.y, p.z)) return
        val r = 0.34f * cam.scale
        val col = Palette.power(p.kind)
        fill.color = col; fill.alpha = 70
        c.drawCircle(cam.sx, cam.sy, r * 1.5f, fill)
        fill.alpha = 255; fill.color = 0xFF0B0620.toInt()
        c.drawCircle(cam.sx, cam.sy, r, fill)
        line.color = col; line.strokeWidth = r * 0.18f
        c.drawCircle(cam.sx, cam.sy, r, line)
        text.color = col; text.textSize = r * 0.95f
        c.save(); c.rotate(-rollDeg, cam.sx, cam.sy)
        c.drawText(Palette.powerIcon(p.kind), cam.sx, cam.sy + r * 0.34f, text)
        c.restore()
    }

    // ------------------------------------------------------------------ примитивы

    fun box(c: Canvas, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float,
            front: Int, top: Int, side: Int, drawFront: Boolean) {
        val d = z0 + CAM_DIST
        val camY = TUNNEL_H / 2f
        if (cam.camX < x0) quad(c, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, fog(side, d))
        if (cam.camX > x1) quad(c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, fog(side, d))
        if (camY > y1) quad(c, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, fog(top, d))
        if (camY < y0) quad(c, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, fog(top, d))
        if (drawFront) quad(c, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, fog(front, d))
    }

    fun quad(c: Canvas, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
             cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float, color: Int) {
        path.reset()
        if (!cam.project(ax, ay, az)) return
        path.moveTo(cam.sx, cam.sy)
        if (!cam.project(bx, by, bz)) return
        path.lineTo(cam.sx, cam.sy)
        if (!cam.project(cx, cy, cz)) return
        path.lineTo(cam.sx, cam.sy)
        if (!cam.project(dx, dy, dz)) return
        path.lineTo(cam.sx, cam.sy)
        path.close()
        fill.color = color
        c.drawPath(path, fill)
    }

    private fun rect3(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, z: Float, color: Int) {
        if (!cam.project(x0, y0, z)) return
        val ax = cam.sx; val ay = cam.sy
        cam.project(x1, y1, z)
        fill.color = color
        c.drawRect(min(ax, cam.sx), min(ay, cam.sy), max(ax, cam.sx), max(ay, cam.sy), fill)
    }

    /** Неон: широкий полупрозрачный ореол + яркая сердцевина. */
    fun neon3(c: Canvas, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, color: Int, w: Float) {
        if (!cam.project(ax, ay, az)) return
        val x0 = cam.sx; val y0 = cam.sy
        val s = min(cam.scale, cam.f / 0.8f)
        if (!cam.project(bx, by, bz)) return
        line.color = color
        line.alpha = 60
        line.strokeWidth = max(3f, w * s * 3.2f)
        c.drawLine(x0, y0, cam.sx, cam.sy, line)
        line.alpha = 255
        line.strokeWidth = max(1.2f, w * s)
        c.drawLine(x0, y0, cam.sx, cam.sy, line)
    }
}
