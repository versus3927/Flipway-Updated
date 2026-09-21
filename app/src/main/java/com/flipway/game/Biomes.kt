package com.flipway.game

import kotlin.math.floor

/** Что рисуют декоративные частицы фона у локации: лепестки, искры, снег, звёзды. */
enum class Ambient { NONE, PETAL, EMBER, SNOW, SPARK }

/**
 * Оформление одной локации: цвета туннеля, препятствий и частиц. Само число —
 * не геометрия, а только материал: форму объектов не меняем, чтобы механика
 * (габариты препятствий, честность прохода) осталась той же, что уже проверена
 * тестами. Локации сменяют друг друга каждые [Biomes.LEN] метров пути.
 */
class BiomeStyle(
    val name: String,
    val icon: String,
    // туннель
    val floor: Int, val wall: Int, val fog: Int,
    val edge: Int, val ring: Int,
    // препятствия
    val barrier: Int, val barrierTrim: Int,
    val laser: Int,
    val blockBody: Int, val blockTrim: IntArray,
    val trainBody: Int, val trainWin: Int,
    // фон
    val ambient: Ambient, val ambientColor: Int,
    // освещение и узор поверхностей (см. Shaders.kt): bright — дневная подсветка теней
    // (обычная тёмная неоновая ночь ≈ 0.3, яркий день ≈ 0.6), glow — сила дымки-bloom
    // у горизонта, tex — 0 техно-панели, 1 цветущие пятна (только Сакура)
    val bright: Float = 0.3f, val glow: Float = 1f, val tex: Int = 0,
    val canopy: Boolean = false,
)

/**
 * Локации сменяют друг друга прямо во время забега, по кругу. Первая — Сакура,
 * дальше три придуманные: неоновый мегаполис, лавовые пещеры, ледяной грот.
 * Вся смена цвета идёт по пройденному расстоянию [Game.distance], поэтому
 * логика проверяется обычными JVM-тестами без графики.
 */
object Biomes {
    const val LEN = 240f              // метров на одну локацию
    const val TRANS = 26f             // ширина зоны смены (метров)

    private val sakura = BiomeStyle(
        name = "Сакура", icon = "🌸",
        floor = 0xFF6E3F52.toInt(), wall = 0xFF5A3446.toInt(), fog = 0xFF8A5670.toInt(),
        edge = 0xFFFF7FC0.toInt(), ring = 0xFFFFF3F8.toInt(),
        barrier = 0xFF7A1830.toInt(), barrierTrim = 0xFFFFE8F2.toInt(),
        laser = 0xFFFF5C8A.toInt(),
        blockBody = 0xFF5C1D30.toInt(), blockTrim = intArrayOf(0xFFFF3D7A.toInt(), 0xFFFFD24A.toInt()),
        trainBody = 0xFF7A2440.toInt(), trainWin = 0xFFFFE3B0.toInt(),
        ambient = Ambient.PETAL, ambientColor = 0xFFFFC2DD.toInt(),
        bright = 0.62f, glow = 2.3f, tex = 1, canopy = true,
    )

    private val outdoor = BiomeStyle(
        name = "Улица", icon = "🌳",
        floor = 0xFF4A4A4A.toInt(), wall = 0xFF87CEEB.toInt(), fog = 0xFFB0C4DE.toInt(),
        edge = 0xFFFFFFFF.toInt(), ring = 0xFFD3D3D3.toInt(),
        barrier = 0xFF556B2F.toInt(), barrierTrim = 0xFFBDB76B.toInt(),
        laser = 0xFFFFD700.toInt(),
        blockBody = 0xFF8B4513.toInt(), blockTrim = intArrayOf(0xFFA0522D.toInt(), 0xFFDEB887.toInt()),
        trainBody = 0xFFC0C0C0.toInt(), trainWin = 0xFFFFFFFF.toInt(),
        ambient = Ambient.PETAL, ambientColor = 0xFF90EE90.toInt(),
        bright = 0.8f, glow = 1.5f, canopy = false,
    )

    private val pugs = BiomeStyle(
        name = "Мопсы", icon = "🐶",
        floor = 0xFFD2B48C.toInt(), wall = 0xFFEEDC82.toInt(), fog = 0xFFFFFACD.toInt(),
        edge = 0xFFFF69B4.toInt(), ring = 0xFFFFB6C1.toInt(),
        barrier = 0xFFCD853F.toInt(), barrierTrim = 0xFFFFF0F5.toInt(),
        laser = 0xFFFA8072.toInt(),
        blockBody = 0xFF8B4513.toInt(), blockTrim = intArrayOf(0xFFF4A460.toInt(), 0xFFDEB887.toInt()),
        trainBody = 0xFFBC8F8F.toInt(), trainWin = 0xFFFFFFFF.toInt(),
        ambient = Ambient.SPARK, ambientColor = 0xFFFFC0CB.toInt(),
        bright = 0.7f, glow = 2.0f, canopy = true,
    )

    private val neon = BiomeStyle(
        name = "Неон-сити", icon = "🏙",
        floor = Palette.FLOOR_A, wall = Palette.WALL_A, fog = Palette.FOG,
        edge = Palette.CYAN, ring = Palette.PINK,
        barrier = Palette.LOW_FRONT, barrierTrim = Palette.WHITE,
        laser = Palette.LASER,
        blockBody = Palette.BLOCK_FRONT, blockTrim = intArrayOf(Palette.CYAN, Palette.PINK),
        trainBody = Palette.TRAIN_FRONT, trainWin = Palette.TRAIN_WIN,
        ambient = Ambient.SPARK, ambientColor = Palette.CYAN,
    )

    private val ember = BiomeStyle(
        name = "Лавовые пещеры", icon = "🌋",
        floor = 0xFF2E1108.toInt(), wall = 0xFF351205.toInt(), fog = 0xFF160501.toInt(),
        edge = 0xFFFF6A1F.toInt(), ring = 0xFFFFC24A.toInt(),
        barrier = 0xFF7A1D0D.toInt(), barrierTrim = 0xFFFFB23D.toInt(),
        laser = 0xFFFF4B1F.toInt(),
        blockBody = 0xFF3D1710.toInt(), blockTrim = intArrayOf(0xFFFF4B1F.toInt(), 0xFFFFD24A.toInt()),
        trainBody = 0xFF7A2A12.toInt(), trainWin = 0xFFFFD8A0.toInt(),
        ambient = Ambient.EMBER, ambientColor = 0xFFFF8A3D.toInt(),
    )

    private val frost = BiomeStyle(
        name = "Ледяной грот", icon = "❄",
        floor = 0xFF102834.toInt(), wall = 0xFF163040.toInt(), fog = 0xFF071319.toInt(),
        edge = 0xFF7FE0FF.toInt(), ring = 0xFFEAFBFF.toInt(),
        barrier = 0xFF1C4A5C.toInt(), barrierTrim = 0xFFEAFBFF.toInt(),
        laser = 0xFF5CD8FF.toInt(),
        blockBody = 0xFF15323F.toInt(), blockTrim = intArrayOf(0xFF7FE0FF.toInt(), 0xFFFFFFFF.toInt()),
        trainBody = 0xFF1E4A5C.toInt(), trainWin = 0xFFE0FBFF.toInt(),
        ambient = Ambient.SNOW, ambientColor = 0xFFEAFBFF.toInt(),
    )

    val list = listOf(sakura, outdoor, neon, ember, frost, pugs)

    private fun cellOf(distance: Float) = floor(distance / LEN).toInt()

    private fun wrap(i: Int) = ((i % list.size) + list.size) % list.size

    /** Индекс локации, в которой сейчас находится игрок. */
    fun currentIndex(distance: Float) = wrap(cellOf(distance))

    fun current(distance: Float): BiomeStyle = list[currentIndex(distance)]
    fun next(distance: Float): BiomeStyle = list[wrap(cellOf(distance) + 1)]

    /**
     * Какая локация визуально преобладает прямо у игрока под ногами (z = 0).
     * Из-за широкой зоны смены дальний туннель начинает окрашиваться в цвета
     * следующей локации заранее — эта функция даёт то же название и цвет,
     * что уже видно рядом с игроком, чтобы бейдж в HUD не расходился с картинкой.
     */
    fun atPlayer(distance: Float): BiomeStyle = if (blendAt(distance, 0f) > 0.5f) next(distance) else current(distance)

    /** Сколько метров впереди по курсу до границы со следующей локацией, [0, LEN). */
    fun boundaryAhead(distance: Float): Float = LEN * (cellOf(distance) + 1) - distance

    /**
     * 0..1: насколько точка на глубине z впереди игрока уже относится к следующей
     * локации. Плавно нарастает на последних [TRANS] метрах текущей локации —
     * тем же способом красится и статичная геометрия туннеля в шейдере (там это
     * считается для каждого пикселя от той же величины [boundaryAhead]).
     */
    fun blendAt(distance: Float, z: Float): Float {
        val ahead = boundaryAhead(distance)
        return ((z - (ahead - TRANS)) / TRANS).coerceIn(0f, 1f)
    }

    /** Цвет объекта на глубине z: плавно смешивает текущую и следующую локацию. */
    fun colorAt(distance: Float, z: Float, pick: (BiomeStyle) -> Int): Int =
        lerpColor(pick(current(distance)), pick(next(distance)), blendAt(distance, z))
}
