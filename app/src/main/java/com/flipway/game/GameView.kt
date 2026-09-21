package com.flipway.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.random.Random

/**
 * Запасной 2D-вид на Canvas (псевдо-3D). Включается, только если на телефоне
 * не завёлся OpenGL ES 2.0. Логика та же — вся в Director.
 */
@SuppressLint("ViewConstructor")
class GameView(ctx: Context, private val director: Director) : SurfaceView(ctx), SurfaceHolder.Callback, Runnable {
    private val cam = Camera()
    private val world = WorldRenderer(cam)
    private val player = PlayerRenderer(cam)
    private val shakeRnd = Random(1)

    @Volatile private var running = false
    private var thread: Thread? = null
    private var surfaceReady = false

    init { holder.addCallback(this) }

    override fun surfaceCreated(h: SurfaceHolder) { surfaceReady = true; start() }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
    override fun surfaceDestroyed(h: SurfaceHolder) { surfaceReady = false; stop() }

    fun resume() { if (surfaceReady) start() }
    fun pause() = stop()

    private fun start() {
        if (running) return
        running = true
        thread = Thread(this, "flipway-2d").also { it.start() }
    }

    private fun stop() {
        running = false
        thread?.join(600)
        thread = null
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1e9f).coerceIn(0f, 1f / 20f)
            last = now
            val c: Canvas? = try {
                if (Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
            } catch (e: Exception) { null }
            if (c == null) { Thread.sleep(16); continue }
            try {
                synchronized(director) { director.step(dt); render(c) }
            } finally {
                try { holder.unlockCanvasAndPost(c) } catch (_: Exception) {}
            }
        }
    }

    private fun render(c: Canvas) {
        if (cam.w != c.width.toFloat() || cam.h != c.height.toFloat()) cam.resize(c.width, c.height)
        val game = director.game
        player.skin = director.skin
        c.drawColor(Palette.FOG)
        cam.camX = game.camX
        val rollDeg = Math.toDegrees(game.roll().toDouble()).toFloat()
        c.save()
        if (game.shake > 0f) {
            val a = game.shake * cam.w * 0.02f
            c.translate((shakeRnd.nextFloat() - 0.5f) * a, (shakeRnd.nextFloat() - 0.5f) * a)
        }
        c.rotate(rollDeg, cam.cx, cam.cy)
        world.drawTunnel(c, game)
        world.drawScene(c, game, rollDeg) { player.draw(it, game, rollDeg, director.clock) }
        c.restore()
        director.drawHud(c)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean = director.touch(e, width)
}
