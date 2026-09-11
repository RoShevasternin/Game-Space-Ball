package com.rostislav.spaceball.game.utils.level

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Генератор рівнів.
 *
 * Перші 2 рівні — туторіал, зашиті вручну. Решта генерується детерміновано:
 * seed залежить лише від номера рівня, тому рівень №17 завжди виглядає однаково.
 *
 * Рівень будується у два кроки:
 *  1. Базова розкладка 5 платформ ([buildHeights] + [buildColumns]) — перевірена фізикою,
 *     гарантує досяжність і відсутність «пасток». Розміри розривів і кроків ростуть
 *     з номером рівня ([Curve]): перші рівні компактні й прощають помилки.
 *  2. «Рецепт» планети ([recipe]) накладає механіки: рухомі платформи, батути, слиз,
 *     обвали, портали, чорні діри, лазери, астероїди. Кожна планета вводить своє,
 *     а складність усередині планети росте з номером рівня.
 *
 * ФІЗИКА (звідки взялись обмеження):
 *   METER_UI = 1080 / 6.75 = 160 px/м, гравітація 9.8 м/с².
 *   М'яч: діаметр 119 px = 0.744 м, радіус 0.372 м, density 3
 *         → маса = 3 * π * 0.372² ≈ 1.30 кг.
 *   Кнопка UP   : імпульс 5  → Δv = 3.83 м/с → висота стрибка ≈ 0.75 м ≈ 120 px
 *   Кнопка UPUP : імпульс 10 → Δv = 7.67 м/с → висота стрибка ≈ 3.0  м ≈ 480 px
 *   Кнопки LEFT/RIGHT: імпульс 4 → 3.07 м/с ≈ 490 px/с,
 *         час польоту при UPUP ≈ 1.56 с → горизонтальний виліт ≈ 760 px за один тап.
 *
 * Звідси (для найскладніших рівнів):
 *   MAX_GAP_Y = 330 px — максимальний вертикальний розрив між платформами.
 *       З урахуванням товщини платформи (28) треба піднятись на 358 px < 480 px,
 *       тобто одного тапу UPUP вистачає із запасом ~25%.
 *   MAX_STEP_X = 620 px — горизонтальний крок, легко перекривається одним тапом вбік.
 *   MIN_CLEAR_Y = 300 px — якщо дві платформи перекриваються по X, між ними
 *       має бути щонайменше 300 px, інакше м'яч (119 px) не пролізе і застрягне.
 *       300 - 28 (платформа) - 70 (трикутник) = 202 px просвіту > 119 px. OK.
 */
object LevelGenerator {

    const val LEVEL_COUNT = Planet.LEVELS_PER_PLANET * 4

    const val PLAT_COUNT = 5
    const val STAR_COUNT = 3

    val PLAT_SIZE = Vector2(250f, 28f)
    val TRI_SIZE  = Vector2(58f, 70f)
    val STAR_SIZE = Vector2(70f, 67f)

    const val GATE_SIZE   = 120f
    const val PORTAL_SIZE = 110f
    /** Діаметр ядра чорної діри — смертельна зона. */
    const val HOLE_CORE   = 84f
    /** Товщина променя лазера (px) — фізична форма laser_* ≈ 24 px, актор малюється сам. */
    const val LASER_H     = 24f

    // --- Ігрове поле -------------------------------------------------------
    const val FIELD_W = 1080f
    const val FIELD_H = 1920f

    // Верх нижньої платформи (bDown) = 278, старт м'яча = (481, 382), діаметр 119.
    const val GROUND_TOP    = 278f
    const val BALL_START_X  = 481f
    const val BALL_START_Y  = 382f
    private const val BALL_CENTER_X = 540.5f

    // --- Обмеження генерації ----------------------------------------------
    private const val X_MIN = 0f
    private val      X_MAX  = FIELD_W - PLAT_SIZE.x            // 830

    private const val MIN_CLEAR_Y = 300f
    private val      OVERLAP_X    = PLAT_SIZE.x + 20f          // 270

    // Зміщення об'єктів від краю платформи підібрані так, щоб вільна зона
    // для приземлення м'яча (119 px) завжди була >= 147 px:
    //   трикутник ліворуч  8..40   → вільно праворуч 250 - 98  = 152
    //   трикутник праворуч 152..184 → вільно ліворуч            = 152

    /**
     * Крива складності: усі геометричні межі плавно ростуть від першого рівня
     * до останнього. На старті платформи близько одна до одної, кроки короткі —
     * новачок не програє з першої спроби.
     */
    private class Curve(level: Int) {
        val t = (level / (LEVEL_COUNT - 1f)).coerceIn(0f, 1f)
        private fun l(a: Float, b: Float) = MathUtils.lerp(a, b, t)

        val yFirstMin    = l(500f, 600f)
        val yFirstMax    = l(560f, 700f)          // розрив від землі ≤ 422 px на максимумі
        val minGap       = l(140f, 165f)
        val maxGap       = l(215f, 330f)
        val yTopMin      = l(1340f, 1480f)
        val yTopMax      = l(1520f, 1750f)
        val maxStep      = l(420f, 620f)
        val minStep      = l(110f, 140f)
        /** Перша платформа не має висіти над м'ячем: інакше стрибок угору б'є об її низ. */
        val firstMinStep = 210f
        /**
         * На перших рівнях жодна платформа не висить «стелею» над нижчою ближче за
         * висоту стрибка: новачок, що стрибає вертикально, не б'ється головою.
         * Далі це стає частиною челенджу (треба стрибати вбік, а не вгору).
         */
        val noCeilings   = t < 0.4f
    }

    /** Висота стрибка UPUP (480) + діаметр м'яча (119): ближче — платформа зверху стає стелею. */
    private const val CEILING_CLEAR_Y = 600f

    // =======================================================================
    // Public
    // =======================================================================

    fun get(level: Int): LevelData {
        val safe = level.coerceIn(0, LEVEL_COUNT - 1)
        TUTORIAL.getOrNull(safe)?.let { return it }

        val planet = Planet.of(safe)
        return generate(
            seed   = safe * 7919L + 104729L,
            planet = planet,
            recipe = recipe(planet, Planet.indexInPlanet(safe)),
            curve  = Curve(safe),
        )
    }

    /**
     * Щоденний рівень: один на добу, однаковий для всіх гравців.
     * Планета змінюється по колу, складність — максимальна для цієї планети.
     */
    fun daily(epochDay: Long): LevelData {
        val planet = Planet.entries[(epochDay % Planet.entries.size).toInt()]
        return generate(
            seed   = epochDay * 31337L + 17L,
            planet = planet,
            recipe = recipe(planet, Planet.LEVELS_PER_PLANET - 1),
            curve  = Curve(planet.lastLevel),
        )
    }

    /** Підказка для рівня, де механіка з'являється вперше. */
    fun introHint(level: Int): LevelHint? {
        val planet = Planet.of(level)
        val k = Planet.indexInPlanet(level)
        return when (planet) {
            Planet.LUMINA -> when (k) {
                7  -> LevelHint("moving",  "MOVING PLATFORMS\nWAIT FOR THE RIGHT MOMENT")
                else -> null
            }
            Planet.VERDIS -> when (k) {
                0  -> LevelHint("tramp",   "TRAMPOLINES BOUNCE YOU HIGH\nYOU CAN'T STAND ON THEM")
                3  -> LevelHint("slime",   "SLIME IS SLIPPERY\nTAP THE OTHER WAY TO STOP")
                10 -> LevelHint("sliding", "SLIDING SPIKES\nTIME YOUR LANDING")
                else -> null
            }
            Planet.ASTERIA -> when (k) {
                0  -> LevelHint("crumble", "CRUMBLING PLATFORMS\nDON'T STAND STILL")
                2  -> LevelHint("laser",   "LASERS BLINK\nPASS WHEN THEY ARE OFF")
                4  -> LevelHint("rain",    "ASTEROIDS INCOMING\nWATCH THE WARNING ON TOP")
                else -> null
            }
            Planet.NEBULON -> when (k) {
                0  -> LevelHint("portal",  "LOW GRAVITY: JUMPS ARE FLOATY\nPORTALS TELEPORT YOU")
                2  -> LevelHint("hole",    "BLACK HOLES PULL YOU IN\nKEEP YOUR DISTANCE")
                else -> null
            }
        }
    }

    // =======================================================================
    // Рецепти планет
    // =======================================================================

    private data class Recipe(
        val spikes: Int,
        val moving: Int = 0,
        val slidingSpikes: Int = 0,
        val trampolines: Int = 0,
        val slime: Int = 0,
        val crumble: Int = 0,
        val portals: Int = 0,
        val holes: Int = 0,
        val lasers: Int = 0,
        /** 0 = астероїдів немає. */
        val asteroidInterval: Float = 0f,
    )

    /** [k] — номер рівня всередині планети, 0..14. */
    private fun recipe(planet: Planet, k: Int): Recipe = when (planet) {
        Planet.LUMINA -> Recipe(
            spikes = when { k <= 2 -> 1; k <= 9 -> 2; else -> 3 },
            moving = when { k >= 12 -> 2; k >= 7 -> 1; else -> 0 },
        )
        Planet.VERDIS -> Recipe(
            spikes        = when { k < 4 -> 2; k < 9 -> 3; else -> 4 },
            trampolines   = if (k >= 8) 2 else 1,
            slime         = if (k >= 3) 1 else 0,
            moving        = if (k >= 6) 1 else 0,
            slidingSpikes = if (k >= 10) 1 else 0,
        )
        Planet.ASTERIA -> Recipe(
            spikes           = when { k < 4 -> 2; k < 9 -> 3; else -> 4 },
            crumble          = when { k >= 10 -> 3; k >= 5 -> 2; else -> 1 },
            lasers           = when { k >= 8 -> 2; k >= 2 -> 1; else -> 0 },
            asteroidInterval = if (k >= 4) 3.4f - (k - 4) / 10f * 1.6f else 0f,
            moving           = if (k >= 6) 1 else 0,
            slidingSpikes    = if (k >= 9) 1 else 0,
        )
        Planet.NEBULON -> Recipe(
            spikes        = if (k < 4) 3 else 4,
            portals       = 1,
            holes         = when { k >= 9 -> 2; k >= 2 -> 1; else -> 0 },
            moving        = if (k >= 4) 1 else 0,
            trampolines   = if (k >= 6) 1 else 0,
            crumble       = if (k >= 8) 1 else 0,
            lasers        = if (k >= 11) 1 else 0,
            slidingSpikes = if (k >= 12) 1 else 0,
        )
    }

    // =======================================================================
    // Генерація
    // =======================================================================

    private fun generate(seed: Long, planet: Planet, recipe: Recipe, curve: Curve): LevelData {
        val rnd = Random(seed)

        val ys = buildHeights(rnd, curve)
        val xs = buildColumns(rnd, ys, curve)

        val kinds  = Array(PLAT_COUNT) { PlatformKind.STATIC }
        val ranges = FloatArray(PLAT_COUNT)
        val speeds = FloatArray(PLAT_COUNT)

        // --- 1. Різновиди платформ (верхня, з воротами, завжди звичайна) ---------------------
        val free = (0 until PLAT_COUNT - 1).shuffled(rnd).toMutableList()
        fun assign(count: Int, kind: PlatformKind) {
            repeat(count) { if (free.isNotEmpty()) kinds[free.removeAt(0)] = kind }
        }
        assign(recipe.trampolines, PlatformKind.TRAMPOLINE)
        assign(recipe.crumble,     PlatformKind.CRUMBLE)
        assign(recipe.slime,       PlatformKind.SLIME)

        // Рухомі — тільки якщо є куди їздити без конфліктів з сусідками
        val movingCandidates = free.toList()
        var movingLeft = recipe.moving
        for (i in movingCandidates) {
            if (movingLeft == 0) break
            val range = pickMovingRange(rnd, xs, ys, ranges, i) ?: continue
            kinds[i]  = PlatformKind.MOVING
            ranges[i] = range
            speeds[i] = rnd.range(80f, 100f + 50f * curve.t)
            free.remove(i)
            movingLeft--
        }

        val plats = List(PLAT_COUNT) { i -> PlatformData(xs[i], ys[i], kinds[i], ranges[i], speeds[i]) }

        // --- 2. Зірки та шипи -------------------------------------------------------------
        // Зірки — на нерухомих платформах у першу чергу, щоб не висіли в повітрі.
        val starOrder = (0 until PLAT_COUNT - 1).shuffled(rnd).sortedBy { i ->
            when (kinds[i]) { PlatformKind.MOVING -> 2; PlatformKind.TRAMPOLINE -> 1; else -> 0 }
        }
        val starPlats = starOrder.take(STAR_COUNT).toSet()

        // Шипи — не на батутах (м'яч там некерований) і не на першій платформі
        // перших рівнів (перший стрибок має бути безпечним)
        val spikeCandidates = (0 until PLAT_COUNT - 1)
            .filter { kinds[it] != PlatformKind.TRAMPOLINE && !(curve.t < 0.15f && it == 0) }
            .shuffled(rnd)
        val triPlats = spikeCandidates.take(recipe.spikes).toSet()

        val tris  = ArrayList<SpikeData>()
        val stars = ArrayList<Vector2>()

        var prevCenter = BALL_CENTER_X
        for (i in 0 until PLAT_COUNT - 1) {
            val x = xs[i]
            val y = ys[i]
            val center = x + PLAT_SIZE.x / 2f
            // З якого боку гравець підлітає до платформи — туди й ставимо зірку,
            // а трикутник на протилежний край. Так рівень завжди чесний.
            val fromLeft = prevCenter <= center
            prevCenter = center

            val objY = y + PLAT_SIZE.y - 1f
            val hasStar = i in starPlats
            val hasTri  = i in triPlats
            val attached = if (kinds[i] == PlatformKind.MOVING) i else -1

            when {
                hasStar && hasTri -> if (fromLeft) {
                    stars.add(Vector2(x + rnd.range(8f, 40f),   objY))
                    tris .add(SpikeData(x + rnd.range(152f, 184f), objY, attached))
                } else {
                    stars.add(Vector2(x + rnd.range(140f, 172f), objY))
                    tris .add(SpikeData(x + rnd.range(8f, 40f),    objY, attached))
                }
                hasStar -> stars.add(
                    Vector2(x + if (fromLeft) rnd.range(8f, 40f) else rnd.range(140f, 172f), objY)
                )
                hasTri -> tris.add(
                    SpikeData(x + if (fromLeft) rnd.range(152f, 184f) else rnd.range(8f, 40f), objY, attached)
                )
            }
        }

        // «Повзучі» шипи: на нерухомих платформах без зірки шип їздить по всій платформі
        var slidingLeft = recipe.slidingSpikes
        for (idx in tris.indices.shuffled(rnd)) {
            if (slidingLeft == 0) break
            val tri = tris[idx]
            val i = platIndexAt(tri.x, tri.y, xs, ys) ?: continue
            if (i in starPlats || kinds[i] == PlatformKind.MOVING) continue
            val travel = PLAT_SIZE.x - TRI_SIZE.x - 16f
            val range  = if (tri.x - xs[i] < PLAT_SIZE.x / 2f) travel else -travel
            tris[idx] = tri.copy(rangeX = range, speed = rnd.range(110f, 160f))
            slidingLeft--
        }

        // --- 3. Ворота на верхній платформі --------------------------------------------------
        val top  = PLAT_COUNT - 1
        val gate = Vector2(xs[top] + PLAT_SIZE.x / 2f, ys[top] + PLAT_SIZE.y + GATE_SIZE / 2f + 4f)

        // --- 4. Портали, чорні діри, лазери, астероїди -------------------------------------
        val obstacles = Obstacles(plats, tris, stars, gate)

        val portals = ArrayList<PortalData>()
        repeat(recipe.portals) { pickPortal(rnd, xs, ys, obstacles)?.let { portals.add(it); obstacles.portals.add(it) } }

        val holes = ArrayList<BlackHoleData>()
        repeat(recipe.holes) {
            pickHole(rnd, xs, ys, obstacles)?.let { p ->
                val hole = BlackHoleData(p.x, p.y, pullRadius = 270f, strength = rnd.range(5.0f, 6.5f))
                holes.add(hole); obstacles.holes.add(hole)
            }
        }

        val lasers = ArrayList<LaserData>()
        repeat(recipe.lasers) { pickLaser(rnd, xs, ys, obstacles)?.let { lasers.add(it); obstacles.lasers.add(it) } }

        val asteroids = if (recipe.asteroidInterval > 0f) AsteroidRainData(recipe.asteroidInterval) else null

        return LevelData(planet, plats, tris, stars, gate, portals, holes, lasers, asteroids)
    }

    /**
     * Висоти платформ. Кожен наступний розрив тримається в minGap..maxGap,
     * а межі підтискаються так, щоб верхня платформа гарантовано лягла
     * в yTopMin..yTopMax — рівень не «злипається» знизу і не вилазить за екран.
     */
    private fun buildHeights(rnd: Random, c: Curve): FloatArray {
        val ys = FloatArray(PLAT_COUNT)
        ys[0] = rnd.range(c.yFirstMin, c.yFirstMax)

        for (i in 1 until PLAT_COUNT) {
            val remaining = PLAT_COUNT - 1 - i
            val hi = minOf(c.maxGap, c.yTopMax - ys[i - 1] - remaining * c.minGap)
            val lo = minOf(maxOf(c.minGap, c.yTopMin - ys[i - 1] - remaining * c.maxGap), hi)
            ys[i] = ys[i - 1] + rnd.range(lo, hi)
        }
        return ys
    }

    /** X-координати з перевіркою досяжності та захистом від «пастки» м'яча. */
    private fun buildColumns(rnd: Random, ys: FloatArray, c: Curve): FloatArray {
        val xs = FloatArray(PLAT_COUNT)
        var prevCenter = BALL_CENTER_X

        for (i in 0 until PLAT_COUNT) {
            val minStep = if (i == 0) c.firstMinStep else c.minStep
            xs[i] = pickX(rnd, ys, xs, i, prevCenter, minStep, c.maxStep, c.noCeilings)
            prevCenter = xs[i] + PLAT_SIZE.x / 2f
        }
        return xs
    }

    private fun pickX(
        rnd: Random,
        ys: FloatArray,
        xs: FloatArray,
        index: Int,
        prevCenter: Float,
        minStep: Float,
        maxStep: Float,
        noCeilings: Boolean,
    ): Float {
        val y = ys[index]

        repeat(90) {
            val x = rnd.range(X_MIN, X_MAX)
            val step = abs(x + PLAT_SIZE.x / 2f - prevCenter)

            if (step in minStep..maxStep && isClear(x, y, ys, xs, index, noCeilings)) return x
        }
        // Без правила «стель» знайти місце легше — краще так, ніж запасний варіант
        if (noCeilings) repeat(60) {
            val x = rnd.range(X_MIN, X_MAX)
            val step = abs(x + PLAT_SIZE.x / 2f - prevCenter)
            if (step in minStep..maxStep && isClear(x, y, ys, xs, index, false)) return x
        }

        // Запасний варіант: протилежний бік екрана
        val fallback = if (prevCenter > FIELD_W / 2f) X_MIN + 40f else X_MAX - 40f
        return fallback.coerceIn(X_MIN, X_MAX)
    }

    /**
     * Платформи, що перекриваються по X, мусять бути рознесені по Y хоча б на [MIN_CLEAR_Y];
     * з [noCeilings] — на [CEILING_CLEAR_Y], причому перекриття рахується з запасом на м'яч.
     */
    private fun isClear(x: Float, y: Float, ys: FloatArray, xs: FloatArray, index: Int, noCeilings: Boolean): Boolean {
        for (i in 0 until index) {
            if (abs(x - xs[i]) < OVERLAP_X && abs(y - ys[i]) < MIN_CLEAR_Y) return false
            if (noCeilings && abs(x - xs[i]) < PLAT_SIZE.x + 70f && y - ys[i] < CEILING_CLEAR_Y) return false
        }
        return true
    }

    /**
     * Діапазон руху для платформи [i]: у крайніх положеннях вона не повинна
     * ані виходити за поле, ані потрапляти в «пастку» з іншою платформою.
     */
    private fun pickMovingRange(rnd: Random, xs: FloatArray, ys: FloatArray, ranges: FloatArray, i: Int): Float? {
        val amplitude = rnd.range(120f, 200f)
        val signs = if (rnd.nextBoolean()) floatArrayOf(1f, -1f) else floatArrayOf(-1f, 1f)

        for (sign in signs) {
            val range = amplitude * sign
            val far = xs[i] + range
            if (far < X_MIN || far > X_MAX) continue

            val ok = (0 until PLAT_COUNT).none { j ->
                j != i && abs(ys[i] - ys[j]) < MIN_CLEAR_Y && extentsOverlap(xs[i], range, xs[j], ranges[j])
            }
            if (ok) return range
        }
        return null
    }

    /** Чи перетинаються (з запасом 20 px) горизонтальні «сліди» двох платформ з урахуванням руху. */
    private fun extentsOverlap(xA: Float, rangeA: Float, xB: Float, rangeB: Float): Boolean {
        val aMin = xA + min(0f, rangeA);  val aMax = xA + PLAT_SIZE.x + max(0f, rangeA)
        val bMin = xB + min(0f, rangeB);  val bMax = xB + PLAT_SIZE.x + max(0f, rangeB)
        return aMin < bMax + 20f && bMin < aMax + 20f
    }

    private fun platIndexAt(objX: Float, objY: Float, xs: FloatArray, ys: FloatArray): Int? =
        (0 until PLAT_COUNT).firstOrNull { i ->
            objX >= xs[i] - 1f && objX <= xs[i] + PLAT_SIZE.x && abs(objY - (ys[i] + PLAT_SIZE.y - 1f)) < 2f
        }

    // -----------------------------------------------------------------------
    // Вільне місце для порталів / чорних дір / лазерів
    // -----------------------------------------------------------------------

    private class Obstacles(
        val plats: List<PlatformData>,
        val tris: List<SpikeData>,
        val stars: List<Vector2>,
        val gate: Vector2,
    ) {
        val portals = ArrayList<PortalData>()
        val holes   = ArrayList<BlackHoleData>()
        val lasers  = ArrayList<LaserData>()

        /** Відстань від точки до найближчої платформи (з урахуванням її руху), 0 якщо всередині. */
        fun platformDistance(px: Float, py: Float): Float {
            var best = Float.MAX_VALUE
            for (p in plats) {
                val minX = p.x + min(0f, p.rangeX)
                val maxX = p.x + PLAT_SIZE.x + max(0f, p.rangeX)
                val dx = max(0f, max(minX - px, px - maxX))
                val dy = max(0f, max(p.y - py, py - (p.y + PLAT_SIZE.y)))
                best = min(best, Vector2.len(dx, dy))
            }
            return best
        }

        fun isFree(px: Float, py: Float, platClear: Float, objClear: Float): Boolean {
            if (platformDistance(px, py) < platClear) return false
            if (stars.any { Vector2.dst(it.x + STAR_SIZE.x / 2f, it.y + STAR_SIZE.y / 2f, px, py) < objClear }) return false
            if (tris.any  { Vector2.dst(it.x + TRI_SIZE.x / 2f, it.y + TRI_SIZE.y / 2f, px, py) < objClear + abs(it.rangeX) }) return false
            if (Vector2.dst(gate.x, gate.y, px, py) < 320f) return false
            if (portals.any { Vector2.dst(it.ax, it.ay, px, py) < 220f || Vector2.dst(it.bx, it.by, px, py) < 220f }) return false
            if (holes.any   { Vector2.dst(it.x, it.y, px, py) < 420f }) return false
            if (lasers.any  { py in (it.y - 140f)..(it.y + 140f) && px in (it.x - 80f)..(it.x + it.w + 80f) }) return false
            // Стартова зона м'яча
            if (py < 520f && abs(px - BALL_CENTER_X) < 220f) return false
            return true
        }
    }

    /**
     * Портал: вихід висить над центром платформи 2 або 3 (м'яч з нього падає на неї),
     * вхід — у вільному просторі між першою та другою платформами.
     * Це скорочення шляху ціною пропущених зірок — вибір за гравцем.
     */
    private fun pickPortal(rnd: Random, xs: FloatArray, ys: FloatArray, obstacles: Obstacles): PortalData? {
        val t  = if (rnd.nextBoolean()) 2 else 3
        val bx = xs[t] + PLAT_SIZE.x / 2f
        val by = ys[t] + PLAT_SIZE.y + 150f

        repeat(80) {
            val ax = rnd.range(110f, FIELD_W - 110f)
            val ay = rnd.range(ys[0] - 30f, ys[1] + 80f)
            if (obstacles.isFree(ax, ay, platClear = 80f, objClear = 110f) && Vector2.dst(ax, ay, bx, by) > 400f) {
                return PortalData(ax, ay, bx, by)
            }
        }
        return null
    }

    private fun pickHole(rnd: Random, xs: FloatArray, ys: FloatArray, obstacles: Obstacles): Vector2? {
        repeat(120) {
            val x = rnd.range(150f, FIELD_W - 150f)
            val y = rnd.range(ys[0] + 60f, ys[3] + 100f)
            if (obstacles.isFree(x, y, platClear = 125f, objClear = 130f)) return Vector2(x, y)
        }
        return null
    }

    /**
     * Лазер перекриває шлях між двома сусідніми платформами: промінь на середині
     * висоти між ними, від центру нижньої до центру верхньої (з запасом), і не
     * торкається жодної платформи. Блимає — пройти можна, вгадавши момент.
     */
    private fun pickLaser(rnd: Random, xs: FloatArray, ys: FloatArray, obstacles: Obstacles): LaserData? {
        for (i in (0 until PLAT_COUNT - 1).shuffled(rnd)) {
            val bottom = ys[i] + PLAT_SIZE.y
            val topY   = ys[i + 1]
            if (topY - bottom < 210f) continue

            val y  = (bottom + topY) / 2f + rnd.range(-25f, 25f)
            val c1 = xs[i] + PLAT_SIZE.x / 2f
            val c2 = xs[i + 1] + PLAT_SIZE.x / 2f
            var x1 = (min(c1, c2) - 70f).coerceAtLeast(0f)
            var x2 = (max(c1, c2) + 70f).coerceAtMost(FIELD_W)
            if (x2 - x1 < 260f) { x1 = (x1 - 60f).coerceAtLeast(0f); x2 = (x2 + 60f).coerceAtMost(FIELD_W) }

            // Промінь не має проходити крізь платформи (з урахуванням їхнього руху)
            val hits = obstacles.plats.any { p ->
                val pMin = p.x + min(0f, p.rangeX);  val pMax = p.x + PLAT_SIZE.x + max(0f, p.rangeX)
                y + 40f > p.y && y - 40f < p.y + PLAT_SIZE.y && x1 < pMax && x2 > pMin
            }
            if (hits) continue
            if (obstacles.lasers.any { abs(it.y - y) < 260f }) continue
            if (obstacles.holes.any { abs(it.y - y) < 200f }) continue

            val period = rnd.range(2.4f, 3.2f)
            return LaserData(x1, y, x2 - x1, period, rnd.nextFloat())
        }
        return null
    }

    private fun Random.range(from: Float, to: Float): Float =
        if (to <= from) from else from + nextFloat() * (to - from)

    // =======================================================================
    // Туторіал (Lumina 1–2)
    // =======================================================================
    //
    // Шлях гравця: старт → зона A (стрибок + вбік) → зона B (стрибок + вбік) →
    // зона C (подвійний стрибок) → зона D з воротами. Правила розкладки:
    //  - м'яч на старті займає x 481..600 — над ним нічого немає (стрибок угору
    //    інакше б'є об низ платформи);
    //  - над кожною зоною посадки нічого ближче 600 px (висота стрибка 480 + діаметр
    //    м'яча), інакше стрибок із неї впирається у «стелю»;
    //  - зони посадки подвійні (дві платформи поруч), щоб будь-який тап убік приземлив м'яч.

    private fun tutorial(plats: List<Vector2>, tris: List<Vector2>, stars: List<Vector2>): LevelData {
        // Ворота — над серединою верхньої зони (дві останні платформи стоять поруч)
        val a = plats[plats.size - 2]
        val b = plats.last()
        return LevelData(
            planet = Planet.LUMINA,
            plats  = plats.map { PlatformData(it.x, it.y) },
            tris   = tris.map { SpikeData(it.x, it.y) },
            stars  = stars,
            gate   = Vector2((a.x + b.x + PLAT_SIZE.x) / 2f, b.y + PLAT_SIZE.y + GATE_SIZE / 2f + 4f),
        )
    }

    /** Логічний індекс платформи в туторіалі: усі зони посадки подвійні → пара = один індекс. */
    fun tutorialLogicalIndex(level: Int, platIndex: Int): Int = if (level <= 1) platIndex / 2 else platIndex

    // Кожна зона — пара платформ до самої стіни: переліт у стіну все одно закінчується
    // посадкою, а не падінням. Ворота над серединою верхньої зони.
    private val TUTORIAL: List<LevelData> = listOf(
        // Рівень 1: без шипів
        tutorial(
            plats = listOf(
                Vector2(640f, 430f), Vector2(830f, 430f),      // зона A: 640..1080
                Vector2(0f, 660f),   Vector2(190f, 660f),      // зона B: 0..440
                Vector2(640f, 1060f), Vector2(830f, 1060f),    // зона C: 640..1080 (602 px над A)
                Vector2(0f, 1400f),  Vector2(190f, 1400f),     // зона D: 0..440 (712 px над B), ворота
            ),
            tris  = emptyList(),
            stars = listOf(Vector2(900f, 457f), Vector2(300f, 687f), Vector2(760f, 1087f)),
        ),
        // Рівень 2: один шип біля стіни на третій зоні — гравець прилітає справа,
        // тож лишається 350 px безпечної посадки
        tutorial(
            plats = listOf(
                Vector2(0f, 430f),   Vector2(190f, 430f),      // зона A: 0..440
                Vector2(640f, 660f), Vector2(830f, 660f),      // зона B: 640..1080
                Vector2(0f, 1060f),  Vector2(190f, 1060f),     // зона C: 0..440 (602 px над A)
                Vector2(640f, 1400f), Vector2(830f, 1400f),    // зона D: 640..1080 (712 px над B), ворота
            ),
            tris  = listOf(Vector2(14f, 1087f)),
            stars = listOf(Vector2(200f, 457f), Vector2(760f, 687f), Vector2(330f, 1087f)),
        ),
    )
}
