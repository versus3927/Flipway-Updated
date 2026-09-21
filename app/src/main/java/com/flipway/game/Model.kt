package com.flipway.game

import com.flipway.game.Config.BLOCK_H
import com.flipway.game.Config.HIGH_BOTTOM
import com.flipway.game.Config.HIGH_TOP
import com.flipway.game.Config.LOW_H
import com.flipway.game.Config.TUNNEL_H

/** Поверхности туннеля. */
const val FLOOR = 0
const val CEIL = 1

/** Высота над поверхностью side → мировая Y (пол = 0, потолок = TUNNEL_H). */
fun worldY(side: Int, h: Float): Float = if (side == FLOOR) h else TUNNEL_H - h

enum class ObType { LOW, HIGH, BLOCK, TRAIN }

enum class Power(val title: String) { MAGNET("Магнит"), SHIELD("Щит"), DOUBLE("Монеты x2") }

/** События кадра — по ним вид играет звук и вибрацию. */
enum class Event { COIN, JUMP, SLIDE, FLIP, LANE, POWER, STUMBLE, CRASH, SHIELD_BREAK, WALL_BONUS, BIOME }

class Obstacle(
    val type: ObType,
    val lane: Int,
    val side: Int,
    var z: Float,              // ближний к игроку край
    val len: Float,
    val speed: Float = 0f,     // собственная скорость навстречу (для TRAIN)
    val wall: Boolean = false, // часть сплошной стены на все три полосы
) {
    var passed = false
    var destroyed = false

    /** Высоты над своей поверхностью. */
    val h0: Float get() = if (type == ObType.HIGH) HIGH_BOTTOM else 0f
    val h1: Float get() = when (type) {
        ObType.LOW -> LOW_H
        ObType.HIGH -> HIGH_TOP
        else -> BLOCK_H
    }

    /** Мировой вертикальный диапазон (с учётом того, что на потолке всё перевёрнуто). */
    val wy0: Float get() = minOf(worldY(side, h0), worldY(side, h1))
    val wy1: Float get() = maxOf(worldY(side, h0), worldY(side, h1))
}

/** Монета в мировых координатах. */
class Coin(var x: Float, var y: Float, var z: Float) {
    var taken = false
    var pulled = false         // летит к игроку под магнитом
    var spin = (x * 7f + z) % 6.28f
}

class PowerUp(val kind: Power, val x: Float, val y: Float, var z: Float) {
    var taken = false
}

class Particle(
    var x: Float, var y: Float, var z: Float,
    val vx: Float, val vy: Float, val vz: Float,
    var life: Float, val color: Int,
) {
    val maxLife = life
}

class Popup(val text: String, val color: Int, var life: Float = 1.1f)
