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
 * Шейдер «неонової скляної панелі» (SDF-прямокутник із заокругленням):
 * плавні антиаліасні краї, вертикальний градієнт скла, глянцевий відблиск,
 * неоновий обідок із градієнтом кольору і зовнішнім світінням, мерехтливі
 * зірочки всередині та світлова смуга, що періодично пробігає по кнопці.
 * Ним малюються всі кнопки, картки та панелі підказок.
 */
object UiShader {

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
uniform vec2  u_size;      // розмір квада в px (разом із полем під світіння)
uniform float u_pad;       // поле під світіння, px
uniform float u_radius;    // радіус кутів, px
uniform float u_time;
uniform float u_glow;      // сила зовнішнього світіння 0..1
uniform float u_press;     // 0..1 — натиснута
uniform float u_sheen;     // 1 — світлова смуга ввімкнена
uniform vec4  u_top;       // колір скла зверху
uniform vec4  u_bottom;    // колір скла знизу
uniform vec4  u_rimA;      // неон зліва
uniform vec4  u_rimB;      // неон справа

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

void main() {
    vec2 px = v_texCoords * u_size;               // піксельні координати квада (y вниз)
    vec2 inner = u_size - 2.0 * u_pad;            // розмір самої панелі
    vec2 p = px - u_pad - inner * 0.5;            // центр панелі = 0
    vec2 q = abs(p) - (inner * 0.5 - u_radius);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - u_radius;   // SDF, px

    float t = 1.0 - clamp((px.y - u_pad) / inner.y, 0.0, 1.0);          // 0 низ .. 1 верх
    float u = clamp((px.x - u_pad) / inner.x, 0.0, 1.0);

    // Скло: градієнт + легке затемнення по краях
    vec3 glass = mix(u_bottom.rgb, u_top.rgb, t);
    glass *= 0.85 + 0.15 * (1.0 - smoothstep(-40.0, 0.0, d));
    glass += vec3(0.10) * u_press;

    // Зірочки всередині
    vec2 cell = floor(px / 14.0);
    float h = hash(cell);
    float tw = 0.5 + 0.5 * sin(u_time * (1.5 + h * 3.0) + h * 40.0);
    float star = step(0.93, h) * tw;
    vec2 cp = fract(px / 14.0) - 0.5;
    star *= 1.0 - smoothstep(0.0, 0.18, length(cp) - 0.02);
    glass += vec3(star * 0.9);

    // Глянець: відблиск у верхній частині
    float gloss = smoothstep(0.55, 0.95, t) * 0.22 + smoothstep(0.86, 0.94, t) * 0.20;
    float shine = exp(-pow((u - 0.18) * 3.0, 2.0)) * smoothstep(0.5, 1.0, t) * 0.18;
    glass += vec3(gloss + shine);

    // Світлова смуга, що пробігає
    float sweep = fract(u_time * 0.28);
    float band = exp(-pow((u + (1.0 - t) * 0.35 - sweep * 1.8 + 0.2) * 9.0, 2.0)) * 0.35 * u_sheen;
    glass += vec3(band);

    // Неоновий обідок
    vec3 rim = mix(u_rimA.rgb, u_rimB.rgb, u + 0.15 * sin(u_time * 1.3 + t * 3.0));
    float rimLine = 1.0 - smoothstep(0.0, 3.5, abs(d + 2.5));
    float innerGlow = (1.0 - smoothstep(-22.0, 0.0, d)) * 0.35;
    float pulse = 0.85 + 0.15 * sin(u_time * 2.2);

    float bodyA = 1.0 - smoothstep(-1.0, 1.0, d);
    vec3 col = glass * bodyA;
    col += rim * innerGlow * bodyA * pulse;
    col += rim * rimLine * (1.2 + 0.6 * u_press);

    // Зовнішнє світіння (адитивно — за межами панелі)
    float outer = exp(-max(d, 0.0) / (10.0 + 14.0 * u_press)) * (1.0 - bodyA);
    col += rim * outer * (0.55 * u_glow + 0.6 * u_press) * pulse;

    float a = max(bodyA * (0.92 + 0.08 * u_press), outer * (0.55 * u_glow + 0.6 * u_press));
    gl_FragColor = vec4(col, a) * v_color;
}
"""

    val program: ShaderProgram? by lazy {
        ShaderProgram.pedantic = false
        val shader = ShaderProgram(VERT, FRAG)
        if (shader.isCompiled) shader else {
            log("UiShader compile failed: ${shader.log}")
            null
        }
    }
}

/**
 * Панель, намальована [UiShader]. Розмір актора — сама панель; квад малюється
 * ширшим на [pad] з кожного боку, щоб вмістити світіння.
 */
open class NeonPanel(
    private val region: TextureRegion,
    val top: Color    = Color.valueOf("2A1B5E"),
    val bottom: Color = Color.valueOf("120A2E"),
    val rimA: Color   = Color.valueOf("FF5CC8"),
    val rimB: Color   = Color.valueOf("5C8CFF"),
) : Actor() {

    var radius = -1f          // −1 = пігулка (половина висоти)
    var glow   = 1f
    var press  = 0f
    var sheen  = 1f
    var pad    = 34f
    var time   = MathUtils.random(0f, 20f)

    override fun act(delta: Float) {
        super.act(delta)
        time += delta
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        val shader = UiShader.program ?: return
        val c = color
        val w = width * scaleX
        val h = height * scaleY
        val x0 = x + (width - w) / 2f - pad
        val y0 = y + (height - h) / 2f - pad

        batch.setColor(c.r, c.g, c.b, c.a * parentAlpha)
        batch.shader = shader
        shader.setUniformf("u_size", w + 2f * pad, h + 2f * pad)
        shader.setUniformf("u_pad", pad)
        shader.setUniformf("u_radius", if (radius < 0f) h / 2f else radius * scaleY)
        shader.setUniformf("u_time", time)
        shader.setUniformf("u_glow", glow)
        shader.setUniformf("u_press", press)
        shader.setUniformf("u_sheen", sheen)
        shader.setUniformf("u_top", top)
        shader.setUniformf("u_bottom", bottom)
        shader.setUniformf("u_rimA", rimA)
        shader.setUniformf("u_rimB", rimB)
        batch.draw(region, x0, y0, w + 2f * pad, h + 2f * pad)
        batch.shader = null
        batch.setColor(Color.WHITE)
    }
}
