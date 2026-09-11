package com.rostislav.spaceball.game.utils.skin

import com.badlogic.gdx.graphics.Color

/**
 * Скіни м'яча. [region] — індекс у `assetsAllUtil.ballList` (−1 = базовий спрайт «ball»).
 * Нові м'ячі намальовані у квадраті 166 px майже без полів, тож спрайт малюється
 * ледь більшим за фізичне тіло ([spriteScale]), щоб куля візуально накривала колайдер.
 */
enum class BallSkin(
    val title: String,
    /** Ціна в зірках. 0 = безкоштовно. */
    val price: Int,
    val tint: Color,
    val trail: Color,
    val region: Int = -1,
    val spriteScale: Float = 1f,
    val glow: Boolean = false,
    /** Колір переливається веселкою. */
    val rainbow: Boolean = false,
) {
    CLASSIC("CLASSIC", 0,   Color.WHITE,             Color.valueOf("9FC5FF")),
    NEBULA ("NEBULA",  30,  Color.WHITE,             Color.valueOf("C86BFF"), region = 0, spriteScale = 1.06f),
    VERDANT("VERDANT", 50,  Color.WHITE,             Color.valueOf("7CFF6B"), region = 1, spriteScale = 1.06f),
    MAGMA  ("MAGMA",   80,  Color.WHITE,             Color.valueOf("FF5C4E"), region = 2, spriteScale = 1.06f),
    CRYSTAL("CRYSTAL", 120, Color.WHITE,             Color.valueOf("6FE3FF"), region = 3, spriteScale = 1.06f),
    GOLD   ("GOLD",    160, Color.valueOf("FFD65C"), Color.valueOf("FFB300"), glow = true),
    PRISM  ("PRISM",   220, Color.WHITE,             Color.WHITE,             region = 3, spriteScale = 1.06f, glow = true, rainbow = true);

    companion object {
        fun of(ordinal: Int): BallSkin = entries.getOrElse(ordinal) { CLASSIC }
    }
}
