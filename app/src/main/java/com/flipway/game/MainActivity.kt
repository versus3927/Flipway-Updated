package com.flipway.game

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout

class MainActivity : Activity() {

    private lateinit var director: Director
    private var glView: GlGameView? = null
    private var flatView: GameView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        director = Director(this)
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (am.deviceConfigurationInfo.reqGlEsVersion >= 0x20000) {
            // основной режим: 3D-мир + прозрачный слой интерфейса поверх
            val root = FrameLayout(this)
            glView = GlGameView(this, director) { switchToFlat() }.also { root.addView(it) }
            root.addView(HudView(this, director))
            setContentView(root)
        } else {
            switchToFlat()
        }
        hideSystemUi()
    }

    /** Запасной путь: OpenGL не завёлся — та же игра в 2D на Canvas. */
    private fun switchToFlat() {
        if (flatView != null) return
        glView?.onPause()
        glView = null
        flatView = GameView(this, director).also { setContentView(it) }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        glView?.onResume()
        flatView?.resume()
    }

    override fun onPause() {
        super.onPause()
        director.pause()
        glView?.onPause()
        flatView?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        director.release()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!director.back()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }
}
