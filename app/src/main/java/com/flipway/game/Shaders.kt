package com.flipway.game

/**
 * Шейдеры OpenGL ES 2.0 (GLSL ES 1.00). Фрагментный шейдер продублирован на Kotlin
 * в превью-стенде (Shade.kt) — при правке меняйте оба одинаково.
 */
object Shaders {
    const val VERTEX = """
uniform mat4 uMVP;
uniform mat4 uModel;
uniform mat3 uNormalMat;
attribute vec3 aPos;
attribute vec3 aNormal;
varying vec3 vWorld;
varying vec3 vNormal;
void main() {
    vWorld = (uModel * vec4(aPos, 1.0)).xyz;
    vNormal = uNormalMat * aNormal;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

    const val FRAGMENT = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec3 uColor;
uniform vec3 uEmissive;
uniform vec4 uMat;
uniform float uAdditive;
uniform vec3 uCamPos;
uniform vec3 uLight;
uniform float uFogFar;
uniform float uScroll;
uniform vec3 uPlayerPos;
uniform vec3 uPlayerLight;
// цвета текущей (A) и следующей (B) локации — граница на глубине uBoundaryZ,
// шириной uBioTrans; каждый фрагмент туннеля красится по своей мировой глубине,
// поэтому смена локации видна издалека и проявляется плавно, без рывка.
uniform vec3 uBioFloorA, uBioWallA, uBioEdgeA, uBioRingA, uBioFogA;
uniform vec3 uBioFloorB, uBioWallB, uBioEdgeB, uBioRingB, uBioFogB;
// bright — дневная подсветка теней, glow — сила дымки у горизонта,
// tex — узор поверхности (0 техно-панели, 1 цветущие пятна Сакуры)
uniform float uBrightA, uBrightB, uGlowA, uGlowB, uTexA, uTexB;
uniform float uBoundaryZ;
uniform float uBioTrans;
// настоящие фотографии-текстуры Сакуры, испечённые в Blender (Shade.kt держит
// такой же семплер на Kotlin для превью)
uniform sampler2D uTexGround;
uniform sampler2D uTexBark;
uniform sampler2D uTexCanopy;
varying vec3 vWorld;
varying vec3 vNormal;

const vec3 RIM = vec3(0.55, 0.4, 1.0);
const float HW = 1.75;
const float TH = 5.6;

float band(float d, float w) { return 1.0 - smoothstep(w * 0.5, w, d); }

void main() {
    vec3 n = normalize(vNormal);
    vec3 toCam = uCamPos - vWorld;
    float dist = max(length(toCam), 0.001);
    vec3 v = toCam / dist;
    vec3 base = uColor;
    vec3 emit = uEmissive;
    float lw = 0.04 + dist * 0.004;
    float pat = uMat.x;
    float bblend = clamp((vWorld.z - (uBoundaryZ - uBioTrans)) / uBioTrans, 0.0, 1.0);
    vec3 bioFog = mix(uBioFogA, uBioFogB, bblend);
    float bright = mix(uBrightA, uBrightB, bblend);
    float glow = mix(uGlowA, uGlowB, bblend);
    float texId = bblend < 0.5 ? uTexA : uTexB;
    if (pat > 0.5) {
        vec3 edgeCol = mix(uBioEdgeA, uBioEdgeB, bblend);
        vec3 ringCol = mix(uBioRingA, uBioRingB, bblend);
        base = mix(pat < 1.5 ? uBioFloorA : uBioWallA, pat < 1.5 ? uBioFloorB : uBioWallB, bblend);
        float zz = vWorld.z + uScroll;
        float ring = abs(fract(zz / 12.0 + 0.5) - 0.5) * 12.0;
        vec3 rc = mix(edgeCol, ringCol, step(0.5, mod(floor(zz / 12.0 + 0.5), 2.0)));
        emit += rc * band(ring, lw * 1.6) * 1.2;
        if (texId < 0.5) {
            if (pat < 1.5) {
                float tile = fract(zz / 4.0);
                base *= 0.8 + 0.35 * step(0.5, fract(zz / 8.0));
                float seam = abs(fract(zz / 4.0 + 0.5) - 0.5) * 4.0;
                emit += ringCol * 0.45 * band(seam, lw);
                float dash = band(abs(abs(vWorld.x) - 0.5), lw * 0.6 + 0.03) * step(tile, 0.5) * 0.5;
                emit += edgeCol * 0.9 * dash;
                emit += edgeCol * band(HW - abs(vWorld.x), 0.1 + lw) * 1.1;
            } else {
                emit += edgeCol * (band(vWorld.y, 0.12 + lw) + band(TH - vWorld.y, 0.12 + lw));
                float panel = abs(fract(zz / 3.0 + 0.5) - 0.5) * 3.0;
                base *= 1.0 - 0.35 * band(panel, 0.08 + lw);
            }
        } else if (pat < 1.5) {
            // цветущая тропа: настоящая фотография травы с цветами (пол) или кроны снизу (потолок)
            vec2 uv = vec2(vWorld.x * 0.6, zz * 0.6);
            vec3 texCol = vWorld.y > TH * 0.5 ? texture2D(uTexCanopy, uv).rgb : texture2D(uTexGround, uv).rgb;
            base = mix(base, texCol, 0.88);
            // светящаяся дорожка по границам полос — видно, где бежать, даже среди цветов
            float lane = band(abs(abs(vWorld.x) - 0.5), 0.06 + lw) * (0.5 + 0.5 * step(0.5, fract(zz / 2.0)));
            emit += edgeCol * lane * 0.6;
        } else {
            // стены: настоящая фотография коры дерева
            vec2 uv = vec2(vWorld.y * 0.55, zz * 0.55);
            vec3 texCol = texture2D(uTexBark, uv).rgb;
            base = mix(base, texCol, 0.85);
        }
    }
    float diff = max(dot(n, uLight), 0.0);
    vec3 hv = normalize(uLight + v);
    float spec = pow(max(dot(n, hv), 0.0), 32.0) * uMat.w;
    float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0) * uMat.z;
    float plk = max(0.0, 1.0 - length(vWorld - uPlayerPos) / 3.0);
    plk = plk * plk * step(0.5, pat);
    float haze = pow(max(-v.z, 0.0), 40.0) * smoothstep(25.0, 90.0, dist) * 0.35 * (1.0 - uAdditive);
    vec3 col = base * (bright + 0.75 * diff) + base * uPlayerLight * plk * 0.9 + uPlayerLight * plk * 0.12
        + vec3(spec) + RIM * rim + emit + mix(uBioEdgeA, uBioEdgeB, bblend) * haze * 0.6 * glow;
    float f = clamp(dist / uFogFar, 0.0, 1.0);
    col = mix(col, bioFog * (1.0 - uAdditive), f * f);
    gl_FragColor = vec4(col, uMat.y);
}
"""
}
