package com.flipway.game

/**
 * Все числа игры в одном месте. Единицы мира условные: расстояние между
 * полосами = 1, туннель высотой TUNNEL_H, ось Z смотрит вперёд от игрока.
 */
object Config {
    // --- геометрия туннеля ---
    const val LANE = 1f               // шаг между полосами (-1, 0, 1)
    const val TUNNEL_HALF_W = 1.75f   // стены туннеля
    const val TUNNEL_H = 5.6f         // от пола до потолка

    // --- камера ---
    const val CAM_DIST = 4.2f         // камера позади игрока: больше — дальние препятствия крупнее
    const val LANE_SCREEN_FRAC = 0.28f // шаг полос на экране у ног игрока, доля ширины
    const val PLAYER_SCREEN_Y = 0.84f // где на экране стоят ноги игрока, доля высоты
    const val CAM_FOLLOW_X = 0.55f    // насколько камера следует за игроком вбок
    const val NEAR_Z = -CAM_DIST + 0.35f
    const val DRAW_Z = 95f            // дальность прорисовки

    // --- игрок ---
    const val PLAYER_H = 1.25f
    const val PLAYER_W = 0.55f
    const val PLAYER_DEPTH = 0.5f
    const val SLIDE_H = 0.55f
    const val SLIDE_TIME = 0.62f
    const val JUMP_V = 8.6f      // апекс ~1.1, в воздухе ~0.5 с
    const val GRAVITY = 34f
    const val FLIP_TIME = 0.42f       // переворот пол ↔ потолок
    const val LANE_LERP = 15f

    // --- скорость ---
    const val SPEED_START = 13f
    const val SPEED_MAX = 31f
    const val SPEED_GAIN = 0.11f      // прибавка в секунду

    // --- препятствия ---
    const val LOW_H = 0.6f            // барьер: перепрыгнуть
    const val LOW_LEN = 0.35f
    const val HIGH_BOTTOM = 0.8f      // лазерная арка: проскользнуть под ней
    const val HIGH_TOP = 1.35f
    const val HIGH_LEN = 0.3f
    const val BLOCK_H = 2.3f          // вагон/стена: только обойти или перевернуться
    const val OBST_W = 0.9f
    const val TRAIN_EXTRA_MIN = 5f    // встречный вагон едет навстречу
    const val TRAIN_EXTRA_MAX = 9f

    // --- монеты и бонусы ---
    const val COIN_Y = 0.55f
    const val COIN_GAP = 1.6f
    const val PICK_DX = 0.6f
    const val PICK_DZ = 0.9f
    const val MAGNET_RANGE = 16f
    const val POWER_BASE_TIME = 8f
    const val POWER_PER_LEVEL = 2f
    const val MAX_UPGRADE = 5

    // --- очки ---
    const val MULT_MAX = 6
    const val WALLS_PER_MULT = 3      // столько чистых переворотов через стену = +1 к множителю
    const val STUMBLE_WINDOW = 4f     // второй удар боком в это окно = конец забега
    const val CONTINUE_BASE_COST = 100
    const val MAX_CONTINUES = 2
    const val FLIP_TUTORIAL_RUNS = 3
}
