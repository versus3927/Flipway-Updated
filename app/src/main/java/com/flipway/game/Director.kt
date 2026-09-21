package com.flipway.game

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Всё, кроме рисования мира: экраны, забег, ввод, звук, вибрация, сохранения.
 * Им пользуются и 3D-вид (OpenGL), и запасной 2D-вид на Canvas.
 * Все публичные методы синхронизированы на самом объекте: поток рендера и
 * UI-поток (касания, HUD) обращаются к нему одновременно.
 */
class Director(ctx: Context) {
    private val save = Save(ctx)
    private val sfx = Sfx(ctx).also { it.enabled = save.sound }
    private val ui = Ui()
    private val screens = Screens(ui, save)
    @Suppress("DEPRECATION")
    private val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?

    var game = Game(); private set
    private var demo: Bot? = Bot(game)            // автопилот бежит на фоне меню
    var screen = Screen.MENU; private set
    var skin = Palette.skins[save.skin]; private set
    var clock = 0f; private set
    private var banked = 0                        // монеты забега, уже переложенные в кошелёк

    private var downX = 0f
    private var downY = 0f
    private var swiped = false

    // ------------------------------------------------------------ кадр

    @Synchronized
    fun step(dt: Float) {
        clock += dt
        when (screen) {
            Screen.MENU, Screen.SHOP -> {
                demo?.think(dt)
                game.update(dt)
                if (game.state == Game.State.DEAD && game.deadTime > 1.2f) newDemo()
            }
            Screen.PLAY -> {
                game.update(dt)
                for (e in game.events) {
                    sfx.play(e)
                    if (e == Event.STUMBLE) vibrate(40)
                    if (e == Event.BIOME) vibrate(70)
                }
                sfx.startMusic()
                if (game.state == Game.State.DEAD) onCrash()
            }
            Screen.CONTINUE -> {
                game.update(dt)
                screens.continueLeft -= dt
                if (screens.continueLeft <= 0f) finishRun()
            }
            Screen.OVER -> game.update(dt)
            Screen.PAUSE -> {}
        }
    }

    @Synchronized
    fun drawHud(c: Canvas) = screens.draw(c, screen, game, clock)

    // ------------------------------------------------------------ жизненный цикл

    @Synchronized
    fun pause() {
        if (screen == Screen.PLAY) screen = Screen.PAUSE
        sfx.stopMusic()
    }

    fun release() = sfx.release()

    /** Системная «назад»: true — обработали сами. */
    @Synchronized
    fun back(): Boolean = when (screen) {
        Screen.PLAY -> { screen = Screen.PAUSE; sfx.stopMusic(); true }
        Screen.SHOP, Screen.OVER, Screen.PAUSE -> { toMenu(); true }
        Screen.CONTINUE -> { finishRun(); true }
        Screen.MENU -> false
    }

    // ------------------------------------------------------------ забег

    private fun newDemo() {
        game = Game()
        demo = Bot(game)
    }

    private fun startRun() {
        game = Game(upgrades = save.upgrades())
        demo = null
        banked = 0
        screen = Screen.PLAY
    }

    private fun bank() {
        save.wallet = save.wallet + game.coinsRun - banked
        banked = game.coinsRun
    }

    private fun onCrash() {
        vibrate(160)
        sfx.stopMusic()
        bank()
        if (game.canContinue && save.wallet >= game.continueCost) {
            screens.continueLeft = 5f
            screen = Screen.CONTINUE
        } else finishRun()
    }

    private fun finishRun() {
        bank()
        screens.lastCoins = game.coinsRun
        val s = game.score.toInt()
        screens.newRecord = s > save.best
        if (screens.newRecord) save.best = s
        save.runs = save.runs + 1
        screen = Screen.OVER
    }

    private fun toMenu() {
        if (screen == Screen.PAUSE) {
            // выход из паузы: монеты и рекорд забега всё равно засчитываются
            bank()
            val s = game.score.toInt()
            if (s > save.best) save.best = s
            save.runs = save.runs + 1
        }
        newDemo()
        screen = Screen.MENU
    }

    private fun vibrate(ms: Long) {
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(ms)
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------ ввод

    @Synchronized
    fun touch(e: MotionEvent, width: Int): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; swiped = false }
            MotionEvent.ACTION_POINTER_DOWN -> if (screen == Screen.PLAY) game.flip()   // второй палец — переворот
            MotionEvent.ACTION_MOVE -> if (screen == Screen.PLAY) {
                if (!swiped) {
                    val dx = e.x - downX
                    val dy = e.y - downY
                    if (maxOf(abs(dx), abs(dy)) > width * 0.06f) {
                        swiped = true
                        when {
                            abs(dx) > abs(dy) -> game.move(if (dx > 0) 1 else -1)
                            dy < 0 -> game.jump()
                            else -> game.slide()
                        }
                    }
                }
            } else if (screen == Screen.SHOP) {
                // прокрутка списка бонусов и скинов
                val dy = e.y - downY
                if (abs(dy) > 3f) {
                    screens.shopScroll -= dy
                    downY = e.y
                    swiped = true      // не открывать то, что под пальцем, как тап при отпускании
                }
            }
            MotionEvent.ACTION_UP -> if (!swiped) tap(e.x, e.y)
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        val id = ui.hit(x, y)
        when (screen) {
            Screen.PLAY -> if (id == "pause") { screen = Screen.PAUSE; sfx.stopMusic() } else game.flip()
            Screen.MENU -> when (id) {
                "play" -> startRun()
                "shop" -> { screen = Screen.SHOP; screens.shopScroll = 0f }
                "sound" -> { save.sound = !save.sound; sfx.enabled = save.sound }
            }
            Screen.SHOP -> shopTap(id)
            Screen.PAUSE -> when (id) {
                "resume" -> screen = Screen.PLAY
                "menu" -> toMenu()
            }
            Screen.CONTINUE -> when (id) {
                "revive" -> if (save.wallet >= game.continueCost) {
                    save.wallet = save.wallet - game.continueCost
                    game.revive()
                    screen = Screen.PLAY
                }
                "giveup" -> finishRun()
            }
            Screen.OVER -> when (id) {
                "retry" -> startRun()
                "menu" -> toMenu()
            }
        }
    }

    private fun shopTap(id: String?) {
        id ?: return
        when {
            id == "back" -> screen = Screen.MENU
            id.startsWith("up_") -> {
                val p = Power.valueOf(id.removePrefix("up_"))
                val cost = Save.upgradeCost(save.level(p))
                if (cost in 1..save.wallet) {
                    save.wallet = save.wallet - cost
                    save.setLevel(p, save.level(p) + 1)
                    sfx.play(Event.POWER)
                }
            }
            id.startsWith("buy_") -> {
                val i = id.removePrefix("buy_").toInt()
                val price = Palette.skins[i].price
                if (save.wallet >= price) {
                    save.wallet = save.wallet - price
                    save.own(i); save.skin = i
                    skin = Palette.skins[i]
                    sfx.play(Event.WALL_BONUS)
                }
            }
            id.startsWith("skin_") -> {
                val i = id.removePrefix("skin_").toInt()
                save.skin = i
                skin = Palette.skins[i]
            }
        }
    }
}
