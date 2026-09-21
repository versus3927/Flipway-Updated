package com.flipway.game

import kotlin.math.abs

/** Автопилот (демо в меню и тесты): смотрит, сколько времени до ближайшего глухого блока в каждой позиции. */
class Bot(private val g: Game) {
    private var cooldown = 0f

    /** Секунды до удара о блок/вагон в позиции (lane, side). */
    private fun clearTime(lane: Int, side: Int): Float {
        var best = 99f
        for (o in g.obstacles) {
            if (o.destroyed || o.lane != lane || o.side != side) continue
            if (o.type == ObType.LOW || o.type == ObType.HIGH) continue
            if (o.z + o.len < -0.4f) continue
            val t = maxOf(0f, o.z) / (g.speed + o.speed)
            if (t < best) best = t
        }
        return best
    }

    fun think(dt: Float) {
        cooldown -= dt
        if (g.state != Game.State.RUNNING) return
        dodgeSmall()
        if (g.flipping || cooldown > 0f) return

        val here = clearTime(g.lane, g.side)
        if (here > 1.1f) return
        var bestLane = g.lane
        var bestSide = g.side
        var bestScore = here
        for (side in 0..1) for (lane in -1..1) {
            // идём только через безопасные полосы
            val step = if (lane > g.lane) 1 else -1
            var ok = true
            var l = g.lane
            while (l != lane) { l += step; if (clearTime(l, g.side) < 0.35f && side == g.side) ok = false }
            if (!ok) continue
            val cost = abs(lane - g.lane) * 0.12f + (if (side != g.side) Config.FLIP_TIME else 0f)
            val s = clearTime(lane, side) - cost
            if (s > bestScore + 0.05f) { bestScore = s; bestLane = lane; bestSide = side }
        }
        if (bestSide != g.side) {
            g.flip(); cooldown = 0.05f
        } else if (bestLane != g.lane) {
            val worldDir = if (bestLane > g.lane) 1 else -1
            g.move(if (g.side == CEIL) -worldDir else worldDir)
            cooldown = 0.1f
        }
    }

    /** Перепрыгнуть барьер, проскользнуть под аркой. */
    private fun dodgeSmall() {
        if (!g.onSurface) return
        for (o in g.obstacles) {
            if (o.lane != g.lane || o.side != g.side || o.destroyed) continue
            val t = o.z / g.speed
            if (t < 0f || t > 0.2f) continue
            if (o.type == ObType.LOW) g.jump()
            if (o.type == ObType.HIGH) g.slide()
        }
    }
}
