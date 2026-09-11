package com.rostislav.spaceball.game.actors.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.MathUtils
import com.rostislav.spaceball.game.effects.FxSystem
import com.rostislav.spaceball.game.effects.VortexActor
import com.rostislav.spaceball.game.effects.VortexShader
import com.rostislav.spaceball.game.utils.advanced.AdvancedGroup
import com.rostislav.spaceball.game.utils.advanced.AdvancedScreen

// Актори небезпек і спецоб'єктів. Усі малюються ShapeDrawer-ом і шейдером VortexShader,
// тож не потребують нових текстур; коли з'являться справжні картинки — замінити тут.

/** Лазер: два випромінювачі й промінь між ними, що вмикається/вимикається. */
class ALaser(
    override val screen: AdvancedScreen,
    private val accent: Color,
) : AdvancedGroup() {

    var isOn    = false
    /** Промінь ось-ось увімкнеться — випромінювачі блимають. */
    var warning = false

    private var time = 0f
    private val tmp  = Color()
    private val hot  = Color.valueOf("FF3B3B")

    override fun addActorsOnGroup() {}

    override fun act(delta: Float) { super.act(delta); time += delta }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        val a  = color.a * parentAlpha
        val d  = screen.drawerUtil.drawer
        val cy = y + height / 2f
        val x1 = x + 14f
        val x2 = x + width - 14f

        if (isOn) {
            val flicker = 0.85f + 0.15f * MathUtils.sin(time * 40f)
            screen.drawerUtil.additive {
                tmp.set(hot); tmp.a = 0.18f * a * flicker; line(x1, cy, x2, cy, tmp, 30f)
                tmp.a = 0.45f * a * flicker;               line(x1, cy, x2, cy, tmp, 12f)
            }
            tmp.set(1f, 0.92f, 0.85f, a); d.line(x1, cy, x2, cy, tmp, 4f)
        } else {
            tmp.set(hot); tmp.a = 0.14f * a
            var sx = x1
            while (sx < x2) { d.line(sx, cy, minOf(sx + 12f, x2), cy, tmp, 2f); sx += 22f }
        }

        // Випромінювачі
        val blink = if (warning) 0.5f + 0.5f * MathUtils.sin(time * 30f) else if (isOn) 1f else 0.35f
        for (ex in floatArrayOf(x1, x2)) {
            tmp.set(0.12f, 0.12f, 0.18f, a);  d.filledCircle(ex, cy, 13f, tmp)
            tmp.set(hot).lerp(Color.WHITE, 0.2f * blink); tmp.a = a * (0.45f + 0.55f * blink)
            d.filledCircle(ex, cy, 7f, tmp)
            screen.drawerUtil.additive {
                tmp.set(hot); tmp.a = 0.35f * a * blink
                filledCircle(ex, cy, 16f, tmp)
            }
        }
        d.setColor(Color.WHITE)
        super.draw(batch, parentAlpha)
    }
}

/** Астероїд: камінь із кратерами, обертається разом із тілом. */
class AAsteroid(override val screen: AdvancedScreen) : AdvancedGroup() {

    private val tmp = Color()
    private val rockColor = Color.valueOf("5A4A52")
    private val rim       = Color.valueOf("9C8794")
    private val crater    = Color.valueOf("3A2E36")

    // Кратери у відносних координатах (частка радіуса)
    private val craters = arrayOf(
        floatArrayOf(-0.35f,  0.30f, 0.26f),
        floatArrayOf( 0.40f,  0.10f, 0.18f),
        floatArrayOf( 0.05f, -0.45f, 0.22f),
    )

    override fun addActorsOnGroup() {}

    override fun draw(batch: Batch?, parentAlpha: Float) {
        val a  = color.a * parentAlpha
        val d  = screen.drawerUtil.drawer
        val r  = width / 2f
        val cx = x + r
        val cy = y + r
        val rot = rotation * MathUtils.degreesToRadians
        val cos = MathUtils.cos(rot); val sin = MathUtils.sin(rot)

        screen.drawerUtil.additive {
            tmp.set(1f, 0.55f, 0.3f, 0.18f * a); filledCircle(cx, cy, r * 1.35f, tmp)
        }
        tmp.set(rim);       tmp.a = a; d.filledCircle(cx, cy, r, tmp)
        tmp.set(rockColor); tmp.a = a; d.filledCircle(cx + 2f * cos, cy + 2f * sin - 3f, r * 0.9f, tmp)
        for (c in craters) {
            val lx = c[0] * r; val ly = c[1] * r
            val wx = cx + lx * cos - ly * sin
            val wy = cy + lx * sin + ly * cos
            tmp.set(crater); tmp.a = a; d.filledCircle(wx, wy, c[2] * r, tmp)
            tmp.set(rim);    tmp.a = 0.5f * a; d.filledCircle(wx - c[2] * r * 0.25f, wy + c[2] * r * 0.25f, c[2] * r * 0.45f, tmp)
        }
        d.setColor(Color.WHITE)
        super.draw(batch, parentAlpha)
    }
}

/** Чорна діра: шейдерний акреційний диск, чорне ядро і частинки, що падають усередину. */
class ABlackHole(
    override val screen: AdvancedScreen,
    private val pullRadius: Float,
    private val fx: FxSystem,
    accent: Color,
) : AdvancedGroup() {

    private val vortex = VortexActor(screen.drawerUtil.whiteRegion, VortexShader.MODE_HOLE, Color(accent), Color.valueOf("FFFFFF"))
    private var time = 0f
    private var emit = 0f
    private val tmp  = Color()
    private val sparkColor = Color(accent)

    override fun addActorsOnGroup() {
        val vw = pullRadius * 2f
        vortex.setBounds(width / 2f - vw / 2f, height / 2f - vw / 2f, vw, vw)
        vortex.speed = 1.2f
        addActor(vortex)
    }

    override fun act(delta: Float) {
        super.act(delta)
        time += delta
        emit += delta
        if (emit > 0.06f) {
            emit = 0f
            val cx = x + width / 2f; val cy = y + height / 2f
            val ang = MathUtils.random(0f, MathUtils.PI2)
            val r = pullRadius * MathUtils.random(0.55f, 0.95f)
            val sx = cx + MathUtils.cos(ang) * r
            val sy = cy + MathUtils.sin(ang) * r
            // Летить до центру по спіралі: радіальна + дотична складові
            val speed = 170f
            val vx = (cx - sx) / r * speed - MathUtils.sin(ang) * speed * 0.8f
            val vy = (cy - sy) / r * speed + MathUtils.cos(ang) * speed * 0.8f
            fx.spark(sx, sy, sparkColor, vx, vy, MathUtils.random(0.5f, 0.9f), MathUtils.random(2f, 4f))
        }
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        val a  = color.a * parentAlpha
        val d  = screen.drawerUtil.drawer
        val cx = x + width / 2f; val cy = y + height / 2f
        val r  = width / 2f
        super.draw(batch, parentAlpha)   // диск під ядром
        tmp.set(0f, 0f, 0f, a);             d.filledCircle(cx, cy, r * 0.95f, tmp)
        tmp.set(sparkColor); tmp.a = 0.9f * a; d.setColor(tmp)
        d.circle(cx, cy, r * 0.98f + 2f * MathUtils.sin(time * 5f), 3f)
        screen.drawerUtil.additive {
            tmp.set(sparkColor); tmp.a = 0.25f * a
            setColor(tmp)
            circle(cx, cy, r * 1.15f, 10f)
        }
        d.setColor(Color.WHITE)
    }
}

/** Портал: кільце-вихор. */
class APortal(override val screen: AdvancedScreen, accent: Color) : AdvancedGroup() {

    private val vortex = VortexActor(screen.drawerUtil.whiteRegion, VortexShader.MODE_PORTAL, Color(accent), Color.valueOf("FFFFFF"))
    private var time = 0f
    private val tmp  = Color()
    private val ring = Color(accent)

    override fun addActorsOnGroup() {
        val vw = width * 1.55f
        vortex.setBounds(width / 2f - vw / 2f, height / 2f - vw / 2f, vw, vw)
        vortex.speed = 1.3f
        addActor(vortex)
    }

    /** Спалах при телепортації. */
    fun flash() { vortex.speed = 4f; time = 0f }

    override fun act(delta: Float) {
        super.act(delta)
        time += delta
        if (vortex.speed > 1.3f) vortex.speed = (vortex.speed - delta * 5f).coerceAtLeast(1.3f)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        val a  = color.a * parentAlpha
        val d  = screen.drawerUtil.drawer
        val cx = x + width / 2f; val cy = y + height / 2f
        super.draw(batch, parentAlpha)
        tmp.set(ring).lerp(Color.WHITE, 0.4f); tmp.a = 0.8f * a; d.setColor(tmp)
        d.circle(cx, cy, width * 0.46f, 2.5f)
        d.setColor(Color.WHITE)
    }
}

/** Варп-ворота — фініш рівня. */
class AGate(override val screen: AdvancedScreen, accent: Color) : AdvancedGroup() {

    private val vortex = VortexActor(screen.drawerUtil.whiteRegion, VortexShader.MODE_GATE, Color.valueOf("FFFFFF"), Color(accent))
    private var time = 0f
    private val tmp  = Color()
    private val ring = Color(accent)

    override fun addActorsOnGroup() {
        val vw = width * 1.7f
        vortex.setBounds(width / 2f - vw / 2f, height / 2f - vw / 2f, vw, vw)
        addActor(vortex)
    }

    /** М'яч усередині — вихор прискорюється. */
    fun activate() { vortex.speed = 4f }

    override fun act(delta: Float) { super.act(delta); time += delta }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        val a  = color.a * parentAlpha
        val d  = screen.drawerUtil.drawer
        val cx = x + width / 2f; val cy = y + height / 2f
        val r  = width * 0.48f
        super.draw(batch, parentAlpha)
        // Пунктирне кільце, що обертається
        tmp.set(ring).lerp(Color.WHITE, 0.5f); tmp.a = 0.9f * a; d.setColor(tmp)
        val segments = 8
        for (i in 0 until segments) {
            val start = time * 1.6f + i * MathUtils.PI2 / segments
            d.arc(cx, cy, r, start, MathUtils.PI2 / segments * 0.55f, 4f)
        }
        // Внутрішнє кільце проти годинникової
        tmp.a = 0.5f * a; d.setColor(tmp)
        for (i in 0 until 5) {
            val start = -time * 2.4f + i * MathUtils.PI2 / 5f
            d.arc(cx, cy, r * 0.7f, start, MathUtils.PI2 / 5f * 0.4f, 3f)
        }
        d.setColor(Color.WHITE)
    }
}
