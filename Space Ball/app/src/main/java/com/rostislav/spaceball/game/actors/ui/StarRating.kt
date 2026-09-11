package com.rostislav.spaceball.game.actors.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.rostislav.spaceball.game.utils.ShapeDrawerUtil
import space.earlygrey.shapedrawer.ShapeDrawer

/** Малює п'ятикутну зірку через ShapeDrawer (5 трикутників + п'ятикутник) — без текстур. */
object StarShape {
    private val verts = FloatArray(20)

    fun fill(d: ShapeDrawer, cx: Float, cy: Float, radius: Float, color: Color, rotationDeg: Float = 0f) {
        val inner = radius * 0.42f
        for (i in 0 until 5) {
            val aOut = (90f + 72f * i + rotationDeg) * MathUtils.degreesToRadians
            val aIn  = (90f + 36f + 72f * i + rotationDeg) * MathUtils.degreesToRadians
            verts[i * 4]     = cx + MathUtils.cos(aOut) * radius
            verts[i * 4 + 1] = cy + MathUtils.sin(aOut) * radius
            verts[i * 4 + 2] = cx + MathUtils.cos(aIn) * inner
            verts[i * 4 + 3] = cy + MathUtils.sin(aIn) * inner
        }
        d.setColor(color)
        // Внутрішній п'ятикутник
        d.filledPolygon(cx, cy, 5, inner, (90f + 36f + rotationDeg) * MathUtils.degreesToRadians)
        // Промені: вершина i лежить між внутрішніми точками i−1 та i
        for (i in 0 until 5) {
            val p = (i + 4) % 5
            d.filledTriangle(
                verts[p * 4 + 2], verts[p * 4 + 3],
                verts[i * 4],     verts[i * 4 + 1],
                verts[i * 4 + 2], verts[i * 4 + 3],
            )
        }
        d.setColor(Color.WHITE)
    }
}

/**
 * Рядок із трьох зірок: [earned] золоті, решта — темні.
 * [pop] анімує появу зірок по черзі (екран перемоги).
 */
class StarRating(
    private val drawerUtil: ShapeDrawerUtil,
    var earned: Int,
    private val starSize: Float,
    private val gap: Float = starSize * 0.35f,
    private val big: Boolean = false,
) : Actor() {

    companion object {
        val GOLD   = Color.valueOf("FFD33D")
        val GOLD_2 = Color.valueOf("FF9F1C")
        val DARK   = Color(0.08f, 0.10f, 0.20f, 0.85f)
        val GLOW   = Color(1f, 0.85f, 0.3f, 0.35f)
    }

    private var time = 0f
    private var popping = false
    private val tmp = Color()

    init {
        setSize(starSize * 3f + gap * 2f, starSize * (if (big) 1.35f else 1f))
    }

    fun pop() { popping = true; time = 0f }

    override fun act(delta: Float) {
        super.act(delta)
        time += delta
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        val d = drawerUtil.drawer
        val a = color.a * parentAlpha
        for (i in 0 until 3) {
            val cx = x + starSize / 2f + i * (starSize + gap)
            // Середня зірка більша, як на екрані перемоги
            val base = if (big && i == 1) starSize * 0.68f else starSize * 0.5f
            val cy = y + height / 2f

            var scale = 1f
            var lit = i < earned
            if (popping && lit) {
                val t = time - 0.35f * i
                if (t < 0f) lit = false
                else scale = if (t < 0.25f) 0.2f + 1.3f * (t / 0.25f) else if (t < 0.4f) 1.5f - 0.5f * ((t - 0.25f) / 0.15f) else 1f
            }
            val r = base * scale

            if (lit) {
                drawerUtil.additive {
                    tmp.set(GLOW); tmp.a = GLOW.a * a * (0.7f + 0.3f * MathUtils.sin(time * 4f + i))
                    filledCircle(cx, cy, r * 1.35f, tmp)
                }
                tmp.set(GOLD_2); tmp.a = a
                StarShape.fill(d, cx, cy - r * 0.06f, r * 1.02f, tmp)
                tmp.set(GOLD); tmp.a = a
                StarShape.fill(d, cx, cy, r, tmp)
                tmp.set(1f, 1f, 1f, 0.45f * a)
                d.filledCircle(cx - r * 0.25f, cy + r * 0.25f, r * 0.16f, tmp)
            } else {
                tmp.set(DARK); tmp.a = DARK.a * a
                StarShape.fill(d, cx, cy, r, tmp)
                tmp.set(1f, 1f, 1f, 0.12f * a)
                d.setColor(tmp)
                d.circle(cx, cy, r * 0.8f, 1.5f)
            }
        }
        d.setColor(Color.WHITE)
    }
}
