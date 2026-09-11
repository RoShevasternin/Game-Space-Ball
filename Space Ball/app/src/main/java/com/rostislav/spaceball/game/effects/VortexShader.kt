package com.rostislav.spaceball.game.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.rostislav.spaceball.util.log

/**
 * Один фрагментний шейдер на три ефекти: портал, чорна діра, варп-ворота.
 * Малюється адитивно поверх сцени, тож «світиться» без окремих текстур.
 */
object VortexShader {

    const val MODE_PORTAL = 0
    const val MODE_HOLE   = 1
    const val MODE_GATE   = 2

    private const val VERT = """
attribute vec4 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;
uniform mat4 u_projTrans;
varying vec4 v_color;
varying vec2 v_texCoords;
void main() {
    v_color = a_color;
    v_color.a = v_color.a * (255.0 / 254.0);
    v_texCoords = a_texCoord0;
    gl_Position = u_projTrans * a_position;
}
"""

    private const val FRAG = """
#ifdef GL_ES
precision mediump float;
#endif
varying vec4 v_color;
varying vec2 v_texCoords;
uniform sampler2D u_texture;
uniform float u_time;
uniform float u_mode;
uniform vec4 u_colorA;
uniform vec4 u_colorB;

void main() {
    vec2 p = v_texCoords * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) { gl_FragColor = vec4(0.0); return; }
    float ang = atan(p.y, p.x);
    vec3 c = vec3(0.0);
    float a = 0.0;

    if (u_mode < 0.5) {
        // Портал: кільце зі спіральними рукавами і мерехтливе ядро
        float sw   = 0.5 + 0.5 * sin(3.0 * ang + 14.0 * r - u_time * 5.0);
        float ring = smoothstep(0.42, 0.58, r) * (1.0 - smoothstep(0.82, 1.0, r));
        float core = (1.0 - smoothstep(0.0, 0.5, r)) * (0.30 + 0.20 * sin(u_time * 3.0 + r * 12.0));
        c = mix(u_colorA.rgb, u_colorB.rgb, sw);
        a = ring * (0.55 + 0.45 * sw) + core;
    } else if (u_mode < 1.5) {
        // Чорна діра: акреційний диск, що закручується до центру
        float sw   = 0.5 + 0.5 * sin(2.0 * ang - 11.0 * r + u_time * 4.5);
        float disk = smoothstep(0.16, 0.28, r) * (1.0 - smoothstep(0.50, 1.0, r));
        c = mix(u_colorA.rgb, u_colorB.rgb, sw);
        a = disk * (0.25 + 0.75 * sw) * (1.0 - 0.6 * r);
    } else {
        // Ворота: пульсуюче кільце з рухомими іскрами і м'яким сяйвом усередині
        float pulse = 0.5 + 0.5 * sin(u_time * 4.0);
        float ring  = smoothstep(0.50, 0.60, r) * (1.0 - smoothstep(0.70, 0.90, r));
        float sw    = 0.5 + 0.5 * sin(6.0 * ang - u_time * 6.0);
        float inner = (1.0 - smoothstep(0.0, 0.6, r)) * (0.20 + 0.20 * pulse) * (0.6 + 0.4 * sin(9.0 * r - u_time * 5.0));
        c = mix(u_colorA.rgb, u_colorB.rgb, sw);
        a = ring * (0.65 + 0.35 * sw) + inner;
    }
    gl_FragColor = vec4(c, a) * v_color;
}
"""

    /** null, якщо шейдер не скомпілювався — тоді ефекти просто не малюються. */
    val program: ShaderProgram? by lazy {
        ShaderProgram.pedantic = false
        val shader = ShaderProgram(VERT, FRAG)
        if (shader.isCompiled) shader else {
            log("VortexShader compile failed: ${shader.log}")
            null
        }
    }
}

/**
 * Квадрат, розмальований шейдером [VortexShader]. Додається дитиною у групу тіла
 * (портал, діра, ворота) — координати локальні для батьківської групи.
 */
class VortexActor(
    private val region: TextureRegion,
    var mode: Int,
    val colorA: Color,
    val colorB: Color,
) : Actor() {

    var time  = MathUtils.random(0f, 100f)
    var speed = 1f

    override fun act(delta: Float) {
        super.act(delta)
        time += delta * speed
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        val shader = VortexShader.program ?: return
        val c = color

        batch.setColor(c.r, c.g, c.b, c.a * parentAlpha)
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        batch.shader = shader
        shader.setUniformf("u_time", time)
        shader.setUniformf("u_mode", mode.toFloat())
        shader.setUniformf("u_colorA", colorA)
        shader.setUniformf("u_colorB", colorB)
        batch.draw(region, x, y, width, height)
        batch.shader = null
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.setColor(Color.WHITE)
    }
}
