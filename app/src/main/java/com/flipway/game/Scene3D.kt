package com.flipway.game

import com.flipway.game.Config.CAM_DIST
import com.flipway.game.Config.DRAW_Z
import com.flipway.game.Config.LANE
import com.flipway.game.Config.OBST_W
import com.flipway.game.Config.TUNNEL_H
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Один вызов отрисовки: сетка + матрица модели + материал. */
class Draw {
    val model = FloatArray(16)
    var mesh = MeshId.CUBE
    var color = 0
    var emissive = 0
    var pattern = 0          // 0 — обычный материал, 1 — пол/потолок туннеля, 2 — стены
    var spec = 0.3f
    var rim = 0f
    var alpha = 1f
    var additive = false
    var sortZ = 0f
}

/**
 * Собирает кадр в виде списка вызовов отрисовки. Чистый Kotlin: тот же список
 * рисует и OpenGL на телефоне, и программный растеризатор в превью.
 *
 * Локации (Biomes.kt) сменяют друг друга по пройденному расстоянию прямо во
 * время забега. Статичная геометрия туннеля (единая сетка на весь видимый
 * участок) красится по локации в шейдере — по-фрагментно, от мировой глубины
 * фрагмента; для этого сюда собираются цвета текущей и следующей локации плюс
 * позиция границы между ними, и оба 3D-рендерера (GlRenderer и превью-стенд
 * Shade.kt) читают их отсюда. Отдельные объекты (препятствия, частицы, ворота
 * между локациями) красятся уже на CPU — так дешевле и не нужно гонять массивы
 * цветов по всем четырём локациям в шейдер.
 */
class Scene3D {
    val view = FloatArray(16)
    val proj = FloatArray(16)
    val viewProj = FloatArray(16)
    val camPos = FloatArray(3)
    val light = FloatArray(3)      // направление НА источник, в мире
    val playerPos = FloatArray(3)
    var playerLight = 0
    var scroll = 0f                // пройденное расстояние по модулю периода узоров
    var width = 1; private set
    var height = 1; private set
    var skin = Palette.skins[0]

    // -------- локации: цвета текущей (A) и следующей (B) для шейдера туннеля --------
    val bioFloorA = FloatArray(3); val bioFloorB = FloatArray(3)
    val bioWallA = FloatArray(3); val bioWallB = FloatArray(3)
    val bioEdgeA = FloatArray(3); val bioEdgeB = FloatArray(3)
    val bioRingA = FloatArray(3); val bioRingB = FloatArray(3)
    val bioFogA = FloatArray(3); val bioFogB = FloatArray(3)
    var bioBrightA = 0.3f; var bioBrightB = 0.3f
    var bioGlowA = 1f; var bioGlowB = 1f
    var bioTexA = 0f; var bioTexB = 0f
    var boundaryZ = 0f              // метров впереди игрока до границы локаций
    val bioTrans = Biomes.TRANS

    val opaque = ArrayList<Draw>(320)
    val transparent = ArrayList<Draw>(96)
    private val pool = ArrayList<Draw>(420)
    private var used = 0
    val m = FloatArray(16)
    private val stack = ArrayList<FloatArray>()
    private var sp = 0
    private val runner = Runner3D(this)
    private var distanceNow = 0f

    // ---------------------------------------------------------- матричный стек

    fun reset() { Mat4.identity(m); sp = 0 }
    fun push() { if (sp == stack.size) stack += FloatArray(16); m.copyInto(stack[sp++]) }
    fun pop() { stack[--sp].copyInto(m) }

    fun emit(mesh: MeshId, color: Int, emissive: Int = 0, spec: Float = 0.3f, rim: Float = 0f,
             alpha: Float = 1f, additive: Boolean = false, pattern: Int = 0): Draw {
        if (used == pool.size) pool += Draw()
        val d = pool[used++]
        m.copyInto(d.model)
        d.mesh = mesh; d.color = color; d.emissive = emissive; d.spec = spec; d.rim = rim
        d.alpha = alpha; d.additive = additive; d.pattern = pattern
        d.sortZ = m[14]
        if (alpha < 1f || additive) transparent += d else opaque += d
        return d
    }

    /** Коробка с центром (x, y, z) в текущей системе координат. */
    fun cube(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: Int,
             emissive: Int = 0, spec: Float = 0.3f, rim: Float = 0f) {
        push(); Mat4.translate(m, x, y, z); Mat4.scale(m, sx, sy, sz)
        emit(MeshId.CUBE, color, emissive, spec, rim)
        pop()
    }

    // ---------------------------------------------------------- кадр

    fun build(g: Game, w: Int, h: Int, clock: Float) {
        width = maxOf(1, w); height = maxOf(1, h)
        used = 0; opaque.clear(); transparent.clear()
        distanceNow = g.distance
        camera(g)
        biomeUniforms()
        val cur = Biomes.current(distanceNow)
        reset(); emit(MeshId.TUNNEL_FLOOR, cur.floor, spec = 0.35f, pattern = 1)
        reset(); emit(MeshId.TUNNEL_WALLS, cur.wall, spec = 0.15f, pattern = 2)
        gate(clock)
        for (o in g.obstacles) if (!o.destroyed && o.z < DRAW_Z && o.z + o.len > -CAM_DIST) obstacle(o)
        for (c in g.coins) if (c.z < DRAW_Z && c.z > -CAM_DIST + 0.5f) coin(c)
        for (p in g.powers) if (p.z < DRAW_Z && p.z > -CAM_DIST + 0.5f) power(p, clock)
        ambient(clock)
        canopy(clock)
        runner.draw(g, clock)
        for (p in g.particles) {
            val k = p.life / p.maxLife
            reset(); Mat4.translate(m, p.x, p.y, p.z); Mat4.rotateY(m, p.life * 400f)
            val s = 0.05f + 0.08f * k
            Mat4.scale(m, s, s, s)
            emit(MeshId.CUBE, 0, p.color, alpha = k, additive = true)
        }
        transparent.sortByDescending { it.sortZ }
    }

    private fun camera(g: Game) {
        val f = Config.LANE_SCREEN_FRAC * width * CAM_DIST / LANE
        val cx = width / 2f
        val cy = (Config.PLAYER_SCREEN_Y * height - (TUNNEL_H / 2f) * f / CAM_DIST)
            .coerceIn(height * 0.3f, height * 0.55f)
        val n = 0.3f
        Mat4.frustum(proj, -cx * n / f, (width - cx) * n / f, (cy - height) * n / f, cy * n / f, n, DRAW_Z + CAM_DIST + 15f)
        val roll = g.roll()
        val rollDeg = Math.toDegrees(roll.toDouble()).toFloat()
        Mat4.identity(view)
        Mat4.rotateZ(view, -rollDeg)
        Mat4.scale(view, 1f, 1f, -1f)                 // мир смотрит вдоль +Z, камера GL — вдоль -Z
        Mat4.translate(view, -g.camX, -TUNNEL_H / 2f, CAM_DIST)
        Mat4.mul(viewProj, proj, view)
        camPos[0] = g.camX; camPos[1] = TUNNEL_H / 2f; camPos[2] = -CAM_DIST
        // свет сверху-сзади камеры; на потолке «верх» перевёрнут вместе с камерой
        val lx = 0.3f; val ly = 0.85f; val lz = -0.45f
        val l = sqrt(lx * lx + ly * ly + lz * lz)
        val c = cos(roll); val s = sin(roll)
        light[0] = (lx * c - ly * s) / l; light[1] = (lx * s + ly * c) / l; light[2] = lz / l
        playerPos[0] = g.x; playerPos[1] = (g.py0 + g.py1) / 2f; playerPos[2] = 0f
        playerLight = if (g.state == Game.State.RUNNING) skin.accent else 0
        scroll = g.distance % 24f
    }

    /** Цвета текущей и следующей локации + позиция границы — для шейдера туннеля. */
    private fun biomeUniforms() {
        val cur = Biomes.current(distanceNow); val nxt = Biomes.next(distanceNow)
        rgb3(bioFloorA, cur.floor); rgb3(bioFloorB, nxt.floor)
        rgb3(bioWallA, cur.wall); rgb3(bioWallB, nxt.wall)
        rgb3(bioEdgeA, cur.edge); rgb3(bioEdgeB, nxt.edge)
        rgb3(bioRingA, cur.ring); rgb3(bioRingB, nxt.ring)
        rgb3(bioFogA, cur.fog); rgb3(bioFogB, nxt.fog)
        bioBrightA = cur.bright; bioBrightB = nxt.bright
        bioGlowA = cur.glow; bioGlowB = nxt.glow
        bioTexA = cur.tex.toFloat(); bioTexB = nxt.tex.toFloat()
        boundaryZ = Biomes.boundaryAhead(distanceNow)
    }

    /** Цвет на глубине z, смешанный между текущей и следующей локацией. */
    private fun bio(z: Float, pick: (BiomeStyle) -> Int): Int = Biomes.colorAt(distanceNow, z, pick)

    /** Стабильный псевдослучайный номер 0..1 — для фоновых частиц без хранения состояния. */
    private fun hash01(n: Int): Float {
        val x = sin(n * 12.9898f) * 43758.547f
        return x - floor(x)
    }

    private fun fmod(a: Float, mv: Float): Float = a - mv * floor(a / mv)

    // ---------------------------------------------------------- ворота между локациями

    /** Светящаяся арка на границе локаций: видна издалека и физически «проходится». */
    private fun gate(clock: Float) {
        val z = boundaryZ
        if (z <= 0.25f || z > DRAW_Z) return
        val cur = Biomes.current(distanceNow); val nxt = Biomes.next(distanceNow)
        val edge = lerpColor(cur.edge, nxt.edge, 0.5f)
        val ring = lerpColor(cur.ring, nxt.ring, 0.5f)
        val pulse = 1f + sin(clock * 5f) * 0.06f
        val hw = Config.TUNNEL_HALF_W - 0.05f
        reset()
        for (x in floatArrayOf(-hw, hw)) {
            push(); Mat4.translate(m, x, TUNNEL_H / 2f, z); Mat4.scale(m, 0.22f * pulse, TUNNEL_H, 0.22f * pulse)
            emit(MeshId.CYLINDER, edge, scale(ring, 0.85f), spec = 0.9f, rim = 0.7f); pop()
        }
        for (y in floatArrayOf(-0.16f, TUNNEL_H + 0.16f)) {
            push(); Mat4.translate(m, 0f, y, z); Mat4.rotateZ(m, 90f); Mat4.scale(m, 0.22f * pulse, hw * 2f + 0.5f, 0.22f * pulse)
            emit(MeshId.CYLINDER, edge, scale(ring, 0.85f), spec = 0.9f, rim = 0.7f); pop()
        }
        for (x in floatArrayOf(-hw, hw)) for (y in floatArrayOf(0.06f, TUNNEL_H - 0.06f)) {
            push(); Mat4.translate(m, x, y, z); Mat4.rotateY(m, clock * 90f); Mat4.scale(m, 0.3f * pulse, 0.3f * pulse, 0.3f * pulse)
            emit(MeshId.CRYSTAL, 0, ring, spec = 1f, rim = 1f); pop()
        }
        // полупрозрачная плёнка-портал в проёме
        push(); Mat4.translate(m, 0f, TUNNEL_H / 2f, z); Mat4.scale(m, hw * 1.9f, TUNNEL_H * 0.96f, 0.05f)
        emit(MeshId.CUBE, 0, ring, alpha = 0.1f, additive = true); pop()
    }

    // ---------------------------------------------------------- фоновые частицы локации

    /** Лепестки, искры, снег или угли — по локации, плотным полем вдоль всего туннеля. */
    private fun ambient(clock: Float) {
        val slots = 18
        for (i in 0 until slots) {
            val jitter = hash01(i * 91 + 11)
            val z = 3f + (i + jitter) / slots * (DRAW_Z - 6f)
            val useNext = Biomes.blendAt(distanceNow, z) > 0.5f
            val b = if (useNext) Biomes.next(distanceNow) else Biomes.current(distanceNow)
            if (b.ambient == Ambient.NONE) continue
            val seed = hash01(i * 131 + 7)
            val seed2 = hash01(i * 197 + 53)
            val rising = b.ambient == Ambient.EMBER || b.ambient == Ambient.SPARK
            val speed = when (b.ambient) { Ambient.EMBER -> 1.7f; Ambient.SPARK -> 1.1f; Ambient.SNOW -> 0.4f; else -> 0.55f }
            val cyc = TUNNEL_H / speed
            val t = fmod(clock * speed + seed2 * cyc, cyc)
            val y = if (rising) t else TUNNEL_H - t
            val fade = (minOf(y, TUNNEL_H - y) / 0.5f).coerceIn(0f, 1f)
            if (fade <= 0.02f) continue
            val sway = sin(clock * 0.9f + seed * 6.28f) * (if (b.ambient == Ambient.PETAL) 0.4f else 0.15f)
            val laneX = (seed - 0.5f) * (Config.TUNNEL_HALF_W * 1.8f)
            reset()
            Mat4.translate(m, laneX + sway, y, z)
            Mat4.rotateY(m, clock * 220f * (0.4f + seed))
            Mat4.rotateZ(m, clock * 150f * (0.4f + seed2))
            val additive = rising
            val sz = if (additive) 0.03f else 0.05f
            Mat4.scale(m, sz, sz * 0.35f, sz)
            emit(
                MeshId.CUBE, if (additive) 0 else b.ambientColor, if (additive) b.ambientColor else scale(b.ambientColor, 0.3f),
                alpha = fade * (if (additive) 0.85f else 0.8f), additive = additive,
            )
        }
    }

    /** Свисающие гроздья цветущей кроны у стен — только у локаций с canopy=true (Сакура). */
    private fun canopy(clock: Float) {
        val slots = 14
        for (i in 0 until slots) {
            val seed = hash01(i * 271 + 19)
            val z = 4f + (i + seed) / slots * (DRAW_Z * 0.85f)
            val useNext = Biomes.blendAt(distanceNow, z) > 0.5f
            val b = if (useNext) Biomes.next(distanceNow) else Biomes.current(distanceNow)
            if (!b.canopy) continue
            val seed2 = hash01(i * 353 + 61)
            val seed3 = hash01(i * 401 + 97)
            val side = if (i % 2 == 0) 1f else -1f
            val x = side * (0.95f + seed2 * 0.75f)
            val nearCeil = seed3 < 0.5f
            val hang = 0.3f + hash01(i * 601 + 5) * 0.8f
            val y = (if (nearCeil) TUNNEL_H - hang else hang) + sin(clock * 0.6f + seed * 6f) * 0.05f
            reset()
            Mat4.translate(m, x, y, z)
            val s = 0.45f + seed3 * 0.4f
            val col = if (seed2 < 0.55f) 0xFFFF9AD0.toInt() else 0xFFFFFFFF.toInt()
            push(); Mat4.scale(m, s, s * 0.8f, s); emit(MeshId.SPHERE, col, scale(col, 0.3f), spec = 0.4f, rim = 0.4f); pop()
            if (nearCeil) {
                push(); Mat4.translate(m, 0f, -s * 0.9f, 0f); Mat4.scale(m, s * 0.4f, s * 0.4f, s * 0.4f)
                emit(MeshId.SPHERE, col, scale(col, 0.3f), spec = 0.4f); pop()
            }
        }
    }

    // ---------------------------------------------------------- объекты

    private fun obstacle(o: Obstacle) {
        val x = o.lane * LANE
        val zc = o.z + o.len / 2f
        val s = o.side
        // лёгкий разброс акцента по препятствиям одной локации — не все одного тона
        val h = abs((o.z * 13f + o.lane * 7f).toInt())
        reset()
        when (o.type) {
            ObType.LOW -> {
                val barrier = bio(o.z) { it.barrier }
                val trim = bio(o.z) { it.barrierTrim }
                val hb = Config.LOW_H * 0.62f
                cube(x, worldY(s, hb), zc, OBST_W, 0.3f, o.len, barrier, scale(barrier, 0.3f), spec = 0.6f)
                for (dx in floatArrayOf(-0.22f, 0.22f)) cube(x + dx, worldY(s, hb), zc, 0.16f, 0.31f, o.len + 0.02f, trim, spec = 0.6f)
                val legH = hb - 0.15f
                for (dx in floatArrayOf(-0.36f, 0.36f)) cube(x + dx, worldY(s, legH / 2f), zc, 0.07f, legH, 0.07f, Palette.POST, spec = 0.8f)
            }
            ObType.HIGH -> {
                val laser = bio(o.z) { it.laser }
                val top = Config.HIGH_TOP + 0.1f
                for (dx in floatArrayOf(-OBST_W / 2f + 0.05f, OBST_W / 2f - 0.05f)) {
                    cube(x + dx, worldY(s, top / 2f), zc, 0.1f, top, 0.14f, Palette.POST, spec = 0.8f)
                    cube(x + dx, worldY(s, top + 0.06f), zc, 0.16f, 0.12f, 0.18f, laser, laser)
                }
                val my = worldY(s, (Config.HIGH_BOTTOM + Config.HIGH_TOP) / 2f)
                push(); Mat4.translate(m, x, my, zc); Mat4.rotateZ(m, 90f)
                push(); Mat4.scale(m, 0.08f, OBST_W - 0.1f, 0.08f); emit(MeshId.CYLINDER, laser, Palette.WHITE); pop()
                Mat4.scale(m, 0.36f, OBST_W - 0.12f, 0.36f)
                emit(MeshId.CYLINDER, 0, laser, alpha = 0.45f, additive = true)
                pop()
            }
            ObType.BLOCK -> {
                val trimIdx = h % 2
                block(o, x, zc, bio(o.z) { it.blockBody }, bio(o.z) { it.blockTrim[trimIdx] })
            }
            ObType.TRAIN -> train(o, x, zc)
        }
    }

    private fun block(o: Obstacle, x: Float, zc: Float, body: Int, trim: Int) {
        val s = o.side
        val h = Config.BLOCK_H
        cube(x, worldY(s, h / 2f), zc, OBST_W, h, o.len, body, spec = 0.45f, rim = 0.35f)
        // светящееся окно на торце и неоновые полосы по бокам
        cube(x, worldY(s, h * 0.72f), o.z - 0.012f, OBST_W * 0.76f, h * 0.2f, 0.03f, 0xFF0A0F1E.toInt(), scale(trim, 0.9f))
        for (dx in floatArrayOf(-OBST_W / 2f - 0.008f, OBST_W / 2f + 0.008f)) {
            cube(x + dx, worldY(s, 0.35f), zc, 0.02f, 0.08f, o.len * 0.96f, 0, trim)
            cube(x + dx, worldY(s, h - 0.15f), zc, 0.02f, 0.05f, o.len * 0.96f, 0, scale(trim, 0.6f))
        }
        cube(x, worldY(s, h + 0.07f), o.z + o.len * 0.3f, 0.52f, 0.14f, minOf(0.8f, o.len * 0.3f), scale(trim, 0.7f), spec = 0.5f)
    }

    private fun train(o: Obstacle, x: Float, zc: Float) {
        val s = o.side
        val h = Config.BLOCK_H
        val body = bio(o.z) { it.trainBody }
        val win = bio(o.z) { it.trainWin }
        cube(x, worldY(s, h / 2f), zc, OBST_W, h, o.len, body, spec = 0.7f, rim = 0.3f)
        cube(x, worldY(s, h * 0.68f), o.z - 0.012f, OBST_W * 0.8f, h * 0.28f, 0.03f, 0xFF141024.toInt(), scale(win, 0.4f), spec = 1f)
        for (dx in floatArrayOf(-OBST_W / 2f - 0.008f, OBST_W / 2f + 0.008f)) {
            cube(x + dx, worldY(s, 0.5f), zc, 0.02f, 0.1f, o.len * 0.96f, Palette.WHITE, scale(Palette.WHITE, 0.4f))
        }
        for (dx in floatArrayOf(-0.27f, 0.27f)) {
            push(); Mat4.translate(m, x + dx, worldY(s, 0.42f), o.z - 0.03f)
            push(); Mat4.scale(m, 0.17f, 0.17f, 0.17f); emit(MeshId.SPHERE, Palette.HEADLIGHT, Palette.HEADLIGHT); pop()
            Mat4.scale(m, 0.7f, 0.7f, 0.7f)
            emit(MeshId.SPHERE, 0, Palette.HEADLIGHT, alpha = 0.35f, additive = true)
            pop()
        }
    }

    private fun coin(c: Coin) {
        reset()
        Mat4.translate(m, c.x, c.y, c.z)
        Mat4.rotateY(m, Math.toDegrees(c.spin.toDouble()).toFloat())
        Mat4.rotateX(m, 90f)
        push(); Mat4.scale(m, 0.42f, 0.08f, 0.42f); emit(MeshId.CYLINDER, Palette.COIN_RIM, scale(Palette.COIN_RIM, 0.25f), spec = 1f); pop()
        Mat4.scale(m, 0.3f, 0.1f, 0.3f)
        emit(MeshId.CYLINDER, Palette.COIN, scale(Palette.COIN, 0.35f), spec = 1f)
    }

    private fun power(p: PowerUp, clock: Float) {
        val col = Palette.power(p.kind)
        reset()
        Mat4.translate(m, p.x, p.y + sin(clock * 4f + p.z) * 0.08f, p.z)
        Mat4.rotateY(m, clock * 140f)
        push(); Mat4.scale(m, 0.45f, 0.45f, 0.45f); emit(MeshId.CRYSTAL, col, scale(col, 0.55f), spec = 1f, rim = 0.8f); pop()
        Mat4.scale(m, 1.0f, 1.0f, 1.0f)
        emit(MeshId.SPHERE, 0, col, alpha = 0.3f, additive = true)
    }

    companion object {
        /** Цвет, умноженный на k (для приглушённого свечения). */
        fun scale(c: Int, k: Float): Int {
            val r = (((c shr 16) and 0xFF) * k).toInt().coerceIn(0, 255)
            val g = (((c shr 8) and 0xFF) * k).toInt().coerceIn(0, 255)
            val b = ((c and 0xFF) * k).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        /** 0xAARRGGBB → три числа 0..1, для GL-униформ и превью. */
        fun rgb3(out: FloatArray, c: Int) {
            out[0] = ((c shr 16) and 0xFF) / 255f
            out[1] = ((c shr 8) and 0xFF) / 255f
            out[2] = (c and 0xFF) / 255f
        }
    }
}
