package com.flipway.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiomesTest {

    @Test
    fun cyclesThroughAllFourLocationsInOrder() {
        // на границах (чуть после кратного LEN) должны идти по кругу: Сакура → Неон → Лава → Лёд → Сакура…
        for (lap in 0..2) for (i in Biomes.list.indices) {
            val d = (lap * Biomes.list.size + i) * Biomes.LEN + 1f
            assertEquals("круг $lap, локация $i", i, Biomes.currentIndex(d))
        }
    }

    @Test
    fun boundaryAheadStaysInRange() {
        var d = 0f
        while (d < Biomes.LEN * 6f) {
            val ahead = Biomes.boundaryAhead(d)
            assertTrue("расстояние до границы вне (0, LEN]: $ahead на d=$d", ahead > 0f && ahead <= Biomes.LEN)
            d += 3.7f
        }
    }

    @Test
    fun blendIsZeroFarFromBoundaryAndOneAtIt() {
        val d = 10f
        val ahead = Biomes.boundaryAhead(d)
        assertEquals(0f, Biomes.blendAt(d, ahead - Biomes.TRANS - 5f), 1e-5f)
        assertEquals(1f, Biomes.blendAt(d, ahead), 1e-5f)
        assertTrue("блендинг должен нарастать", Biomes.blendAt(d, ahead - Biomes.TRANS / 2f) in 0f..1f)
    }

    @Test
    fun colorMatchesCurrentAndNextAtBlendExtremes() {
        val d = 5f
        val ahead = Biomes.boundaryAhead(d)
        val farZ = ahead - Biomes.TRANS - 10f
        val atGate = ahead
        assertEquals(Biomes.current(d).floor, Biomes.colorAt(d, farZ) { it.floor })
        assertEquals(Biomes.next(d).floor, Biomes.colorAt(d, atGate) { it.floor })
    }

    /** Бейдж в HUD должен совпадать с тем, что уже видно у ног игрока, а не с формальным индексом. */
    @Test
    fun atPlayerMatchesVisibleColorNearBoundary() {
        val d = Biomes.LEN - 2f            // почти у самой границы: рядом с игроком уже видна следующая локация
        assertEquals(Biomes.next(d).name, Biomes.atPlayer(d).name)
        val far = 10f                       // далеко от границы: всё ещё текущая
        assertEquals(Biomes.current(far).name, Biomes.atPlayer(far).name)
    }

    @Test
    fun onlySakuraHasBlossomTextureAndCanopy() {
        val sakura = Biomes.list[0]
        assertTrue("Сакура должна быть светлой дневной локацией", sakura.tex == 1 && sakura.canopy)
        for (b in Biomes.list.drop(1)) {
            assertTrue("${b.name}: не должна получить цветочный узор Сакуры", b.tex == 0 && !b.canopy)
        }
        for (b in Biomes.list) assertTrue("${b.name}: bright вне 0..1", b.bright in 0f..1f)
    }

    @Test
    fun everyLocationHasTwoBlockTrimColors() {
        for (b in Biomes.list) assertTrue("${b.name}: нужно 2 цвета отделки блока", b.blockTrim.size >= 2)
    }
}
