package com.flipway.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class MeshId { CUBE, CYLINDER, SPHERE, CRYSTAL, TUNNEL_FLOOR, TUNNEL_WALLS }

/**
 * 3D-геометрия игры: списки треугольников, на вершину 6 чисел — позиция и нормаль.
 * Все примитивы единичного размера с центром в нуле; нужный размер и положение
 * задаёт матрица модели. Туннель строится сразу в мировых координатах.
 */
object Meshes {
    const val STRIDE = 6

    fun build(id: MeshId): FloatArray = when (id) {
        MeshId.CUBE -> cube()
        MeshId.CYLINDER -> cylinder(20)
        MeshId.SPHERE -> sphere(10, 16)
        MeshId.CRYSTAL -> crystal()
        MeshId.TUNNEL_FLOOR -> tunnelFloor()
        MeshId.TUNNEL_WALLS -> tunnelWalls()
    }

    private class Builder {
        var buf = FloatArray(4096)
        var size = 0
        fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
            if (size + 6 > buf.size) buf = buf.copyOf(buf.size * 2)
            buf[size++] = x; buf[size++] = y; buf[size++] = z
            buf[size++] = nx; buf[size++] = ny; buf[size++] = nz
        }
        /** Плоский четырёхугольник p0..p3 с общей нормалью. */
        fun quad(p: FloatArray, nx: Float, ny: Float, nz: Float) {
            for (i in intArrayOf(0, 1, 2, 0, 2, 3)) v(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], nx, ny, nz)
        }
        fun result() = buf.copyOf(size)
    }

    private fun cube(): FloatArray {
        val b = Builder()
        val h = 0.5f
        b.quad(floatArrayOf(h, -h, -h, h, h, -h, h, h, h, h, -h, h), 1f, 0f, 0f)
        b.quad(floatArrayOf(-h, -h, h, -h, h, h, -h, h, -h, -h, -h, -h), -1f, 0f, 0f)
        b.quad(floatArrayOf(-h, h, -h, -h, h, h, h, h, h, h, h, -h), 0f, 1f, 0f)
        b.quad(floatArrayOf(-h, -h, h, -h, -h, -h, h, -h, -h, h, -h, h), 0f, -1f, 0f)
        b.quad(floatArrayOf(-h, -h, h, h, -h, h, h, h, h, -h, h, h), 0f, 0f, 1f)
        b.quad(floatArrayOf(h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h), 0f, 0f, -1f)
        return b.result()
    }

    /** Цилиндр вдоль оси Y: радиус 0.5, высота 1, гладкие бока. */
    private fun cylinder(seg: Int): FloatArray {
        val b = Builder()
        for (i in 0 until seg) {
            val a0 = (2 * PI * i / seg).toFloat()
            val a1 = (2 * PI * (i + 1) / seg).toFloat()
            val c0 = cos(a0); val s0 = sin(a0); val c1 = cos(a1); val s1 = sin(a1)
            val x0 = c0 * 0.5f; val z0 = s0 * 0.5f; val x1 = c1 * 0.5f; val z1 = s1 * 0.5f
            b.v(x0, -0.5f, z0, c0, 0f, s0); b.v(x1, -0.5f, z1, c1, 0f, s1); b.v(x1, 0.5f, z1, c1, 0f, s1)
            b.v(x0, -0.5f, z0, c0, 0f, s0); b.v(x1, 0.5f, z1, c1, 0f, s1); b.v(x0, 0.5f, z0, c0, 0f, s0)
            b.v(0f, 0.5f, 0f, 0f, 1f, 0f); b.v(x0, 0.5f, z0, 0f, 1f, 0f); b.v(x1, 0.5f, z1, 0f, 1f, 0f)
            b.v(0f, -0.5f, 0f, 0f, -1f, 0f); b.v(x1, -0.5f, z1, 0f, -1f, 0f); b.v(x0, -0.5f, z0, 0f, -1f, 0f)
        }
        return b.result()
    }

    /** Сфера радиуса 0.5. */
    private fun sphere(lat: Int, lon: Int): FloatArray {
        val b = Builder()
        fun p(i: Int, j: Int, out: FloatArray, o: Int) {
            val t = (PI * i / lat).toFloat()
            val f = (2 * PI * j / lon).toFloat()
            out[o] = sin(t) * cos(f); out[o + 1] = cos(t); out[o + 2] = sin(t) * sin(f)
        }
        val q = FloatArray(12)
        for (i in 0 until lat) for (j in 0 until lon) {
            p(i, j, q, 0); p(i + 1, j, q, 3); p(i + 1, j + 1, q, 6); p(i, j + 1, q, 9)
            for (k in intArrayOf(0, 1, 2, 0, 2, 3)) {
                val x = q[k * 3]; val y = q[k * 3 + 1]; val z = q[k * 3 + 2]
                b.v(x * 0.5f, y * 0.5f, z * 0.5f, x, y, z)
            }
        }
        return b.result()
    }

    /** Кристалл-бонус: вытянутый октаэдр с плоскими гранями. */
    private fun crystal(): FloatArray {
        val b = Builder()
        val top = floatArrayOf(0f, 0.7f, 0f)
        val bot = floatArrayOf(0f, -0.7f, 0f)
        val ring = arrayOf(floatArrayOf(0.5f, 0f, 0f), floatArrayOf(0f, 0f, 0.5f), floatArrayOf(-0.5f, 0f, 0f), floatArrayOf(0f, 0f, -0.5f))
        for (i in 0 until 4) {
            val a = ring[i]; val c = ring[(i + 1) % 4]
            face(b, top, a, c)
            face(b, bot, c, a)
        }
        return b.result()
    }

    private fun face(b: Builder, p0: FloatArray, p1: FloatArray, p2: FloatArray) {
        val ux = p1[0] - p0[0]; val uy = p1[1] - p0[1]; val uz = p1[2] - p0[2]
        val vx = p2[0] - p0[0]; val vy = p2[1] - p0[1]; val vz = p2[2] - p0[2]
        var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
        val l = sqrt(nx * nx + ny * ny + nz * nz)
        // нормаль наружу: от центра к грани
        val cx = p0[0] + p1[0] + p2[0]; val cy = p0[1] + p1[1] + p2[1]; val cz = p0[2] + p1[2] + p2[2]
        val sgn = if (nx * cx + ny * cy + nz * cz < 0f) -1f else 1f
        nx *= sgn / l; ny *= sgn / l; nz *= sgn / l
        for (p in arrayOf(p0, p1, p2)) b.v(p[0], p[1], p[2], nx, ny, nz)
    }

    private const val Z0 = -Config.CAM_DIST + 0.1f
    private const val Z1 = Config.DRAW_Z + 8f

    /** Пол и потолок туннеля (узор рисует шейдер). */
    private fun tunnelFloor(): FloatArray {
        val b = Builder()
        val w = Config.TUNNEL_HALF_W
        val h = Config.TUNNEL_H
        b.quad(floatArrayOf(-w, 0f, Z0, w, 0f, Z0, w, 0f, Z1, -w, 0f, Z1), 0f, 1f, 0f)
        b.quad(floatArrayOf(-w, h, Z0, -w, h, Z1, w, h, Z1, w, h, Z0), 0f, -1f, 0f)
        return b.result()
    }

    /** Боковые стены туннеля. */
    private fun tunnelWalls(): FloatArray {
        val b = Builder()
        val w = Config.TUNNEL_HALF_W
        val h = Config.TUNNEL_H
        b.quad(floatArrayOf(-w, 0f, Z0, -w, 0f, Z1, -w, h, Z1, -w, h, Z0), 1f, 0f, 0f)
        b.quad(floatArrayOf(w, 0f, Z0, w, h, Z0, w, h, Z1, w, 0f, Z1), -1f, 0f, 0f)
        return b.result()
    }
}
