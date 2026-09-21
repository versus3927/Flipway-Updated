package com.flipway.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameTest {

    private fun blocking(o: Obstacle) = o.type == ObType.BLOCK || o.type == ObType.TRAIN

    /** В каждом срезе уровня есть позиция (полоса × поверхность) без глухого блока. */
    @Test
    fun alwaysSomeWayThrough() {
        for (seed in 1L..20L) {
            val obs = ArrayList<Obstacle>()
            val sp = Spawner(obs, ArrayList(), ArrayList(), Random(seed))
            sp.reset()
            sp.fill(2000f, 20f)            // сгенерировать ~2 км вперёд без движения
            val statics = obs.filter { it.type == ObType.BLOCK }
            var z = 0f
            while (z < 2000f) {
                val blocked = statics.filter { z >= it.z && z <= it.z + it.len }
                    .map { it.lane to it.side }.toSet()
                assertTrue("seed $seed, z=$z: закрыты все 6 позиций", blocked.size < 6)
                z += 0.25f
            }
        }
    }

    /** Перед стеной и вдоль неё противоположная поверхность свободна от блоков. */
    @Test
    fun wallsLeaveOtherSurfaceOpen() {
        for (seed in 1L..20L) {
            val obs = ArrayList<Obstacle>()
            val sp = Spawner(obs, ArrayList(), ArrayList(), Random(seed))
            sp.reset()
            sp.fill(2000f, 20f)
            for (w in obs.filter { it.wall && it.lane == 0 }) {
                val from = w.z - 20f * Config.FLIP_TIME - 2f
                val clash = obs.filter { blocking(it) && it.side != w.side && it.type != ObType.TRAIN }
                    .any { it.z < w.z + w.len && it.z + it.len > from }
                assertTrue("seed $seed: стена на z=${w.z} без свободного пути", !clash)
            }
        }
    }

    @Test
    fun jumpAndSlideChangeHitbox() {
        val g = Game(1)
        g.jump(); repeat(10) { g.update(1 / 60f) }
        assertTrue("прыжок поднимает игрока", g.py0 > 0.5f)
        repeat(60) { g.update(1 / 60f) }
        g.slide(); g.update(1 / 60f)
        assertTrue("подкат уменьшает рост", g.py1 - g.py0 < Config.PLAYER_H)
    }

    @Test
    fun flipMovesToCeilingAndInvertsLanes() {
        val g = Game(2)
        g.flip()
        repeat(40) { g.update(1 / 60f) }
        assertEquals(CEIL, g.side)
        assertTrue("стоим на потолке", g.py1 > Config.TUNNEL_H - 0.01f)
        g.move(-1)                          // свайп влево на перевёрнутом экране
        assertEquals(1, g.lane)             // = вправо в мире
    }

    /** Пересекли 240 м пути — должны получить событие смены локации, попап и вспышку частиц. */
    @Test
    fun crossingBiomeBoundaryFiresEventAndPopup() {
        val g = Game(9)
        val bot = Bot(g)                   // без уклонения от препятствий забег не доедет до границы
        var sawEvent = false
        var t = 0f
        while (g.distance < Biomes.LEN + 5f && t < 60f) {
            bot.think(1 / 60f)
            g.update(1 / 60f)
            if (Event.BIOME in g.events) sawEvent = true
            t += 1 / 60f
        }
        assertTrue("не доехали до границы локации за 60 с (умер на ${g.distance} м)", g.distance >= Biomes.LEN)
        assertTrue("не было события BIOME при переходе через ${Biomes.LEN} м", sawEvent)
        assertTrue("должен появиться попап с названием локации", g.popups.any { it.text.contains(Biomes.list[1].name, ignoreCase = true) })
    }

    /** Простой бот должен уметь долго бежать — значит, уровень проходим. */
    @Test
    fun botSurvives() {
        var total = 0f
        for (seed in 1L..10L) {
            val g = Game(seed)
            val bot = Bot(g)
            var t = 0f
            while (t < 120f && g.state == Game.State.RUNNING) {
                bot.think(1 / 60f)
                g.update(1 / 60f)
                t += 1 / 60f
            }
            println("seed $seed: ${"%.1f".format(t)} c, очки ${g.score.toInt()}, монеты ${g.coinsRun}, стен ${g.wallsFlipped}")
            total += t
        }
        assertTrue("бот в среднем живёт меньше 60 с: ${total / 10}", total / 10 > 60f)
    }
}
