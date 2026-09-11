package com.rostislav.spaceball.game.actors.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.MathUtils
import com.rostislav.spaceball.game.utils.advanced.AdvancedGroup
import com.rostislav.spaceball.game.utils.advanced.AdvancedScreen
import com.rostislav.spaceball.game.utils.level.PlatformKind

/**
 * Платформа будь-якого типу, намальована ShapeDrawer-ом: без текстур,
 * зате кожен тип упізнається з першого погляду. Верхня грань світиться
 * кольором планети — під неоновий стиль фонів.
 */
class APlatform(
    override val screen: AdvancedScreen,
    private val kind: PlatformKind,
    private val baseColor: Color,
    private val accent: Color,
) : AdvancedGroup() {

    /** Для MOVING: «рейка» в координатах сцени, задається після створення тіла. */
    var trackFrom = 0f
    var trackTo   = 0f
    var trackY    = 0f

    /** CRUMBLE: тремтіння перед обвалом. */
    var shaking = false

    private var time   = 0f
    private var squash = 0f
    private val tmp    = Color()
    private val rock   = Color.valueOf("5E4A55")
    private val slime  = Color.valueOf("8CFF5C")
    private val slimeDark = Color.valueOf("2E8F3A")
    /** Колір обвальної платформи — окремий об'єкт, бо [tmp] перезаписується всередині [slab]. */
    private val crumbleColor = Color(baseColor).lerp(rock, 0.6f)

    override fun addActorsOnGroup() {}

    /** TRAMPOLINE: анімація стискання при відскоку. */
    fun bounce() { squash = 1f }

    override fun act(delta: Float) {
        super.act(delta)
        time += delta
        if (squash > 0f) squash = (squash - delta * 4.5f).coerceAtLeast(0f)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        val a = color.a * parentAlpha
        if (a <= 0.01f) { super.draw(batch, parentAlpha); return }

        var px = x
        var py = y
        if (shaking) { px += MathUtils.random(-4f, 4f); py += MathUtils.random(-3f, 3f) }

        when (kind) {
            PlatformKind.STATIC     -> slab(px, py, width, height, baseColor, a)
            PlatformKind.MOVING     -> drawMoving(px, py, a)
            PlatformKind.TRAMPOLINE -> drawTrampoline(px, py, a)
            PlatformKind.SLIME      -> drawSlime(px, py, a)
            PlatformKind.CRUMBLE    -> drawCrumble(px, py, a)
        }
        screen.drawerUtil.drawer.setColor(Color.WHITE)
        super.draw(batch, parentAlpha)
    }

    // ------------------------------------------------------------------------

    /** Базова плита: тінь, тіло, темний низ і неонова верхня грань. */
    private fun slab(px: Float, py: Float, w: Float, h: Float, c: Color, a: Float, neon: Color = accent) {
        val d = screen.drawerUtil.drawer
        tmp.set(0f, 0f, 0f, 0.30f * a); d.filledRectangle(px + 3f, py - 7f, w, h, tmp)
        tmp.set(c); tmp.a = a;           d.filledRectangle(px, py, w, h, tmp)
        tmp.set(0f, 0f, 0f, 0.30f * a);  d.filledRectangle(px, py, w, 4f, tmp)
        screen.drawerUtil.additive {
            tmp.set(neon); tmp.a = 0.22f * a * (0.8f + 0.2f * MathUtils.sin(time * 2f))
            filledRectangle(px - 3f, py + h - 8f, w + 6f, 14f, tmp)
        }
        tmp.set(neon).lerp(Color.WHITE, 0.55f); tmp.a = 0.95f * a
        d.filledRectangle(px, py + h - 3f, w, 3f, tmp)
    }

    private fun drawMoving(px: Float, py: Float, a: Float) {
        val d = screen.drawerUtil.drawer
        // Рейка
        val ty = trackY + height / 2f
        tmp.set(accent); tmp.a = 0.22f * a
        d.line(trackFrom + 8f, ty, trackTo - 8f, ty, tmp, 3f)
        var tx = trackFrom + 12f
        while (tx < trackTo - 8f) { d.filledCircle(tx, ty, 3f, tmp); tx += 26f }
        slab(px, py, width, height, baseColor, a)
        // Шеврони, що біжать у напрямку руху
        tmp.set(accent).lerp(Color.WHITE, 0.3f); tmp.a = 0.8f * a
        d.setColor(tmp)
        val shift = (time * 60f) % 34f
        var cx = px + 20f + shift
        while (cx < px + width - 20f) {
            d.line(cx - 6f, py + 7f, cx, py + height / 2f, 2.5f)
            d.line(cx, py + height / 2f, cx - 6f, py + height - 7f, 2.5f)
            cx += 34f
        }
    }

    private fun drawTrampoline(px: Float, py: Float, a: Float) {
        val d = screen.drawerUtil.drawer
        val k = squash * squash
        val bodyH = height * (1f - 0.35f * k)
        // Основа
        tmp.set(baseColor).mul(0.6f, 0.6f, 0.7f, 1f); tmp.a = a
        d.filledRectangle(px + 3f, py - 7f, width, bodyH, Color(0f, 0f, 0f, 0.30f * a))
        d.filledRectangle(px, py, width, bodyH, tmp)
        // Пружини
        tmp.set(1f, 1f, 1f, 0.45f * a); d.setColor(tmp)
        for (i in 0 until 6) {
            val sx = px + 25f + i * (width - 50f) / 5f
            val zig = 5f
            var yy = py + 4f
            var s = 1f
            while (yy < py + bodyH - 6f) { d.line(sx - zig * s, yy, sx + zig * s, yy + 5f, 2f); yy += 5f; s = -s }
        }
        // Пружна стрічка зверху
        val bandH = 9f
        val bandY = py + bodyH - bandH + 2f
        screen.drawerUtil.additive {
            tmp.set(accent); tmp.a = 0.40f * a * (0.75f + 0.25f * MathUtils.sin(time * 5f))
            filledRectangle(px - 4f, bandY - 6f, width + 8f, bandH + 12f, tmp)
        }
        tmp.set(accent); tmp.a = a; d.filledRectangle(px, bandY, width, bandH, tmp)
        tmp.set(1f, 1f, 1f, 0.6f * a); d.filledRectangle(px, bandY + bandH - 3f, width, 3f, tmp)
    }

    /** Слиз: зелена желейна маса з хвилястим верхом, краплями, що стікають, і блискітками. */
    private fun drawSlime(px: Float, py: Float, a: Float) {
        val d = screen.drawerUtil.drawer
        slab(px, py, width, height * 0.55f, slimeDark, a, neon = slime)
        // Желе
        tmp.set(slime); tmp.a = 0.88f * a
        d.filledRectangle(px, py + height * 0.35f, width, height * 0.65f, tmp)
        var bx = px + 14f
        var i = 0
        while (bx < px + width - 10f) {
            val wob = 3f * MathUtils.sin(time * 3f + i * 1.3f)
            d.filledCircle(bx, py + height + wob, 12f, tmp)
            bx += 26f; i++
        }
        // Краплі під платформою
        tmp.set(slime).lerp(slimeDark, 0.2f); tmp.a = 0.85f * a
        for (j in 0 until 3) {
            val dx = px + 45f + j * 80f
            val drip = 10f + 8f * (0.5f + 0.5f * MathUtils.sin(time * 1.7f + j * 2.1f))
            d.filledRectangle(dx - 4f, py - drip, 8f, drip + 2f, tmp)
            d.filledCircle(dx, py - drip, 6f, tmp)
        }
        // Блискітки й сяйво
        screen.drawerUtil.additive {
            tmp.set(slime); tmp.a = 0.18f * a
            filledRectangle(px - 6f, py - 4f, width + 12f, height + 10f, tmp)
            for (j in 0 until 4) {
                val tw = 0.5f + 0.5f * MathUtils.sin(time * 5f + j * 1.9f)
                tmp.set(1f, 1f, 1f, 0.75f * tw * a)
                filledCircle(px + 30f + j * 62f, py + height * 0.7f, 2.5f + 1.5f * tw, tmp)
            }
        }
    }

    private fun drawCrumble(px: Float, py: Float, a: Float) {
        val d = screen.drawerUtil.drawer
        slab(px, py, width, height, crumbleColor, a)
        // Тріщини
        tmp.set(0f, 0f, 0f, 0.6f * a); d.setColor(tmp)
        val cr = floatArrayOf(0.18f, 0.42f, 0.66f, 0.86f)
        for ((i, f) in cr.withIndex()) {
            val cx = px + width * f
            val dir = if (i % 2 == 0) 1f else -1f
            d.line(cx, py + height, cx + 6f * dir, py + height * 0.55f, 2f)
            d.line(cx + 6f * dir, py + height * 0.55f, cx - 4f * dir, py + 2f, 2f)
            if (i == 1) d.line(cx + 6f * dir, py + height * 0.55f, cx + 20f * dir, py + height * 0.4f, 1.5f)
        }
        if (shaking) {
            tmp.set(1f, 0.6f, 0.3f, 0.35f * a)
            d.filledRectangle(px, py, width, height, tmp)
        }
    }
}
