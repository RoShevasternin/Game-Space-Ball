package com.rostislav.spaceball.game.utils.level

import com.badlogic.gdx.math.Vector2

/** Різновиди платформ — кожна планета додає свої. */
enum class PlatformKind {
    /** Звичайна нерухома. */
    STATIC,
    /** Їздить по горизонталі туди-сюди ([PlatformData.rangeX], [PlatformData.speed]). */
    MOVING,
    /** Батут: м'яч не зупиняється, а підкидається вгору. */
    TRAMPOLINE,
    /** Слиз: нульове тертя, м'яч не гальмує сам. */
    SLIME,
    /** Обвалюється через пів секунди після дотику, повертається за кілька секунд. */
    CRUMBLE,
}

data class PlatformData(
    val x: Float,
    val y: Float,
    val kind: PlatformKind = PlatformKind.STATIC,
    /** Для MOVING: на скільки px платформа від'їжджає від [x] (може бути від'ємним). */
    val rangeX: Float = 0f,
    /** Для MOVING: швидкість, px/с. */
    val speed: Float = 0f,
) {
    val position get() = Vector2(x, y)
}

data class SpikeData(
    val x: Float,
    val y: Float,
    /** Індекс платформи, разом з якою рухається шип (−1 = не прив'язаний). */
    val attachedTo: Int = -1,
    /** Для «повзучого» шипа: діапазон і швидкість власного руху. */
    val rangeX: Float = 0f,
    val speed: Float = 0f,
) {
    val position get() = Vector2(x, y)
    val isSliding get() = rangeX != 0f && speed > 0f
}

/** Пара порталів: вхід в A виводить із B і навпаки. Координати — центри. */
data class PortalData(val ax: Float, val ay: Float, val bx: Float, val by: Float)

/** Чорна діра: [x],[y] — центр, [pullRadius] — радіус притягання (px), [strength] — сила (м³/с²). */
data class BlackHoleData(val x: Float, val y: Float, val pullRadius: Float, val strength: Float)

/**
 * Горизонтальний лазер від [x] завширшки [w] на висоті [y] (центр променя).
 * Увімкнений половину [period], зсув фази — [phase] (0..1).
 */
data class LaserData(val x: Float, val y: Float, val w: Float, val period: Float, val phase: Float)

/** Астероїдний дощ: новий астероїд кожні [interval] с. */
data class AsteroidRainData(val interval: Float)

data class LevelData(
    val planet: Planet,
    val plats: List<PlatformData>,
    val tris: List<SpikeData>,
    val stars: List<Vector2>,
    /** Центр варп-воріт — фініш рівня. */
    val gate: Vector2,
    val portals: List<PortalData> = emptyList(),
    val blackHoles: List<BlackHoleData> = emptyList(),
    val lasers: List<LaserData> = emptyList(),
    val asteroids: AsteroidRainData? = null,
) {
    val gravity get() = planet.gravity
}

/** Підказка, яку показуємо на початку рівня, де вперше з'являється механіка. */
data class LevelHint(val id: String, val text: String)
