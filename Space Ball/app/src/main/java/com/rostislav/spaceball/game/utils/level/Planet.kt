package com.rostislav.spaceball.game.utils.level

import com.badlogic.gdx.graphics.Color

/**
 * Чотири планети по [LEVELS_PER_PLANET] рівнів. Планета задає оформлення
 * (фон, кольори, спрайти зірок і шипів — індекс [theme]) і гравітацію,
 * а головне — які механіки з'являються на її рівнях (див. [LevelGenerator]).
 *
 * Порядок і кольори прив'язані до графіки: фон 1 — пурпуровий, 2 — зелений,
 * 3 — магента з поясом астероїдів, 4 — кристалічний із галактикою.
 */
enum class Planet(
    val title: String,
    val subtitle: String,
    val gravity: Float,
    /** Колір платформ. */
    val platformColor: Color,
    /** Колір землі. */
    val groundColor: Color,
    /** Акцент інтерфейсу / світіння. */
    val accent: Color,
    /** Другий колір неонового обідка (градієнт accent → accent2). */
    val accent2: Color,
) {
    LUMINA ("LUMINA",  "MOVING PLATFORMS",     -9.8f, Color.valueOf("D2C4FF"), Color.valueOf("B9A6F5"), Color.valueOf("FF7BE0"), Color.valueOf("7FA4FF")),
    VERDIS ("VERDIS",  "TRAMPOLINES & SLIME",  -9.8f, Color.valueOf("173F36"), Color.valueOf("2E8F5F"), Color.valueOf("7CFF6B"), Color.valueOf("2EE6C8")),
    ASTERIA("ASTERIA", "LASERS & ASTEROIDS",   -9.8f, Color.valueOf("3B1B5E"), Color.valueOf("8A3FC4"), Color.valueOf("FF5CC8"), Color.valueOf("FF9A5C")),
    NEBULON("NEBULON", "LOW GRAVITY & PORTALS", -7.0f, Color.valueOf("5A4BD6"), Color.valueOf("7C6CF0"), Color.valueOf("9FB4FF"), Color.valueOf("E08CFF"));

    /** Індекс набору графіки (фон, зірка, шип, планета у списку). */
    val theme get() = ordinal

    val firstLevel get() = ordinal * LEVELS_PER_PLANET
    val lastLevel  get() = firstLevel + LEVELS_PER_PLANET - 1

    companion object {
        const val LEVELS_PER_PLANET = 15

        fun of(level: Int): Planet = entries[(level / LEVELS_PER_PLANET).coerceIn(0, entries.size - 1)]

        /** Номер рівня всередині планети: 0..14. */
        fun indexInPlanet(level: Int): Int = level % LEVELS_PER_PLANET
    }
}
