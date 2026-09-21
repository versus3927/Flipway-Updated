package com.flipway.game

import kotlin.math.cos
import kotlin.math.sin

/**
 * Матрицы 4×4 в формате OpenGL (по столбцам: элемент [строка r, столбец c] = m[c*4 + r]).
 * Своя реализация вместо android.opengl.Matrix — чтобы 3D-сцену можно было
 * собрать и проверить обычными JVM-тестами и превью без телефона.
 * Все операции «справа»: m = m × T, как в классическом glTranslate/glRotate.
 */
object Mat4 {
    fun identity(m: FloatArray) {
        m.fill(0f)
        m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
    }

    /** out = a × b; out может совпадать с a или b. Вызывать из одного потока (поток рендера). */
    fun mul(out: FloatArray, a: FloatArray, b: FloatArray) {
        val t = tmp
        for (c in 0 until 4) for (r in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += a[k * 4 + r] * b[c * 4 + k]
            t[c * 4 + r] = s
        }
        t.copyInto(out)
    }

    fun translate(m: FloatArray, x: Float, y: Float, z: Float) {
        for (r in 0 until 4) m[12 + r] += m[r] * x + m[4 + r] * y + m[8 + r] * z
    }

    fun scale(m: FloatArray, x: Float, y: Float, z: Float) {
        for (r in 0 until 4) { m[r] *= x; m[4 + r] *= y; m[8 + r] *= z }
    }

    /** Поворот вокруг X (градусы): положительный угол уводит ось -Y назад, к -Z. */
    fun rotateX(m: FloatArray, deg: Float) = rot(m, deg, 4, 8)
    fun rotateY(m: FloatArray, deg: Float) = rot(m, -deg, 0, 8)
    fun rotateZ(m: FloatArray, deg: Float) = rot(m, deg, 0, 4)

    /** Общий поворот в плоскости двух столбцов a, b. */
    private fun rot(m: FloatArray, deg: Float, a: Int, b: Int) {
        val rad = Math.toRadians(deg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        for (r in 0 until 4) {
            val x = m[a + r]
            val y = m[b + r]
            m[a + r] = x * c + y * s
            m[b + r] = -x * s + y * c
        }
    }

    /** Как glFrustum: несимметричная пирамида видимости. */
    fun frustum(m: FloatArray, l: Float, r: Float, b: Float, t: Float, n: Float, f: Float) {
        m.fill(0f)
        m[0] = 2f * n / (r - l)
        m[5] = 2f * n / (t - b)
        m[8] = (r + l) / (r - l)
        m[9] = (t + b) / (t - b)
        m[10] = -(f + n) / (f - n)
        m[11] = -1f
        m[14] = -2f * f * n / (f - n)
    }

    /** Матрица нормалей 3×3 (обратная транспонированная к левому верхнему блоку). */
    fun normalMatrix(out: FloatArray, m: FloatArray) {
        val a00 = m[0]; val a10 = m[1]; val a20 = m[2]
        val a01 = m[4]; val a11 = m[5]; val a21 = m[6]
        val a02 = m[8]; val a12 = m[9]; val a22 = m[10]
        val c00 = a11 * a22 - a12 * a21
        val c01 = -(a10 * a22 - a12 * a20)
        val c02 = a10 * a21 - a11 * a20
        val c10 = -(a01 * a22 - a02 * a21)
        val c11 = a00 * a22 - a02 * a20
        val c12 = -(a00 * a21 - a01 * a20)
        val c20 = a01 * a12 - a02 * a11
        val c21 = -(a00 * a12 - a02 * a10)
        val c22 = a00 * a11 - a01 * a10
        val det = a00 * c00 + a01 * c01 + a02 * c02
        val k = if (det == 0f) 1f else 1f / det
        // по столбцам: out[c*3 + r] = C[r][c] / det
        out[0] = c00 * k; out[1] = c10 * k; out[2] = c20 * k
        out[3] = c01 * k; out[4] = c11 * k; out[5] = c21 * k
        out[6] = c02 * k; out[7] = c12 * k; out[8] = c22 * k
    }

    private val tmp = FloatArray(16)
}
