package com.flipway.game

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random

/**
 * Рисует 3D-сцену через OpenGL ES 2.0. Каждый кадр: шаг логики и сборка сцены
 * под замком Director, затем отрисовка — сначала непрозрачное, потом свечения
 * и полупрозрачное (без записи глубины, от дальних к ближним).
 * Если шейдер не собрался — onFail, и игра переключается на 2D-вид.
 */
class GlRenderer(
    private val ctx: Context,
    private val director: Director,
    private val onFail: (String) -> Unit,
) : GLSurfaceView.Renderer {
    private val scene = Scene3D()
    private var width = 1
    private var height = 1
    private var program = 0
    private var failed = false
    private val vbo = IntArray(MeshId.values().size)
    private val counts = IntArray(MeshId.values().size)
    private var last = 0L
    private val mvp = FloatArray(16)
    private val nm = FloatArray(9)
    private val shaken = FloatArray(16)
    private val shakeRnd = Random(3)

    private var aPos = 0; private var aNormal = 0
    private var uMVP = 0; private var uModel = 0; private var uNormalMat = 0
    private var uColor = 0; private var uEmissive = 0; private var uMat = 0; private var uAdditive = 0
    private var uCamPos = 0; private var uLight = 0; private var uFogFar = 0
    private var uScroll = 0; private var uPlayerPos = 0; private var uPlayerLight = 0
    // локации: цвета текущей/следующей + позиция границы (см. Biomes.kt, Scene3D.biomeUniforms)
    private var uBioFloorA = 0; private var uBioWallA = 0; private var uBioEdgeA = 0; private var uBioRingA = 0; private var uBioFogA = 0
    private var uBioFloorB = 0; private var uBioWallB = 0; private var uBioEdgeB = 0; private var uBioRingB = 0; private var uBioFogB = 0
    private var uBrightA = 0; private var uBrightB = 0; private var uGlowA = 0; private var uGlowB = 0; private var uTexA = 0; private var uTexB = 0
    private var uBoundaryZ = 0; private var uBioTrans = 0
    // настоящие фото-текстуры Сакуры, испечённые в Blender (см. assets/textures)
    private var uTexGround = 0; private var uTexBark = 0; private var uTexCanopy = 0
    private var texGround = 0; private var texBark = 0; private var texCanopy = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        failed = false
        program = buildProgram()
        if (program == 0) { failed = true; return }
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        fun u(name: String) = GLES20.glGetUniformLocation(program, name)
        uMVP = u("uMVP"); uModel = u("uModel"); uNormalMat = u("uNormalMat")
        uColor = u("uColor"); uEmissive = u("uEmissive"); uMat = u("uMat"); uAdditive = u("uAdditive")
        uCamPos = u("uCamPos"); uLight = u("uLight"); uFogFar = u("uFogFar")
        uScroll = u("uScroll"); uPlayerPos = u("uPlayerPos"); uPlayerLight = u("uPlayerLight")
        uBioFloorA = u("uBioFloorA"); uBioWallA = u("uBioWallA"); uBioEdgeA = u("uBioEdgeA"); uBioRingA = u("uBioRingA"); uBioFogA = u("uBioFogA")
        uBioFloorB = u("uBioFloorB"); uBioWallB = u("uBioWallB"); uBioEdgeB = u("uBioEdgeB"); uBioRingB = u("uBioRingB"); uBioFogB = u("uBioFogB")
        uBrightA = u("uBrightA"); uBrightB = u("uBrightB"); uGlowA = u("uGlowA"); uGlowB = u("uGlowB")
        uTexA = u("uTexA"); uTexB = u("uTexB")
        uBoundaryZ = u("uBoundaryZ"); uBioTrans = u("uBioTrans")
        uTexGround = u("uTexGround"); uTexBark = u("uTexBark"); uTexCanopy = u("uTexCanopy")
        texGround = loadTexture("textures/ground_flowers.jpg")
        texBark = loadTexture("textures/bark.jpg")
        texCanopy = loadTexture("textures/canopy.jpg")

        // геометрия — один раз в видеопамять
        GLES20.glGenBuffers(vbo.size, vbo, 0)
        for (id in MeshId.values()) {
            val data = Meshes.build(id)
            val buf = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            buf.put(data).position(0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[id.ordinal])
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * 4, buf, GLES20.GL_STATIC_DRAW)
            counts[id.ordinal] = data.size / Meshes.STRIDE
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        val fog = Palette.FOG
        GLES20.glClearColor(ch(fog, 16), ch(fog, 8), ch(fog, 0), 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        last = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w; height = h
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - last) / 1e9f).coerceIn(0f, 1f / 20f)
        last = now
        if (failed) { GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT); return }

        val shake: Float
        synchronized(director) {
            director.step(dt)
            scene.skin = director.skin
            scene.build(director.game, width, height, director.clock)
            shake = director.game.shake
        }
        // фон за геометрией красим туманом текущей локации, чтобы граница не «протекала» серым
        GLES20.glClearColor(scene.bioFogA[0], scene.bioFogA[1], scene.bioFogA[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        // тряска камеры: сдвиг картинки в экранных координатах
        scene.viewProj.copyInto(shaken)
        if (shake > 0f) {
            val dx = (shakeRnd.nextFloat() - 0.5f) * shake * 0.04f
            val dy = (shakeRnd.nextFloat() - 0.5f) * shake * 0.04f
            for (c in 0 until 4) {
                shaken[c * 4] += dx * shaken[c * 4 + 3]
                shaken[c * 4 + 1] += dy * shaken[c * 4 + 3]
            }
        }

        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glUniform3f(uCamPos, scene.camPos[0], scene.camPos[1], scene.camPos[2])
        GLES20.glUniform3f(uLight, scene.light[0], scene.light[1], scene.light[2])
        GLES20.glUniform1f(uFogFar, Config.DRAW_Z + Config.CAM_DIST)
        GLES20.glUniform1f(uScroll, scene.scroll)
        GLES20.glUniform3f(uPlayerPos, scene.playerPos[0], scene.playerPos[1], scene.playerPos[2])
        color3(uPlayerLight, scene.playerLight)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texGround); GLES20.glUniform1i(uTexGround, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texBark); GLES20.glUniform1i(uTexBark, 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texCanopy); GLES20.glUniform1i(uTexCanopy, 2)
        v3(uBioFloorA, scene.bioFloorA); v3(uBioWallA, scene.bioWallA); v3(uBioEdgeA, scene.bioEdgeA)
        v3(uBioRingA, scene.bioRingA); v3(uBioFogA, scene.bioFogA)
        v3(uBioFloorB, scene.bioFloorB); v3(uBioWallB, scene.bioWallB); v3(uBioEdgeB, scene.bioEdgeB)
        v3(uBioRingB, scene.bioRingB); v3(uBioFogB, scene.bioFogB)
        GLES20.glUniform1f(uBrightA, scene.bioBrightA); GLES20.glUniform1f(uBrightB, scene.bioBrightB)
        GLES20.glUniform1f(uGlowA, scene.bioGlowA); GLES20.glUniform1f(uGlowB, scene.bioGlowB)
        GLES20.glUniform1f(uTexA, scene.bioTexA); GLES20.glUniform1f(uTexB, scene.bioTexB)
        GLES20.glUniform1f(uBoundaryZ, scene.boundaryZ)
        GLES20.glUniform1f(uBioTrans, scene.bioTrans)

        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        var bound = -1
        for (d in scene.opaque) bound = draw(d, bound)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glDepthMask(false)
        for (d in scene.transparent) {
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, if (d.additive) GLES20.GL_ONE else GLES20.GL_ONE_MINUS_SRC_ALPHA)
            bound = draw(d, bound)
        }
        GLES20.glDepthMask(true)
    }

    private fun draw(d: Draw, bound: Int): Int {
        val id = d.mesh.ordinal
        if (id != bound) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[id])
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, Meshes.STRIDE * 4, 0)
            GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, Meshes.STRIDE * 4, 12)
        }
        Mat4.mul(mvp, shaken, d.model)
        Mat4.normalMatrix(nm, d.model)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, d.model, 0)
        GLES20.glUniformMatrix3fv(uNormalMat, 1, false, nm, 0)
        color3(uColor, d.color)
        color3(uEmissive, d.emissive)
        GLES20.glUniform4f(uMat, d.pattern.toFloat(), d.alpha, d.rim, d.spec)
        GLES20.glUniform1f(uAdditive, if (d.additive) 1f else 0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, counts[id])
        return id
    }

    /** Грузит jpg из assets/, заливает в GL с повтором по краям и мип-уровнями. */
    private fun loadTexture(assetPath: String): Int {
        val id = IntArray(1)
        try {
            ctx.assets.open(assetPath).use { stream ->
                val bmp = BitmapFactory.decodeStream(stream) ?: return 0
                GLES20.glGenTextures(1, id, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id[0])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
                bmp.recycle()
            }
        } catch (e: Exception) {
            Log.e("Flipway", "texture $assetPath: $e")
        }
        return id[0]
    }

    private fun ch(c: Int, shift: Int) = ((c shr shift) and 0xFF) / 255f
    private fun color3(loc: Int, c: Int) = GLES20.glUniform3f(loc, ch(c, 16), ch(c, 8), ch(c, 0))
    private fun v3(loc: Int, a: FloatArray) = GLES20.glUniform3f(loc, a[0], a[1], a[2])

    private fun buildProgram(): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, Shaders.VERTEX) ?: return 0
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, Shaders.FRAGMENT) ?: return 0
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) { fail("link: " + GLES20.glGetProgramInfoLog(p)); return 0 }
        return p
    }

    private fun compile(type: Int, src: String): Int? {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { fail("compile: " + GLES20.glGetShaderInfoLog(s)); return null }
        return s
    }

    private fun fail(msg: String) {
        Log.e("Flipway", "OpenGL: $msg")
        if (!failed) { failed = true; onFail(msg) }
    }
}
