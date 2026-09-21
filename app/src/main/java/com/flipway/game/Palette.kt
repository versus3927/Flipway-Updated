package com.flipway.game

/** Цвета игры: тёмный фиолетовый туннель, неон циан/маджента. */
object Palette {
    const val FOG = 0xFF07031A.toInt()
    const val FLOOR_A = 0xFF24124F.toInt()
    const val FLOOR_B = 0xFF1A0D3C.toInt()
    const val WALL_A = 0xFF150B35.toInt()
    const val WALL_B = 0xFF100829.toInt()
    const val LANE_DASH = 0xFF5B3BB0.toInt()

    const val CYAN = 0xFF23F0FF.toInt()
    const val PINK = 0xFFFF2BD6.toInt()
    const val PINK_GLOW = 0x40FF2BD6
    const val YELLOW = 0xFFFFD84A.toInt()
    const val GREEN = 0xFF9DFF6A.toInt()
    const val ORANGE = 0xFFFFB02E.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    const val LOW_FRONT = 0xFFE0264F.toInt()
    const val LOW_TOP = 0xFFFF5C7C.toInt()
    const val LOW_SIDE = 0xFF9E1535.toInt()
    const val POST = 0xFF7A80A8.toInt()
    const val LASER = 0xFFFF3355.toInt()
    const val BLOCK_FRONT = 0xFF34296B.toInt()
    const val BLOCK_TOP = 0xFF4E3F96.toInt()
    const val BLOCK_SIDE = 0xFF231A4D.toInt()
    const val BLOCK_WIN = 0xFF1FB8D6.toInt()
    const val TRAIN_FRONT = 0xFFC2410C.toInt()
    const val TRAIN_TOP = 0xFFF97316.toInt()
    const val TRAIN_SIDE = 0xFF7C2D12.toInt()
    const val TRAIN_WIN = 0xFFFDE68A.toInt()
    const val HEADLIGHT = 0xFFFFF7D6.toInt()
    const val COIN = 0xFFFFD84A.toInt()
    const val COIN_RIM = 0xFFC98A12.toInt()

    fun power(p: Power) = when (p) {
        Power.MAGNET -> 0xFFFF4D6D.toInt()
        Power.SHIELD -> CYAN
        Power.DOUBLE -> GREEN
    }

    fun powerIcon(p: Power) = when (p) {
        Power.MAGNET -> "U"
        Power.SHIELD -> "Щ"
        Power.DOUBLE -> "x2"
    }

    /** shine — множитель блеска бликов (обычные скины 1, «Хром» сильно глянцевый). */
    class Skin(val name: String, val body: Int, val accent: Int, val price: Int, val shine: Float = 1f)

    val skins = listOf(
        Skin("Неон", 0xFFC9D3F2.toInt(), CYAN, 0),
        Skin("Сакура", 0xFF3A1E2A.toInt(), 0xFFFF6FB5.toInt(), 550),
        Skin("Магма", 0xFF2A1010.toInt(), 0xFFFF6A00.toInt(), 900),
        Skin("Токсик", 0xFF14261A.toInt(), 0xFF7CFF3A.toInt(), 1300),
        Skin("Мороз", 0xFF102A34.toInt(), 0xFF7FE0FF.toInt(), 1700),
        Skin("Коралл", 0xFF3A1A16.toInt(), 0xFFFF6B5B.toInt(), 2200),
        Skin("Фантом", 0xFF1B1030.toInt(), PINK, 2800),
        Skin("Плазма", 0xFF200A3A.toInt(), 0xFFC13CFF.toInt(), 3600),
        Skin("Полночь", 0xFF0A0A12.toInt(), 0xFF9B5CFF.toInt(), 4500),
        Skin("Золото", 0xFF3B2A06.toInt(), YELLOW, 5500),
        Skin("Хром", 0xFF3A3F4A.toInt(), 0xFF7CFFE8.toInt(), 7000, shine = 2.4f),
    )
}

fun lerpColor(a: Int, b: Int, t: Float): Int {
    val u = 1f - t
    val aa = (a ushr 24) and 0xFF
    val r = (((a shr 16) and 0xFF) * u + ((b shr 16) and 0xFF) * t).toInt()
    val g = (((a shr 8) and 0xFF) * u + ((b shr 8) and 0xFF) * t).toInt()
    val bl = ((a and 0xFF) * u + (b and 0xFF) * t).toInt()
    return (aa shl 24) or (r shl 16) or (g shl 8) or bl
}

fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
