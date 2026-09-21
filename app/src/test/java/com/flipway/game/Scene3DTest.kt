package com.flipway.game

import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверки 3D-сцены без телефона: камера, крен при перевороте, корректность матриц. */
class Scene3DTest {

    /** Проекция мировой точки в NDC через матрицу вида-проекции сцены. */
    private fun ndc(s: Scene3D, x: Float, y: Float, z: Float): FloatArray {
        val m = s.viewProj
        val cx = m[0] * x + m[4] * y + m[8] * z + m[12]
        val cy = m[1] * x + m[5] * y + m[9] * z + m[13]
        val cw = m[3] * x + m[7] * y + m[11] * z + m[15]
        return floatArrayOf(cx / cw, cy / cw, cw)
    }

    private fun assertRunnerAtBottom(g: Game, s: Scene3D, label: String) {
        s.build(g, 1080, 2340, 0f)
        val p = ndc(s, g.x, (g.py0 + g.py1) / 2f, 0f)
        assertTrue("$label: бегун перед камерой (w=${p[2]})", p[2] > 0f)
        assertTrue("$label: бегун по центру по горизонтали (x=${p[0]})", kotlin.math.abs(p[0]) < 0.15f)
        assertTrue("$label: бегун в нижней части экрана (y=${p[1]})", p[1] < -0.3f && p[1] > -0.9f)
    }

    @Test
    fun runnerStaysAtBottomOnFloorAndCeiling() {
        val g = Game(seed = 3)
        val s = Scene3D()
        g.update(1 / 60f)
        assertRunnerAtBottom(g, s, "пол")
        g.flip()
        repeat(40) { g.update(1 / 60f) }
        assertTrue(g.side == CEIL)
        assertRunnerAtBottom(g, s, "потолок")
    }

    @Test
    fun ceilingIsUpWhenOnFloorAndDownWhenOnCeiling() {
        val g = Game(seed = 4)
        val s = Scene3D()
        g.update(1 / 60f)
        s.build(g, 1080, 2340, 0f)
        assertTrue("на полу потолок сверху", ndc(s, 0f, Config.TUNNEL_H, 10f)[1] > ndc(s, 0f, 0f, 10f)[1])
        g.flip(); repeat(40) { g.update(1 / 60f) }
        s.build(g, 1080, 2340, 0f)
        assertTrue("на потолке потолок снизу", ndc(s, 0f, Config.TUNNEL_H, 10f)[1] < ndc(s, 0f, 0f, 10f)[1])
    }

    @Test
    fun allDrawMatricesAreFinite() {
        val g = Game(seed = 5)
        val bot = Bot(g)
        val s = Scene3D()
        repeat(60 * 40) {
            bot.think(1 / 60f); g.update(1 / 60f)
            if (it % 30 == 0) {
                s.build(g, 720, 1600, it / 60f)
                assertTrue("есть что рисовать", s.opaque.size > 5)
                for (d in s.opaque + s.transparent) for (v in d.model) assertTrue("NaN в матрице", v.isFinite())
            }
        }
    }

    /** Цвета локаций для шейдера всегда валидны — даже при переходе через несколько границ подряд. */
    @Test
    fun biomeUniformsStayValidAcrossFullCycle() {
        val g = Game(seed = 6)
        val bot = Bot(g)
        val s = Scene3D()
        var checked = 0
        while (g.distance < Biomes.LEN * Biomes.list.size + 20f && checked < 20000) {
            bot.think(1 / 60f); g.update(1 / 60f); checked++
            if (checked % 15 != 0) continue
            s.build(g, 720, 1600, checked / 60f)
            assertTrue("boundaryZ конечен", s.boundaryZ.isFinite())
            assertTrue("boundaryZ в (0, LEN]", s.boundaryZ > 0f && s.boundaryZ <= Biomes.LEN)
            for (arr in listOf(s.bioFloorA, s.bioFloorB, s.bioWallA, s.bioWallB, s.bioEdgeA, s.bioEdgeB, s.bioRingA, s.bioRingB, s.bioFogA, s.bioFogB)) {
                for (v in arr) assertTrue("цвет локации вне 0..1: $v", v in 0f..1f)
            }
            for (v in listOf(s.bioBrightA, s.bioBrightB)) assertTrue("bright вне 0..1: $v", v in 0f..1f)
            for (v in listOf(s.bioGlowA, s.bioGlowB)) assertTrue("glow конечен и не отрицателен: $v", v.isFinite() && v >= 0f)
            for (v in listOf(s.bioTexA, s.bioTexB)) assertTrue("tex должен быть 0 или 1: $v", v == 0f || v == 1f)
        }
        assertTrue("прошли хотя бы один полный круг локаций", g.distance > Biomes.LEN * Biomes.list.size)
    }
}
