package com.flipway.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Звук без ассетов: эффекты и музыкальная петля синтезируются при первом запуске
 * в WAV-файлы в кэше и играются через SoundPool.
 */
class Sfx(ctx: Context) {
    private val rate = 22050
    private val rnd = Random(7)
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val ids = HashMap<Event, Int>()
    private var musicId = 0
    private var musicStream = 0
    private val loaded: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()  // колбэк приходит из другого потока
    var enabled = true
        set(v) { field = v; if (!v) stopMusic() }

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id }
        val dir = File(ctx.cacheDir, "sfx_v1").apply { mkdirs() }
        for (e in Event.values()) ids[e] = pool.load(file(dir, e.name) { synth(e) }.path, 1)
        musicId = pool.load(file(dir, "music") { music() }.path, 1)
    }

    fun play(e: Event) {
        if (!enabled) return
        val id = ids[e] ?: return
        if (id !in loaded) return
        val vol = if (e == Event.COIN) 0.5f else 0.9f
        pool.play(id, vol, vol, 1, 0, if (e == Event.COIN) 1f + rnd.nextFloat() * 0.08f else 1f)
    }

    fun startMusic() {
        if (!enabled || musicStream != 0 || musicId !in loaded) return
        musicStream = pool.play(musicId, 0.4f, 0.4f, 0, -1, 1f)
    }

    fun stopMusic() {
        if (musicStream != 0) pool.stop(musicStream)
        musicStream = 0
    }

    fun release() = pool.release()

    // ------------------------------------------------------------ синтез

    private fun file(dir: File, name: String, gen: () -> FloatArray): File {
        val f = File(dir, "$name.wav")
        if (!f.exists()) writeWav(f, gen())
        return f
    }

    private fun buf(sec: Float) = FloatArray((sec * rate).toInt())
    private fun tone(out: FloatArray, from: Int, len: Int, f0: Float, f1: Float, amp: Float, decay: Float, square: Boolean = false) {
        var ph = 0.0
        for (i in 0 until len) {
            val k = from + i
            if (k >= out.size) break
            val t = i.toFloat() / len
            ph += 2 * PI * (f0 + (f1 - f0) * t) / rate
            val s = sin(ph).toFloat()
            val v = if (square) (if (s > 0) 0.6f else -0.6f) else s
            out[k] += v * amp * exp(-decay * t) * minOf(1f, i / 60f)
        }
    }
    private fun noise(out: FloatArray, from: Int, len: Int, amp: Float, decay: Float) {
        var lp = 0f
        for (i in 0 until len) {
            val k = from + i
            if (k >= out.size) break
            lp += (rnd.nextFloat() * 2f - 1f - lp) * 0.35f
            out[k] += lp * amp * exp(-decay * i.toFloat() / len)
        }
    }
    private fun n(sec: Float) = (sec * rate).toInt()

    private fun synth(e: Event): FloatArray = when (e) {
        Event.COIN -> buf(0.16f).also { tone(it, 0, n(0.06f), 1320f, 1320f, 0.5f, 2f); tone(it, n(0.05f), n(0.11f), 1760f, 1760f, 0.5f, 4f) }
        Event.JUMP -> buf(0.18f).also { tone(it, 0, n(0.18f), 280f, 720f, 0.45f, 3f, square = true) }
        Event.SLIDE -> buf(0.22f).also { noise(it, 0, n(0.22f), 0.7f, 3f) }
        Event.FLIP -> buf(0.4f).also { tone(it, 0, n(0.4f), 180f, 1100f, 0.45f, 2.5f); noise(it, 0, n(0.4f), 0.35f, 2f) }
        Event.LANE -> buf(0.07f).also { noise(it, 0, n(0.07f), 0.5f, 5f) }
        Event.POWER -> buf(0.3f).also { for ((i, f) in floatArrayOf(660f, 880f, 1100f, 1320f).withIndex()) tone(it, n(0.06f * i), n(0.1f), f, f, 0.4f, 3f, square = true) }
        Event.STUMBLE -> buf(0.2f).also { tone(it, 0, n(0.2f), 160f, 70f, 0.8f, 4f); noise(it, 0, n(0.1f), 0.5f, 4f) }
        Event.CRASH -> buf(0.7f).also { noise(it, 0, n(0.7f), 1f, 4f); tone(it, 0, n(0.5f), 110f, 40f, 0.9f, 3f) }
        Event.SHIELD_BREAK -> buf(0.4f).also { for (i in 0..4) tone(it, n(0.03f * i), n(0.3f), 1800f + i * 370f, 1500f + i * 300f, 0.25f, 5f) }
        Event.WALL_BONUS -> buf(0.4f).also { for ((i, f) in floatArrayOf(523f, 659f, 784f, 1047f).withIndex()) tone(it, n(0.05f * i), n(0.25f), f, f, 0.35f, 2.5f) }
        Event.BIOME -> buf(0.9f).also {
            tone(it, 0, n(0.9f), 260f, 1400f, 0.4f, 1.6f)          // восходящий свист портала
            noise(it, n(0.05f), n(0.5f), 0.25f, 2.5f)
            for ((i, f) in floatArrayOf(784f, 988f, 1175f).withIndex()) tone(it, n(0.35f + 0.08f * i), n(0.3f), f, f, 0.3f, 2f)
        }
    }

    /** 8-секундная синтвейв-петля: бочка, бас восьмыми, хэт и арпеджио. */
    private fun music(): FloatArray {
        val out = buf(8f)
        val beat = n(0.5f)
        val roots = floatArrayOf(110f, 87.31f, 130.81f, 98f)        // Am F C G
        val thirds = floatArrayOf(1.19f, 1.26f, 1.26f, 1.26f)
        for (b in 0 until 16) {
            val at = b * beat
            tone(out, at, n(0.16f), 150f, 45f, 0.9f, 3f)                     // бочка
            noise(out, at + beat / 2, n(0.04f), 0.25f, 6f)                    // хэт на слабую долю
            val r = roots[b / 4]
            tone(out, at, beat / 2, r, r, 0.3f, 2.5f, square = true)          // бас
            tone(out, at + beat / 2, beat / 2, r * 2f, r * 2f, 0.25f, 2.5f, square = true)
            val chord = floatArrayOf(r * 4f, r * 4f * thirds[b / 4], r * 6f, r * 8f)
            for (q in 0 until 4) tone(out, at + q * beat / 4, beat / 4, chord[q], chord[q], 0.09f, 3f)
        }
        return out
    }

    private fun writeWav(f: File, data: FloatArray) {
        val peak = maxOf(0.001f, data.maxOf { abs(it) })
        val g = if (peak > 0.95f) 0.95f / peak else 1f
        val bb = ByteBuffer.allocate(44 + data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + data.size * 2).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(data.size * 2)
        for (v in data) bb.putShort(((v * g).coerceIn(-1f, 1f) * 32767).toInt().toShort())
        FileOutputStream(f).use { it.write(bb.array()) }
    }
}
