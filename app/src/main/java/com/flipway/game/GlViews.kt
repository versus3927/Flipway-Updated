package com.flipway.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.View
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/** 3D-мир на OpenGL ES 2.0. */
@SuppressLint("ViewConstructor")
class GlGameView(ctx: Context, director: Director, onFail: () -> Unit) : GLSurfaceView(ctx) {
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(ConfigChooser())
        preserveEGLContextOnPause = true
        setRenderer(GlRenderer(ctx, director) { post { onFail() } })
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /**
     * Сначала пробуем 24-битную глубину со сглаживанием 4x, потом без сглаживания,
     * и в самом конце — 16 бит, которые есть на любом устройстве с GLES 2.0.
     */
    private class ConfigChooser : EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val es2 = 4          // EGL_OPENGL_ES2_BIT
            val renderable = 0x3040  // EGL_RENDERABLE_TYPE
            val attempts = arrayOf(
                intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 24, renderable, es2,
                    EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, 4, EGL10.EGL_NONE),
                intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 24, renderable, es2, EGL10.EGL_NONE),
                intArrayOf(EGL10.EGL_RED_SIZE, 5, EGL10.EGL_GREEN_SIZE, 6, EGL10.EGL_BLUE_SIZE, 5,
                    EGL10.EGL_DEPTH_SIZE, 16, renderable, es2, EGL10.EGL_NONE),
            )
            for (spec in attempts) {
                val num = IntArray(1)
                if (!egl.eglChooseConfig(display, spec, null, 0, num) || num[0] <= 0) continue
                val configs = arrayOfNulls<EGLConfig>(num[0])
                if (egl.eglChooseConfig(display, spec, configs, num[0], num)) configs.firstOrNull { it != null }?.let { return it }
            }
            throw IllegalStateException("Нет подходящей конфигурации EGL")
        }
    }
}

/** Прозрачный слой поверх 3D: меню, HUD и касания рисуются обычным Canvas. */
@SuppressLint("ViewConstructor")
class HudView(ctx: Context, private val director: Director) : View(ctx) {
    override fun onDraw(c: Canvas) {
        director.drawHud(c)
        postInvalidateOnAnimation()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean = director.touch(e, width)
}
