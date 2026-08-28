package com.rostislav.spaceball.game.utils.level

import com.badlogic.gdx.math.Vector2
import kotlin.math.abs
import kotlin.random.Random

/**
 * Генератор рівнів.
 *
 * Перші 4 рівні — оригінальні, зашиті вручну.
 * Решта генерується детерміновано: seed залежить лише від номера рівня,
 * тому рівень №17 завжди виглядає однаково (і в списку, і при перепроходженні).
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
 * Звідси:
 *   MAX_GAP_Y = 330 px — максимальний вертикальний розрив між платформами.
 *       З урахуванням товщини платформи (28) треба піднятись на 358 px < 480 px,
 *       тобто одного тапу UPUP вистачає із запасом ~25%.
 *   MAX_STEP_X = 620 px — горизонтальний крок, легко перекривається одним тапом вбік.
 *   MIN_CLEAR_Y = 300 px — якщо дві платформи перекриваються по X, між ними
 *       має бути щонайменше 300 px, інакше м'яч (119 px) не пролізе і застрягне.
 *       300 - 28 (платформа) - 70 (трикутник) = 202 px просвіту > 119 px. OK.
 */
object LevelGenerator {

    const val LEVEL_COUNT = 60

    const val PLAT_COUNT = 5
    const val TRI_COUNT  = 3
    const val STAR_COUNT = 3

    val PLAT_SIZE = Vector2(250f, 28f)
    val TRI_SIZE  = Vector2(58f, 70f)
    val STAR_SIZE = Vector2(70f, 67f)

    // --- Ігрове поле -------------------------------------------------------
    private const val FIELD_W = 1080f

    // Верх нижньої платформи (bDown) = 278, старт м'яча = (481, 382), діаметр 119.
    private const val BALL_CENTER_X = 540.5f

    // --- Обмеження генерації ----------------------------------------------
    private const val X_MIN = 0f
    private val      X_MAX  = FIELD_W - PLAT_SIZE.x            // 830

    private const val Y_FIRST_MIN = 600f
    private const val Y_FIRST_MAX = 700f                       // розрив від землі ≤ 422 px
    private const val MIN_GAP_Y   = 165f
    private const val MAX_GAP_Y   = 330f
    private const val Y_TOP_MIN   = 1480f
    private const val Y_TOP_MAX   = 1750f

    private const val MAX_STEP_X       = 620f
    private const val MIN_STEP_X       = 140f
    private const val FIRST_MIN_STEP_X = 200f                  // щоб перша платформа не висіла над м'ячем
    private const val MIN_CLEAR_Y      = 300f
    private val      OVERLAP_X         = PLAT_SIZE.x + 20f     // 270

    // Зміщення об'єктів від краю платформи підібрані так, щоб вільна зона
    // для приземлення м'яча (119 px) завжди була >= 147 px:
    //   трикутник ліворуч  8..40   → вільно праворуч 250 - 98  = 152
    //   трикутник праворуч 152..184 → вільно ліворуч            = 152

    data class LevelData(
        val plats: List<Vector2>,
        val tris : List<Vector2>,
        val stars: List<Vector2>,
    )

    // =======================================================================
    // Public
    // =======================================================================

    fun get(level: Int): LevelData {
        val safe = level.coerceIn(0, LEVEL_COUNT - 1)
        return HANDMADE.getOrNull(safe) ?: generate(safe)
    }

    // =======================================================================
    // Генерація
    // =======================================================================

    private fun generate(level: Int): LevelData {
        val rnd = Random(level * 7919L + 104729L)

        val ys = buildHeights(rnd)
        val xs = buildColumns(rnd, ys)

        val starPlats = (0 until PLAT_COUNT).shuffled(rnd).take(STAR_COUNT).toSet()
        val triPlats  = (0 until PLAT_COUNT).shuffled(rnd).take(TRI_COUNT).toSet()

        val plats = ArrayList<Vector2>(PLAT_COUNT)
        val tris  = ArrayList<Vector2>(TRI_COUNT)
        val stars = ArrayList<Vector2>(STAR_COUNT)

        var prevCenter = BALL_CENTER_X

        for (i in 0 until PLAT_COUNT) {
            val x = xs[i]
            val y = ys[i]
            plats.add(Vector2(x, y))

            val center = x + PLAT_SIZE.x / 2f
            // З якого боку гравець підлітає до платформи — туди й ставимо зірку,
            // а трикутник на протилежний край. Так рівень завжди чесний.
            val fromLeft = prevCenter <= center
            prevCenter = center

            val objY = y + PLAT_SIZE.y - 1f
            val hasStar = i in starPlats
            val hasTri  = i in triPlats

            when {
                hasStar && hasTri -> if (fromLeft) {
                    stars.add(Vector2(x + rnd.range(8f, 40f),   objY))
                    tris .add(Vector2(x + rnd.range(152f, 184f), objY))
                } else {
                    stars.add(Vector2(x + rnd.range(140f, 172f), objY))
                    tris .add(Vector2(x + rnd.range(8f, 40f),    objY))
                }
                hasStar -> stars.add(
                    Vector2(x + if (fromLeft) rnd.range(8f, 40f) else rnd.range(140f, 172f), objY)
                )
                hasTri -> tris.add(
                    Vector2(x + if (fromLeft) rnd.range(152f, 184f) else rnd.range(8f, 40f), objY)
                )
            }
        }

        return LevelData(plats, tris, stars)
    }

    /**
     * Висоти платформ. Кожен наступний розрив тримається в [MIN_GAP_Y]..[MAX_GAP_Y],
     * а межі підтискаються так, щоб верхня платформа гарантовано лягла
     * в [Y_TOP_MIN]..[Y_TOP_MAX] — рівень не «злипається» знизу і не вилазить за екран.
     */
    private fun buildHeights(rnd: Random): FloatArray {
        val ys = FloatArray(PLAT_COUNT)
        ys[0] = rnd.range(Y_FIRST_MIN, Y_FIRST_MAX)

        for (i in 1 until PLAT_COUNT) {
            val remaining = PLAT_COUNT - 1 - i
            val hi = minOf(MAX_GAP_Y, Y_TOP_MAX - ys[i - 1] - remaining * MIN_GAP_Y)
            val lo = minOf(maxOf(MIN_GAP_Y, Y_TOP_MIN - ys[i - 1] - remaining * MAX_GAP_Y), hi)
            ys[i] = ys[i - 1] + rnd.range(lo, hi)
        }
        return ys
    }

    /** X-координати з перевіркою досяжності та захистом від «пастки» м'яча. */
    private fun buildColumns(rnd: Random, ys: FloatArray): FloatArray {
        val xs = FloatArray(PLAT_COUNT)
        var prevCenter = BALL_CENTER_X

        for (i in 0 until PLAT_COUNT) {
            val minStep = if (i == 0) FIRST_MIN_STEP_X else MIN_STEP_X
            xs[i] = pickX(rnd, ys, xs, i, prevCenter, minStep)
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
    ): Float {
        val y = ys[index]

        repeat(60) {
            val x = rnd.range(X_MIN, X_MAX)
            val step = abs(x + PLAT_SIZE.x / 2f - prevCenter)

            if (step in minStep..MAX_STEP_X && isClear(x, y, ys, xs, index)) return x
        }

        // Запасний варіант: протилежний бік екрана
        val fallback = if (prevCenter > FIELD_W / 2f) X_MIN + 40f else X_MAX - 40f
        return fallback.coerceIn(X_MIN, X_MAX)
    }

    /** Платформи, що перекриваються по X, мусять бути рознесені по Y хоча б на [MIN_CLEAR_Y]. */
    private fun isClear(x: Float, y: Float, ys: FloatArray, xs: FloatArray, index: Int): Boolean {
        for (i in 0 until index) {
            if (abs(x - xs[i]) < OVERLAP_X && abs(y - ys[i]) < MIN_CLEAR_Y) return false
        }
        return true
    }

    private fun Random.range(from: Float, to: Float): Float =
        if (to <= from) from else from + nextFloat() * (to - from)

    // =======================================================================
    // Оригінальні 4 рівні
    // =======================================================================

    private val HANDMADE: List<LevelData> = listOf(
        LevelData(
            plats = listOf(
                Vector2(812f, 671f), Vector2(206f, 847f), Vector2(687f, 1152f),
                Vector2(96f, 1473f), Vector2(777f, 1683f),
            ),
            tris = listOf(
                Vector2(879f, 698f), Vector2(243f, 874f), Vector2(271f, 1500f),
            ),
            stars = listOf(
                Vector2(972f, 698f), Vector2(145f, 1499f), Vector2(920f, 1710f),
            ),
        ),
        LevelData(
            plats = listOf(
                Vector2(684f, 765f), Vector2(96f, 960f), Vector2(415f, 1152f),
                Vector2(739f, 1410f), Vector2(58f, 1588f),
            ),
            tris = listOf(
                Vector2(435f, 1178f), Vector2(835f, 1436f), Vector2(205f, 1614f),
            ),
            stars = listOf(
                Vector2(192f, 1000f), Vector2(918f, 1439f), Vector2(86f, 1615f),
            ),
        ),
        LevelData(
            plats = listOf(
                Vector2(738f, 874f), Vector2(108f, 1149f), Vector2(772f, 1191f),
                Vector2(314f, 1382f), Vector2(590f, 1648f),
            ),
            tris = listOf(
                Vector2(164f, 1173f), Vector2(805f, 1217f), Vector2(603f, 1673f),
            ),
            stars = listOf(
                Vector2(257f, 1176f), Vector2(908f, 1220f), Vector2(711f, 1678f),
            ),
        ),
        LevelData(
            plats = listOf(
                Vector2(45f, 732f), Vector2(471f, 1011f), Vector2(16f, 1311f),
                Vector2(778f, 1314f), Vector2(612f, 1744f),
            ),
            tris = listOf(
                Vector2(571f, 1036f), Vector2(146f, 1338f), Vector2(749f, 1770f),
            ),
            stars = listOf(
                Vector2(28f, 1341f), Vector2(865f, 1341f), Vector2(613f, 1771f),
            ),
        ),
    )
}