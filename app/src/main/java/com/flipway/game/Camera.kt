package com.flipway.game

import com.flipway.game.Config.CAM_DIST
import com.flipway.game.Config.TUNNEL_H

/**
 * Перспективная проекция. Камера висит на оси туннеля (Y = TUNNEL_H/2) позади
 * игрока, поэтому переворот гравитации — это просто поворот картинки на 180°
 * вокруг точки (cx, cy): пол и потолок симметричны относительно камеры.
 */
class Camera {
    var w = 1f; private set
    var h = 1f; private set
    var f = 1f; private set      // фокус в пикселях
    var cx = 0f; private set
    var cy = 0f; private set
    var camX = 0f

    // результат последнего project()
    var sx = 0f; private set
    var sy = 0f; private set
    var scale = 0f; private set  // пикселей на единицу мира на этой глубине

    fun resize(width: Int, height: Int) {
        w = width.toFloat(); h = height.toFloat()
        f = Config.LANE_SCREEN_FRAC * w * CAM_DIST / Config.LANE
        cx = w / 2f
        cy = (Config.PLAYER_SCREEN_Y * h - (TUNNEL_H / 2f) * f / CAM_DIST).coerceIn(h * 0.3f, h * 0.55f)
    }

    /** false — точка за ближней плоскостью, рисовать нельзя. */
    fun project(x: Float, y: Float, z: Float): Boolean {
        val depth = z + CAM_DIST
        if (depth < 0.05f) return false
        scale = f / depth
        sx = cx + (x - camX) * scale
        sy = cy - (y - TUNNEL_H / 2f) * scale
        return true
    }
}
