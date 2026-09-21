package com.flipway.game

import kotlin.random.Random

/**
 * Генерирует уровень кусками-«паттернами» впереди игрока.
 *
 * Главное правило честности: в любом ряду остаётся хотя бы одна проходимая
 * позиция (полоса × поверхность). Сплошная стена на все три полосы бывает
 * только на одной поверхности, а противоположная перед ней и на всю её длину
 * свободна — ровно столько, чтобы успеть перевернуться.
 */
class Spawner(
    private val obstacles: MutableList<Obstacle>,
    private val coins: MutableList<Coin>,
    private val powers: MutableList<PowerUp>,
    private val rnd: Random,
) {
    /** Z, где заканчивается уже сгенерированная часть (относительно игрока). */
    var cursor = 0f
        private set
    private var patterns = 0
    private var nextPower = 8

    fun reset() {
        cursor = 42f
        patterns = 0
        nextPower = 6 + rnd.nextInt(5)
    }

    fun update(dz: Float, speed: Float) {
        cursor -= dz
        fill(Config.DRAW_Z + 10f, speed)
    }

    /** Догенерировать уровень до limit (в тестах — сразу на километры вперёд). */
    fun fill(limit: Float, speed: Float) {
        while (cursor < limit) {
            val gap = maxOf(9f, speed * 0.75f)   // не меньше 0,75 с на реакцию и приземление
            cursor += spawnPattern(cursor, speed) + gap
            patterns++
        }
    }

    private fun spawnPattern(z: Float, speed: Float): Float {
        val d = ((speed - Config.SPEED_START) / (Config.SPEED_MAX - Config.SPEED_START)).coerceIn(0f, 1f)
        // первые паттерны — разминка без стен и поездов
        if (patterns < 3) return if (patterns == 0) coinLine(z, 0, FLOOR, 8) else single(z)
        val r = rnd.nextFloat()
        return when {
            r < 0.20f - d * 0.08f -> single(z)
            r < 0.42f -> row(z, if (rnd.nextFloat() < 0.25f + d * 0.3f) 3 else 2)
            r < 0.62f -> wall(z, speed)
            r < 0.76f + d * 0.06f -> train(z, speed)
            r < 0.88f -> coinArc(z)
            else -> double(z)
        }
    }

    private fun side() = if (rnd.nextFloat() < 0.5f) FLOOR else CEIL
    private fun lane() = rnd.nextInt(3) - 1
    private fun blockLen() = 3f + rnd.nextFloat() * 4f

    private fun randomType(allowBlock: Boolean): ObType {
        val r = rnd.nextInt(if (allowBlock) 3 else 2)
        return when (r) { 0 -> ObType.LOW; 1 -> ObType.HIGH; else -> ObType.BLOCK }
    }

    private fun add(type: ObType, lane: Int, side: Int, z: Float, wall: Boolean = false, speed: Float = 0f): Obstacle {
        val len = when (type) {
            ObType.LOW -> Config.LOW_LEN
            ObType.HIGH -> Config.HIGH_LEN
            ObType.BLOCK -> blockLen()
            ObType.TRAIN -> 6f + rnd.nextFloat() * 3f
        }
        val o = Obstacle(type, lane, side, z, len, speed, wall)
        obstacles += o
        return o
    }

    private fun coinLine(z: Float, lane: Int, side: Int, n: Int): Float {
        for (i in 0 until n) coins += Coin(lane * Config.LANE, worldY(side, Config.COIN_Y), z + i * Config.COIN_GAP)
        maybePower(z + n * Config.COIN_GAP + 2f, lane, side)
        return n * Config.COIN_GAP
    }

    private fun maybePower(z: Float, lane: Int, side: Int) {
        if (patterns < nextPower) return
        nextPower = patterns + 9 + rnd.nextInt(7)
        val kind = Power.values()[rnd.nextInt(Power.values().size)]
        powers += PowerUp(kind, lane * Config.LANE, worldY(side, 0.75f), z)
    }

    /** Одно препятствие и дорожка монет рядом. */
    private fun single(z: Float): Float {
        val s = side()
        val l = lane()
        val o = add(randomType(true), l, s, z)
        val cl = (l + 1 + rnd.nextInt(2)).let { if (it > 1) it - 3 else it }
        val cLen = coinLine(z - 2f, cl, s, 5)
        return maxOf(o.len, cLen)
    }

    /** Ряд из 2–3 препятствий; в ряду из трёх — без глухих блоков. */
    private fun row(z: Float, n: Int): Float {
        val s = side()
        val lanes = mutableListOf(-1, 0, 1).also { it.shuffle(rnd) }
        var len = 0f
        for (i in 0 until n) {
            val o = add(randomType(allowBlock = n < 3), lanes[i], s, z)
            len = maxOf(len, o.len)
        }
        if (n < 3) coinLine(z - 3f, lanes[2], s, 5)
        return len
    }

    /** Сплошная стена на одной поверхности: пройти можно только перевернувшись. */
    private fun wall(z: Float, speed: Float): Float {
        val s = side()
        val lead = speed * Config.FLIP_TIME + 6f
        val len = 3f + rnd.nextFloat() * 4f
        for (l in -1..1) obstacles += Obstacle(ObType.BLOCK, l, s, z + lead, len, wall = true)
        // монеты на противоположной поверхности подсказывают путь
        coinLine(z + lead - 2f, lane(), 1 - s, ((len + 5f) / Config.COIN_GAP).toInt())
        return lead + len
    }

    /** Встречный вагон. Его полоса свободна на всём участке, который он проедет. */
    private fun train(z: Float, speed: Float): Float {
        val s = side()
        val l = lane()
        val extra = Config.TRAIN_EXTRA_MIN + rnd.nextFloat() * (Config.TRAIN_EXTRA_MAX - Config.TRAIN_EXTRA_MIN)
        val reserve = z * extra / speed + 4f
        val t = add(ObType.TRAIN, l, s, z + reserve, speed = extra)
        val other = if (l == 0) (if (rnd.nextBoolean()) -1 else 1) else 0
        coinLine(z + 2f, other, s, minOf(10, (reserve / Config.COIN_GAP).toInt()))
        return reserve + t.len
    }

    /** Барьер с дугой монет над ним — подсказка «прыгай». */
    private fun coinArc(z: Float): Float {
        val s = side()
        val l = lane()
        add(ObType.LOW, l, s, z + 3f)
        for (i in 0..6) {
            val t = i / 6f
            val h = Config.COIN_Y + 1.1f * 4f * t * (1f - t)
            coins += Coin(l * Config.LANE, worldY(s, h), z + i * 1.05f)
        }
        maybePower(z + 9f, l, s)
        return 7f
    }

    /** Препятствия сразу на полу и на потолке, в разных полосах. */
    private fun double(z: Float): Float {
        val lanes = mutableListOf(-1, 0, 1).also { it.shuffle(rnd) }
        val a = add(randomType(true), lanes[0], FLOOR, z)
        val b = add(randomType(true), lanes[1], CEIL, z)
        coinLine(z - 2f, lanes[2], side(), 4)
        return maxOf(a.len, b.len)
    }
}
