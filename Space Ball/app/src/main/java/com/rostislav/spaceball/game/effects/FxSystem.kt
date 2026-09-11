package com.rostislav.spaceball.game.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.rostislav.spaceball.game.utils.ShapeDrawerUtil

/**
 * Легкі процедурні ефекти без текстур: частинки (іскри, пил, уламки)
 * і слід за м'ячем. Один екземпляр на ігровий екран, два шари:
 * [FxLayer] back — слід (під м'ячем), front — частинки (над усім).
 */
class FxSystem(private val drawerUtil: ShapeDrawerUtil) {

    companion object {
        private const val MAX_PARTICLES = 360
        private const val TRAIL_LENGTH  = 22
    }

    private class Particle {
        var x = 0f; var y = 0f
        var vx = 0f; var vy = 0f
        var life = 0f; var maxLife = 1f
        var size = 4f
        var gravity = 0f
        var drag = 0f
        /** 0 — крапка, 1 — іскра (риска вздовж швидкості), 2 — кільце, що розширюється. */
        var kind = 0
        var additive = false
        val color = Color()
    }

    private val particles = Array(MAX_PARTICLES) { Particle() }
    private var cursor = 0

    // --- Слід ---------------------------------------------------------------
    private val trailX = FloatArray(TRAIL_LENGTH)
    private val trailY = FloatArray(TRAIL_LENGTH)
    private val trailR = FloatArray(TRAIL_LENGTH)
    private var trailCount = 0
    private var trailHead  = 0
    val trailColor = Color(0.6f, 0.8f, 1f, 1f)
    var trailEnabled = true

    private val tmp = Color()

    // ------------------------------------------------------------------------
    // Emitters
    // ------------------------------------------------------------------------

    private fun next(): Particle {
        val p = particles[cursor]
        cursor = (cursor + 1) % MAX_PARTICLES
        return p
    }

    /** Вибух іскор у всі боки. */
    fun burst(
        x: Float, y: Float, color: Color, count: Int,
        speedMin: Float = 150f, speedMax: Float = 520f,
        sizeMin: Float = 3f, sizeMax: Float = 7f,
        life: Float = 0.7f, gravity: Float = -900f, kind: Int = 0, additive: Boolean = true,
    ) {
        repeat(count) {
            val p = next()
            val ang = MathUtils.random(0f, MathUtils.PI2)
            val spd = MathUtils.random(speedMin, speedMax)
            p.x = x; p.y = y
            p.vx = MathUtils.cos(ang) * spd
            p.vy = MathUtils.sin(ang) * spd
            p.maxLife = life * MathUtils.random(0.6f, 1.15f)
            p.life = p.maxLife
            p.size = MathUtils.random(sizeMin, sizeMax)
            p.gravity = gravity
            p.drag = 1.2f
            p.kind = kind
            p.additive = additive
            p.color.set(color)
        }
    }

    /** Пил при ударі об поверхню — летить угору конусом. */
    fun dust(x: Float, y: Float, color: Color, count: Int = 10, spread: Float = 60f) {
        repeat(count) {
            val p = next()
            val ang = MathUtils.random(90f - spread, 90f + spread) * MathUtils.degreesToRadians
            val spd = MathUtils.random(60f, 260f)
            p.x = x + MathUtils.random(-14f, 14f); p.y = y
            p.vx = MathUtils.cos(ang) * spd
            p.vy = MathUtils.sin(ang) * spd
            p.maxLife = MathUtils.random(0.3f, 0.6f)
            p.life = p.maxLife
            p.size = MathUtils.random(3f, 6f)
            p.gravity = -600f
            p.drag = 2f
            p.kind = 0
            p.additive = false
            p.color.set(color)
        }
    }

    /** Кільце ударної хвилі. */
    fun ring(x: Float, y: Float, color: Color, size: Float = 60f, life: Float = 0.45f) {
        val p = next()
        p.x = x; p.y = y; p.vx = 0f; p.vy = 0f
        p.maxLife = life; p.life = life
        p.size = size
        p.gravity = 0f; p.drag = 0f
        p.kind = 2
        p.additive = true
        p.color.set(color)
    }

    /** Одна повільна іскра (для постійного «дихання» об'єктів). */
    fun spark(x: Float, y: Float, color: Color, vx: Float, vy: Float, life: Float, size: Float, gravity: Float = 0f) {
        val p = next()
        p.x = x; p.y = y; p.vx = vx; p.vy = vy
        p.maxLife = life; p.life = life
        p.size = size
        p.gravity = gravity; p.drag = 0f
        p.kind = 0
        p.additive = true
        p.color.set(color)
    }

    fun trailPush(x: Float, y: Float, r: Float) {
        if (!trailEnabled) return
        if (trailCount > 0) {
            val last = (trailHead - 1 + TRAIL_LENGTH) % TRAIL_LENGTH
            if (Math.abs(trailX[last] - x) + Math.abs(trailY[last] - y) < 3f) return
        }
        trailX[trailHead] = x; trailY[trailHead] = y; trailR[trailHead] = r
        trailHead = (trailHead + 1) % TRAIL_LENGTH
        if (trailCount < TRAIL_LENGTH) trailCount++
    }

    fun trailClear() { trailCount = 0; trailHead = 0 }

    // ------------------------------------------------------------------------
    // Update / draw
    // ------------------------------------------------------------------------

    /** Зупинити частинки на місці (кадр для скріншота). */
    var frozen = false

    fun update(delta: Float) {
        if (frozen) return
        for (p in particles) {
            if (p.life <= 0f) continue
            p.life -= delta
            p.vy += p.gravity * delta
            if (p.drag > 0f) {
                val k = 1f - p.drag * delta
                p.vx *= k; p.vy *= k
            }
            p.x += p.vx * delta
            p.y += p.vy * delta
        }
    }

    fun drawTrail(parentAlpha: Float) {
        if (trailCount < 2) return
        val d = drawerUtil.drawer
        drawerUtil.additive {
            for (i in 0 until trailCount) {
                val idx = (trailHead - trailCount + i + TRAIL_LENGTH) % TRAIL_LENGTH
                val k = (i + 1f) / trailCount            // 0 — найстаріша, 1 — свіжа
                // М'яке широке сяйво + яскраве ядро
                tmp.set(trailColor).also { it.a = 0.22f * k * parentAlpha }
                d.filledCircle(trailX[idx], trailY[idx], trailR[idx] * (0.45f + 0.5f * k), tmp)
                tmp.a = 0.5f * k * parentAlpha
                d.filledCircle(trailX[idx], trailY[idx], trailR[idx] * (0.15f + 0.3f * k), tmp)
            }
        }
    }

    fun drawParticles(parentAlpha: Float) {
        val d = drawerUtil.drawer
        // Спершу звичайні, потім адитивні — щоб не перемикати змішування на кожну частинку
        for (pass in 0..1) {
            val additive = pass == 1
            if (additive) drawerUtil.additive { drawPass(true, parentAlpha) }
            else drawPass(false, parentAlpha)
        }
        d.setColor(Color.WHITE)
    }

    private fun drawPass(additive: Boolean, parentAlpha: Float) {
        val d = drawerUtil.drawer
        for (p in particles) {
            if (p.life <= 0f || p.additive != additive) continue
            val k = (p.life / p.maxLife).coerceIn(0f, 1f)
            tmp.set(p.color); tmp.a = p.color.a * k * parentAlpha
            when (p.kind) {
                1 -> {
                    val len = (p.size * 3f).coerceAtLeast(6f)
                    val vlen = Math.max(1f, Math.sqrt((p.vx * p.vx + p.vy * p.vy).toDouble()).toFloat())
                    d.line(p.x, p.y, p.x - p.vx / vlen * len, p.y - p.vy / vlen * len, tmp, p.size * 0.6f * k + 1f)
                }
                2 -> {
                    val r = p.size * (1f + 2.2f * (1f - k))
                    d.setColor(tmp)
                    d.circle(p.x, p.y, r, 4f * k + 1f)
                }
                else -> d.filledCircle(p.x, p.y, p.size * (0.4f + 0.6f * k), tmp)
            }
        }
    }
}

/** Актор-шар для [FxSystem]: [front] = частинки (оновлює систему), інакше — слід. */
class FxLayer(private val fx: FxSystem, private val front: Boolean) : Actor() {

    override fun act(delta: Float) {
        super.act(delta)
        if (front) fx.update(delta)
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        if (front) fx.drawParticles(parentAlpha * color.a) else fx.drawTrail(parentAlpha * color.a)
    }
}
