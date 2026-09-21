package com.flipway.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sin

enum class Screen { MENU, SHOP, PLAY, PAUSE, CONTINUE, OVER }

/** Все экраны поверх мира: меню, HUD, пауза, продолжение, итоги, магазин. */
class Screens(private val ui: Ui, private val save: Save) {
    var newRecord = false
    var continueLeft = 0f
    var lastCoins = 0

    fun draw(c: Canvas, s: Screen, g: Game, clock: Float) {
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        ui.begin(w)
        when (s) {
            Screen.MENU -> menu(c, w, h, clock)
            Screen.SHOP -> shop(c, w, h)
            Screen.PLAY -> hud(c, w, h, g, clock)
            Screen.PAUSE -> { hud(c, w, h, g, clock); pause(c, w, h) }
            Screen.CONTINUE -> { hud(c, w, h, g, clock); cont(c, w, h, g) }
            Screen.OVER -> over(c, w, h, g, clock)
        }
    }

    private fun wallet(c: Canvas, w: Float, y: Float) {
        val u = ui.u
        val s = save.wallet.toString()
        val tw = ui.textWidth(s, 5f)
        ui.coin(c, w - 6f * u - tw - 3.5f * u, y - 1.7f * u, 2.4f * u)
        ui.text(c, s, w - 5f * u, y, 5f, Palette.YELLOW, Paint.Align.RIGHT)
    }

    private fun menu(c: Canvas, w: Float, h: Float, clock: Float) {
        val u = ui.u
        ui.dim(c, 110)
        wallet(c, w, 9f * u)
        val ty = h * 0.2f
        ui.text(c, "FLIPWAY", w / 2f + 0.8f * u, ty + 0.8f * u, 17f, Palette.PINK)
        ui.text(c, "FLIPWAY", w / 2f, ty, 17f, Palette.CYAN, glow = true)
        ui.text(c, "ГРАВИ-РАННЕР", w / 2f, ty + 8f * u, 4.6f, Palette.PINK, glow = true)
        ui.text(c, "беги по полу — беги по потолку", w / 2f, ty + 15f * u, 3.8f, 0xCCFFFFFF.toInt())
        ui.text(c, "РЕКОРД  ${save.best}", w / 2f, h * 0.38f, 5f, Palette.WHITE)

        val pulse = 1f + sin(clock * 4f) * 0.03f
        ui.button(c, "play", "ИГРАТЬ", w / 2f, h * 0.5f, 62f * pulse, 14f, Palette.CYAN, size = 7.5f)
        ui.button(c, "shop", "МАГАЗИН", w / 2f, h * 0.5f + 18f * u, 50f, 10.5f, Palette.PINK)
        ui.button(c, "sound", if (save.sound) "ЗВУК: ВКЛ" else "ЗВУК: ВЫКЛ", w / 2f, h * 0.5f + 32f * u, 40f, 8f,
            0xFF8A7CFF.toInt(), size = 3.8f)

        val top = h * 0.77f
        ui.panel(c, RectF(6f * u, top, w - 6f * u, top + 30f * u), Palette.CYAN)
        ui.text(c, "СВАЙП ← →   сменить полосу", w / 2f, top + 8f * u, 3.8f, Palette.WHITE)
        ui.text(c, "СВАЙП ↑ прыжок     СВАЙП ↓ подкат", w / 2f, top + 15f * u, 3.8f, Palette.WHITE)
        ui.text(c, "ТАП — переворот: пол ↔ потолок", w / 2f, top + 23f * u, 4.2f, Palette.CYAN, glow = true)
    }

    private fun hud(c: Canvas, w: Float, h: Float, g: Game, clock: Float) {
        val u = ui.u
        ui.text(c, g.score.toInt().toString(), w - 4f * u, 11f * u, 7.5f, Palette.WHITE, Paint.Align.RIGHT, glow = true)
        if (g.mult > 1) ui.text(c, "x${g.mult}", w - 4f * u, 17.5f * u, 5f, Palette.PINK, Paint.Align.RIGHT, glow = true)
        ui.coin(c, 7f * u, 8.6f * u, 2.6f * u)
        ui.text(c, g.coinsRun.toString(), 11f * u, 10.6f * u, 5.5f, Palette.YELLOW, Paint.Align.LEFT)
        ui.button(c, "pause", "II", w / 2f, 8.5f * u, 11f, 9f, 0xFF8A7CFF.toInt(), size = 4.5f)
        val biome = Biomes.atPlayer(g.distance)
        ui.text(c, "${biome.icon} ${biome.name}", w / 2f, 15.5f * u, 3f, withAlpha(biome.ring, 210), Paint.Align.CENTER)

        var y = 16f * u
        for (p in Power.values()) {
            if (!g.active(p)) continue
            ui.text(c, p.title, 4f * u, y, 3.2f, Palette.power(p), Paint.Align.LEFT)
            ui.bar(c, 4f * u, y + 1.3f * u, 26f * u, 1.6f * u, g.powerTime[p.ordinal] / g.powerDuration(p), Palette.power(p))
            y += 7f * u
        }

        for ((i, p) in g.popups.withIndex()) {
            val a = (255 * (p.life / 1.1f).coerceIn(0f, 1f)).toInt()
            val py = h * 0.3f - (1.1f - p.life) * 12f * u + i * 7f * u
            ui.text(c, p.text, w / 2f, py, 6f, withAlpha(p.color, a), glow = true)
        }

        if (save.runs < Config.FLIP_TUTORIAL_RUNS) {
            val wall = g.wallAhead()
            if (wall != null && wall < 48f && !g.flipping) {
                val a = (180 + 75 * sin(clock * 10f)).toInt()
                ui.text(c, "СТЕНА! ТАПНИ — ПЕРЕВОРОТ", w / 2f, h * 0.44f, 5.6f, withAlpha(Palette.CYAN, a), glow = true)
            } else if (g.time < 5f) {
                ui.text(c, "свайпы — движение, тап — переворот", w / 2f, h * 0.44f, 4.2f, 0xCCFFFFFF.toInt())
            }
        }
    }

    private fun pause(c: Canvas, w: Float, h: Float) {
        val u = ui.u
        ui.dim(c, 170)
        ui.text(c, "ПАУЗА", w / 2f, h * 0.36f, 11f, Palette.CYAN, glow = true)
        ui.button(c, "resume", "ПРОДОЛЖИТЬ", w / 2f, h * 0.5f, 62f, 13f, Palette.CYAN, size = 6f)
        ui.button(c, "menu", "В МЕНЮ", w / 2f, h * 0.5f + 17f * u, 46f, 10f, Palette.PINK)
    }

    private fun cont(c: Canvas, w: Float, h: Float, g: Game) {
        val u = ui.u
        ui.dim(c, 150)
        val r = RectF(8f * u, h * 0.3f, w - 8f * u, h * 0.3f + 72f * u)
        ui.panel(c, r)
        ui.text(c, "ВРЕЗАЛСЯ!", w / 2f, r.top + 13f * u, 9f, Palette.PINK, glow = true)
        ui.text(c, "Продолжить с этого места?", w / 2f, r.top + 22f * u, 4.2f, Palette.WHITE)
        ui.bar(c, r.left + 8f * u, r.top + 27f * u, r.width() - 16f * u, 1.6f * u, continueLeft / 5f, Palette.PINK)
        val cost = g.continueCost
        ui.button(c, "revive", "ПРОДОЛЖИТЬ · $cost", w / 2f, r.top + 41f * u, 66f, 12f, Palette.YELLOW,
            enabled = save.wallet >= cost, size = 5f)
        ui.text(c, "в кошельке: ${save.wallet}", w / 2f, r.top + 52f * u, 3.6f, 0xCCFFFFFF.toInt())
        ui.button(c, "giveup", "НЕТ, ЗАКОНЧИТЬ", w / 2f, r.top + 62f * u, 50f, 9f, 0xFF8A7CFF.toInt(), size = 4f)
    }

    private fun over(c: Canvas, w: Float, h: Float, g: Game, clock: Float) {
        val u = ui.u
        ui.dim(c, 175)
        ui.text(c, "ЗАБЕГ ОКОНЧЕН", w / 2f, h * 0.2f, 8f, Palette.PINK, glow = true)
        ui.text(c, g.score.toInt().toString(), w / 2f, h * 0.2f + 20f * u, 15f, Palette.WHITE, glow = true)
        if (newRecord) {
            val a = (190 + 65 * sin(clock * 8f)).toInt()
            ui.text(c, "НОВЫЙ РЕКОРД!", w / 2f, h * 0.2f + 30f * u, 6f, withAlpha(Palette.YELLOW, a), glow = true)
        } else {
            ui.text(c, "рекорд: ${save.best}", w / 2f, h * 0.2f + 30f * u, 4.5f, 0xCCFFFFFF.toInt())
        }
        val y = h * 0.2f + 44f * u
        ui.text(c, "монет: +$lastCoins", w / 2f, y, 5f, Palette.YELLOW)
        ui.text(c, "переворотов через стену: ${g.wallsFlipped}", w / 2f, y + 8f * u, 4.2f, Palette.CYAN)
        ui.text(c, "множитель: x${g.mult}", w / 2f, y + 15f * u, 4.2f, Palette.PINK)
        ui.button(c, "retry", "ЕЩЁ РАЗ", w / 2f, h * 0.66f, 62f, 13f, Palette.CYAN, size = 6.5f)
        ui.button(c, "menu", "МЕНЮ", w / 2f, h * 0.66f + 17f * u, 44f, 10f, Palette.PINK)
    }

    var shopScroll = 0f
    var shopMax = 0f; private set

    /** Прокачка бонусов и скины — список длиннее экрана, поэтому прокручивается свайпом. */
    private fun shop(c: Canvas, w: Float, h: Float) {
        val u = ui.u
        ui.dim(c, 215)
        val top = 20f * u
        val bottom = h - 13f * u
        val row = 15f * u

        // -------- подсчёт полной высоты содержимого --------
        val contentH = 6f * u + Power.values().size * row + 2f * u + 6f * u + Palette.skins.size * row
        shopMax = maxOf(0f, contentH - (bottom - top))
        shopScroll = shopScroll.coerceIn(0f, shopMax)

        c.save()
        c.clipRect(0f, top, w, bottom)
        var y = top - shopScroll
        ui.text(c, "ПРОКАЧКА БОНУСОВ", 6f * u, y + 4f * u, 3.8f, Palette.PINK, Paint.Align.LEFT)
        y += 6f * u
        for (p in Power.values()) {
            val lv = save.level(p)
            ui.panel(c, RectF(4f * u, y, w - 4f * u, y + row - 1.5f * u), Palette.power(p))
            val mid = y + (row - 1.5f * u) / 2f
            ui.text(c, p.title, 8f * u, mid - 0.5f * u, 4.3f, Palette.power(p), Paint.Align.LEFT)
            val secs = (Config.POWER_BASE_TIME + lv * Config.POWER_PER_LEVEL).toInt()
            ui.text(c, "$secs с", 8f * u, mid + 4.5f * u, 3.2f, 0xCCFFFFFF.toInt(), Paint.Align.LEFT)
            for (i in 0 until Config.MAX_UPGRADE) {
                ui.circle(c, 40f * u + i * 4.2f * u, mid, 1.3f * u, if (i < lv) Palette.power(p) else 0xFF3A3558.toInt())
            }
            val cost = Save.upgradeCost(lv)
            if (cost < 0) ui.text(c, "МАКС", w - 17f * u, mid + 1.5f * u, 4f, Palette.GREEN)
            else ui.button(c, "up_${p.name}", "$cost", w - 17f * u, mid, 22f, row / u * 0.55f, Palette.YELLOW,
                enabled = save.wallet >= cost, size = 4f)
            y += row
        }
        y += 2f * u
        ui.text(c, "СКИНЫ", 6f * u, y + 4f * u, 3.8f, Palette.PINK, Paint.Align.LEFT)
        y += 6f * u
        for ((i, s) in Palette.skins.withIndex()) {
            ui.panel(c, RectF(4f * u, y, w - 4f * u, y + row - 1.5f * u), s.accent)
            val mid = y + (row - 1.5f * u) / 2f
            ui.circle(c, 11f * u, mid, 3.6f * u, s.accent)
            ui.circle(c, 11f * u, mid, 2.5f * u, s.body)
            ui.text(c, s.name, 18f * u, mid + 1.6f * u, 4.5f, Palette.WHITE, Paint.Align.LEFT)
            val bx = w - 17f * u
            val bh = row / u * 0.55f
            when {
                save.skin == i -> ui.text(c, "ВЫБРАН", bx, mid + 1.5f * u, 4f, Palette.GREEN)
                save.owned(i) -> ui.button(c, "skin_$i", "ВЫБРАТЬ", bx, mid, 22f, bh, Palette.CYAN, size = 3.6f)
                else -> ui.button(c, "buy_$i", "${s.price}", bx, mid, 22f, bh, Palette.YELLOW,
                    enabled = save.wallet >= s.price, size = 4f)
            }
            y += row
        }
        c.restore()

        // шапка и кнопка «назад» рисуются поверх, без прокрутки
        ui.text(c, "МАГАЗИН", 6f * u, 11f * u, 8f, Palette.CYAN, Paint.Align.LEFT, glow = true)
        wallet(c, w, 10.5f * u)
        if (shopMax > 1f) {
            val frac = shopScroll / shopMax
            ui.bar(c, w - 3.2f * u, top, 1.4f * u, bottom - top, 1f, 0x33FFFFFF)
            ui.bar(c, w - 3.2f * u, top + (bottom - top - 6f * u) * frac, 1.4f * u, 6f * u, 1f, Palette.CYAN)
        }
        ui.button(c, "back", "НАЗАД", w / 2f, h - 6.5f * u, 44f, 9f, Palette.PINK)
    }
}
