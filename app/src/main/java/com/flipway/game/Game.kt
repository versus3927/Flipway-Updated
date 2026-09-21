package com.flipway.game

import com.flipway.game.Config.LANE
import com.flipway.game.Config.PLAYER_H
import com.flipway.game.Config.TUNNEL_H
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Состояние забега. Чистый Kotlin без Android — поэтому проверяется JVM-тестами.
 * Z отсчитывается от игрока вперёд; мир едет навстречу, игрок стоит на z = 0.
 */
class Game(seed: Long = System.nanoTime(), private val upgrades: IntArray = IntArray(3)) {
    enum class State { RUNNING, DEAD }

    val rnd = Random(seed)
    val obstacles = ArrayList<Obstacle>()
    val coins = ArrayList<Coin>()
    val powers = ArrayList<PowerUp>()
    val particles = ArrayList<Particle>()
    val popups = ArrayList<Popup>()
    val events = ArrayList<Event>()
    private val spawner = Spawner(obstacles, coins, powers, rnd)

    var state = State.RUNNING; private set
    var time = 0f; private set
    var deadTime = 0f; private set
    var speed = Config.SPEED_START; private set
    var score = 0f; private set
    var distance = 0f; private set
    var coinsRun = 0; private set
    var wallsFlipped = 0; private set
    var mult = 1; private set
    var continues = 0; private set

    // --- игрок ---
    var lane: Int = 0; private set
    private var prevLane: Int = 0
    var x: Float = 0f; private set
    var side: Int = FLOOR; private set
    var wallRunT: Float = 0f; private set
    var isWallRunning get() = wallRunT > 0f
    var flipT: Float = 1f; private set
    private var flipY0: Float = 0f
    var h: Float = 0f; private set
    private var vy: Float = 0f
    var slideT: Float = 0f; private set
    var runPhase: Float = 0f; private set
    var invuln: Float = 0f; private set
    private var stumbleT: Float = 0f
    private var bufJump: Float = 0f
    private var bufSlide: Float = 0f
    var shake: Float = 0f; private set
    var camX: Float = 0f; private set
    private var biomeIdx: Int = 0
    private var _flipT: Float = 1f
    var py0: Float = 0f; private set
    var py1: Float = PLAYER_H; private set

    val powerTime = FloatArray(Power.values().size)

    init {
        spawner.reset()
        spawner.update(0f, speed)
    }

    val flipping get() = _flipT < 1f
    val sliding get() = slideT > 0f
    val onSurface get() = !flipping && h <= 0f
    fun active(p: Power) = powerTime[p.ordinal] > 0f
    fun powerDuration(p: Power) = Config.POWER_BASE_TIME + upgrades[p.ordinal] * Config.POWER_PER_LEVEL
    val continueCost get() = Config.CONTINUE_BASE_COST shl continues
    val canContinue get() = continues < Config.MAX_CONTINUES

    /** Крен камеры: пол — 0, потолок — π; переворот всегда крутит в одну сторону. */
    fun roll(): Float {
        val e = ease(_flipT)
        return if (side == CEIL) (PI.toFloat() * e) else (PI.toFloat() * (1f + e)) % (2f * PI.toFloat())
    }

    fun ease(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    // ---------------- управление ----------------

    /** dir — направление свайпа на экране; на потолке экран перевёрнут. */
    fun move(dir: Int) {
        if (state != State.RUNNING) return
        val d = if (side == CEIL) -dir else dir
        val n = (lane + d).coerceIn(-1, 1)
        if (n != lane) { prevLane = lane; lane = n; events += Event.LANE }
    }

    fun jump() { if (state == State.RUNNING) bufJump = 0.18f }

    fun slide() {
        if (state != State.RUNNING) return
        bufSlide = 0.3f
        if (!onSurface && !flipping) vy = min(vy, -14f)   // в воздухе — резко вниз
    }

    fun flip() {
        if (state != State.RUNNING || flipping) return
        flipY0 = (py0 + py1) / 2f
        side = 1 - side
        _flipT = 0f
        h = 0f; vy = 0f; slideT = 0f
        events += Event.FLIP
    }

    // ---------------- кадр ----------------

    fun update(dt: Float) {
        events.clear()
        updateEffects(dt, if (state == State.RUNNING) speed * dt else 0f)
        if (state == State.DEAD) { deadTime += dt; return }

        time += dt
        speed = min(Config.SPEED_MAX, Config.SPEED_START + time * Config.SPEED_GAIN)
        val dz = speed * dt
        score += dz * mult
        distance += dz
        checkBiome()
        runPhase += dt * speed * 0.85f
        for (o in obstacles) o.z -= dz + o.speed * dt
        for (c in coins) if (!c.pulled) c.z -= dz
        for (p in powers) p.z -= dz
        spawner.update(dz, speed)

        updatePlayer(dt)
        collide()
        pickups(dt)
        cleanup()
    }

    private fun updatePlayer(dt: Float) {
        x += (lane * LANE - x) * min(1f, dt * Config.LANE_LERP)
        camX += (x * Config.CAM_FOLLOW_X - camX) * min(1f, dt * 8f)
        bufJump -= dt; bufSlide -= dt; slideT -= dt
        invuln -= dt; stumbleT -= dt; shake = maxOf(0f, shake - dt * 2.5f)
        for (i in powerTime.indices) powerTime[i] = maxOf(0f, powerTime[i] - dt)

        if (flipping) {
            _flipT = min(1f, _flipT + dt / Config.FLIP_TIME)
        } else {
            if (h > 0f || vy > 0f) {
                // Wall run check: if at edge and jumping
                if (abs(lane) == 1 && h > 0.1f && !isWallRunning) {
                    wallRunT = 1.5f
                    vy = 0f // stay at current height
                    events += Event.LANE
                }
                
                if (isWallRunning) {
                    wallRunT -= dt
                    if (wallRunT <= 0f) {
                        wallRunT = 0f
                        vy = -5f // push off wall
                    }
                } else {
                    vy -= Config.GRAVITY * dt
                    h += vy * dt
                    if (h <= 0f) { h = 0f; vy = 0f }
                }
            }
            if (onSurface && bufJump > 0f) {
                bufJump = 0f; bufSlide = 0f; slideT = 0f
                vy = Config.JUMP_V; h = 0.001f
                events += Event.JUMP
            } else if (onSurface && bufSlide > 0f) {
                bufSlide = 0f
                if (!sliding) events += Event.SLIDE
                slideT = Config.SLIDE_TIME
            }
        }

        if (flipping) {
            val target = worldY(side, PLAYER_H / 2f)
            val c = flipY0 + (target - flipY0) * ease(_flipT)
            py0 = c - PLAYER_H / 2f; py1 = c + PLAYER_H / 2f
        } else {
            val body = if (sliding) Config.SLIDE_H else PLAYER_H
            if (side == FLOOR) { py0 = h; py1 = h + body } else { py0 = TUNNEL_H - h - body; py1 = TUNNEL_H - h }
        }
    }

    private fun collide() {
        val hx0 = x - Config.PLAYER_W / 2f
        val hx1 = x + Config.PLAYER_W / 2f
        val hz = Config.PLAYER_DEPTH / 2f
        for (o in obstacles) {
            if (o.destroyed) continue
            if (!o.passed && o.z + o.len < -hz) {
                o.passed = true
                if (o.wall && o.lane == 0 && side != o.side) wallBonus()
            }
            if (o.z > hz || o.z + o.len < -hz) continue
            // в кувырке переворота барьеры и арки не цепляют — только глухие блоки
            if (flipping && (o.type == ObType.LOW || o.type == ObType.HIGH)) continue
            val ox = o.lane * LANE
            if (hx1 < ox - Config.OBST_W / 2f || hx0 > ox + Config.OBST_W / 2f) continue
            if (py1 < o.wy0 || py0 > o.wy1) continue
            hit(o)
            if (state == State.DEAD) return
        }
    }

    private fun hit(o: Obstacle) {
        if (invuln > 0f) return
        if (active(Power.SHIELD)) {
            o.destroyed = true
            powerTime[Power.SHIELD.ordinal] = 0f
            invuln = 0.6f; shake = 0.6f
            burst(o.lane * LANE, (o.wy0 + o.wy1) / 2f, 0.5f, 0xFF7DF9FF.toInt(), 26)
            events += Event.SHIELD_BREAK
            return
        }
        val sideways = abs(x - lane * LANE) > 0.12f && o.z < -0.1f
        if (sideways && prevLane != lane) {
            // зацепил боком — отбросило обратно; второй раз подряд — конец
            if (stumbleT > 0f) return crash()
            stumbleT = Config.STUMBLE_WINDOW
            lane = prevLane
            invuln = 0.3f   // пока отъезжаем обратно, этот же блок не должен добить
            shake = 0.5f
            events += Event.STUMBLE
            popups += Popup("ОСТОРОЖНО!", 0xFFFFB02E.toInt())
            return
        }
        crash()
    }

    private fun crash() {
        state = State.DEAD
        deadTime = 0f
        shake = 1f
        burst(x, (py0 + py1) / 2f, 0f, 0xFFFF2BD6.toInt(), 40)
        events += Event.CRASH
    }

    /** Пересекли границу локации — звук, вибро-щелчок в HUD и вспышка частиц у ворот. */
    private fun checkBiome() {
        val idx = Biomes.currentIndex(distance)
        if (idx == biomeIdx) return
        biomeIdx = idx
        val b = Biomes.list[idx]
        events += Event.BIOME
        popups += Popup("${b.icon} ${b.name.uppercase()}", b.ring)
        burst(0f, TUNNEL_H / 2f, 0f, b.edge, 30)
    }

    private fun wallBonus() {
        wallsFlipped++
        mult = min(Config.MULT_MAX, 1 + wallsFlipped / Config.WALLS_PER_MULT)
        score += 50f * mult
        events += Event.WALL_BONUS
        popups += Popup("ПЕРЕВОРОТ! +${50 * mult}", 0xFF23F0FF.toInt())
    }

    private fun pickups(dt: Float) {
        val cy = (py0 + py1) / 2f
        val magnet = active(Power.MAGNET)
        val k = min(1f, dt * 11f)
        for (c in coins) {
            if (c.taken) continue
            c.spin += dt * 5f
            if (magnet && c.z < Config.MAGNET_RANGE && c.z > -1f) c.pulled = true
            if (c.pulled) { c.x += (x - c.x) * k; c.y += (cy - c.y) * k; c.z += (0f - c.z) * k }
            if (abs(c.x - x) < Config.PICK_DX && abs(c.z) < Config.PICK_DZ && c.y > py0 - 0.4f && c.y < py1 + 0.4f) {
                c.taken = true
                coinsRun += if (active(Power.DOUBLE)) 2 else 1
                events += Event.COIN
                burst(c.x, c.y, c.z, 0xFFFFD84A.toInt(), 5)
            }
        }
        for (p in powers) {
            if (p.taken) continue
            if (abs(p.x - x) < Config.PICK_DX && abs(p.z) < Config.PICK_DZ && p.y > py0 - 0.5f && p.y < py1 + 0.5f) {
                p.taken = true
                powerTime[p.kind.ordinal] = powerDuration(p.kind)
                events += Event.POWER
                popups += Popup(p.kind.title.uppercase(), 0xFF9DFF6A.toInt())
            }
        }
    }

    private fun cleanup() {
        val back = -Config.CAM_DIST - 1f
        obstacles.removeAll { it.z + it.len < back }
        coins.removeAll { it.taken || it.z < back }
        powers.removeAll { it.taken || it.z < back }
    }

    private fun updateEffects(dt: Float, dz: Float) {
        for (p in particles) {
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt - dz
            p.life -= dt
        }
        particles.removeAll { it.life <= 0f }
        for (p in popups) p.life -= dt
        popups.removeAll { it.life <= 0f }
    }

    fun burst(x: Float, y: Float, z: Float, color: Int, n: Int) {
        repeat(n) {
            particles += Particle(
                x, y, z,
                (rnd.nextFloat() - 0.5f) * 6f, (rnd.nextFloat() - 0.5f) * 6f, (rnd.nextFloat() - 0.3f) * 6f,
                0.35f + rnd.nextFloat() * 0.4f, color,
            )
        }
    }

    /** Продолжить после столкновения: расчистить путь и дать секунду неуязвимости. */
    fun revive() {
        if (state != State.DEAD) return
        continues++
        obstacles.removeAll { it.z < 30f }
        state = State.RUNNING
        invuln = 2f
        stumbleT = 0f
    }

    /** Ближайшая сплошная стена на текущей поверхности — для подсказки новичку. */
    fun wallAhead(): Float? =
        obstacles.filter { it.wall && it.side == side && it.z > 0f }.minOfOrNull { it.z }
}
