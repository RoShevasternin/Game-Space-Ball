package com.rostislav.spaceball.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.rostislav.spaceball.game.GdxGame
import com.rostislav.spaceball.game.box2d.WorldUtil
import com.rostislav.spaceball.game.utils.TIME_ANIM_ALPHA
import com.rostislav.spaceball.game.utils.actor.animHide
import com.rostislav.spaceball.game.utils.actor.animShow
import com.rostislav.spaceball.game.utils.actor.setOnClickListener
import com.rostislav.spaceball.game.utils.advanced.AdvancedBox2dScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedStage
import com.rostislav.spaceball.game.utils.font.FontParameter
import com.rostislav.spaceball.game.utils.level.LevelGenerator
import com.rostislav.spaceball.game.utils.region
import kotlin.math.abs

/**
 * Вертикальний список усіх рівнів зі скролом та інерцією.
 *
 * Рівні відкриваються послідовно: спочатку доступний лише перший, кожна перемога
 * відкриває наступний. Закриті рівні затемнені й на тап не реагують (лише коротка
 * анімація «не можна»).
 *
 * Скрол зроблений вручну (один прозорий актор-перехоплювач зверху), а не через ScrollPane,
 * бо стандартний setOnClickListener проекту з'їдає touchDown і конфліктує зі ScrollPane —
 * рівень відкривався б просто від протягування пальцем.
 */
class SpaceLevelsScreen(override val game: GdxGame): AdvancedBox2dScreen(WorldUtil()) {

    companion object {
        private const val COLUMNS     = 3
        private const val CELL        = 300f
        private const val PLANET      = 210f
        private const val LIST_TOP    = 1740f
        private const val LIST_BOTTOM = 40f
        private const val SIDE_PAD    = 90f

        /** Поріг у пікселях: якщо палець проїхав більше — це скрол, а не тап. */
        private const val TAP_SLOP = 24f

        private const val FLING_DECAY = 4.5f
        private const val MIN_FLING   = 40f
    }

    private val params = FontParameter().setCharacters(FontParameter.CharType.NUMBERS).setSize(64)
    private val font   = fontGenerator_InterBold.generateFont(params)

    // Кольори відкритого / закритого рівня
    private val colorOpen        = Color.WHITE
    private val colorLocked      = Color(0.30f, 0.32f, 0.42f, 1f)
    private val labelColor       = Color.valueOf("C5D5F3")
    private val labelTintLocked  = Color(0.42f, 0.44f, 0.55f, 1f)

    private val back = Image(game.assetsAllUtil.BACK)

    private val rows          = (LevelGenerator.LEVEL_COUNT + COLUMNS - 1) / COLUMNS
    private val contentHeight = rows * CELL
    private val viewHeight    = LIST_TOP - LIST_BOTTOM
    private val maxOffset     = (contentHeight - viewHeight).coerceAtLeast(0f)

    /** Клітинки в координатах контенту — для попадання тапом і відсікання невидимих. */
    private val cells = ArrayList<Cell>(LevelGenerator.LEVEL_COUNT)

    private val content = object : Group() {
        override fun act(delta: Float) {
            super.act(delta)
            applyFling(delta)
            refreshLocksIfChanged()
            cullCells()
        }
    }

    private var offset     = 0f
    private var velocity   = 0f
    private var isDragging = false

    /** Прогрес вантажиться з DataStore асинхронно — стежимо, коли він приїде. */
    private var knownRevision = -1

    override fun show() {
        stageUI.root.animHide()
        setBackBackground(game.assetsLoaderUtil.backgrounds.random().region)
        super.show()
        stageUI.root.animShow(TIME_ANIM_ALPHA)
    }

    override fun AdvancedStage.addActorsOnStageUI() {
        addContent()
        addTouchCatcher()
        addBack()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun AdvancedStage.addContent() {
        content.setSize(1080f, contentHeight)
        addActor(content)

        repeat(LevelGenerator.LEVEL_COUNT) { index ->
            val row = index / COLUMNS
            val col = index % COLUMNS

            val cellX = SIDE_PAD + col * CELL
            // row 0 має бути зверху контенту
            val cellY = contentHeight - (row + 1) * CELL

            val planet = Image(game.assetsAllUtil.pList[index % 4]).apply {
                setBounds(cellX + (CELL - PLANET) / 2f, cellY + (CELL - PLANET) / 2f, PLANET, PLANET)
                setOrigin(Align.center)
            }
            val label = Label("${index + 1}", Label.LabelStyle(font, labelColor)).apply {
                setBounds(cellX, cellY + CELL / 2f - 40f, CELL, 80f)
                setAlignment(Align.center)
            }

            content.addActor(planet)
            content.addActor(label)
            cells.add(Cell(index, cellX, cellY, planet, label))
        }

        refreshLocksIfChanged()
        scrollToCurrentLevel()
    }

    /**
     * Прозорий актор поверх списку: ловить і скрол, і тап.
     * Так жоден дочірній лісенер не конфліктує з протягуванням.
     */
    private fun AdvancedStage.addTouchCatcher() {
        val catcher = Actor().apply { setBounds(0f, LIST_BOTTOM, 1080f, viewHeight) }
        addActor(catcher)

        catcher.addListener(object : InputListener() {
            private var startY   = 0f
            private var startOff = 0f
            private var dragged  = 0f
            private var lastY    = 0f

            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                startY     = y
                lastY      = y
                startOff   = offset
                dragged    = 0f
                velocity   = 0f
                isDragging = true
                return true
            }

            override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                val delta = y - startY
                dragged   = maxOf(dragged, abs(delta))

                // Палець вниз (y меншає) → показуємо наступні рівні
                offset = (startOff + delta).coerceIn(0f, maxOffset)
                applyOffset()

                velocity = (y - lastY) * 12f
                lastY    = y
            }

            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                isDragging = false

                if (dragged <= TAP_SLOP) {
                    velocity = 0f
                    cellAt(x, y)?.let { cell ->
                        if (game.levelUtil.isUnlocked(cell.index)) openLevel(cell.index)
                        else rejectLocked(cell)
                    }
                }
            }
        })
    }

    private fun AdvancedStage.addBack() {
        addActor(back)
        back.setBounds(25f, 1776f, 119f, 119f)
        back.setOnClickListener(game.soundUtil) {
            stageUI.root.animHide(TIME_ANIM_ALPHA) {
                game.navigationManager.back()
            }
        }
    }

    // ------------------------------------------------------------------------
    // Прогрес / замки
    // ------------------------------------------------------------------------
    private fun refreshLocksIfChanged() {
        val revision = game.levelUtil.revision
        if (revision == knownRevision) return
        knownRevision = revision

        cells.onEach { cell ->
            val unlocked = game.levelUtil.isUnlocked(cell.index)
            // Колір актора множиться на колір шрифту/текстури — тому просто притемнюємо
            cell.planet.color.set(if (unlocked) colorOpen else colorLocked)
            cell.label.color.set(if (unlocked) colorOpen else labelTintLocked)
        }
    }

    /** Коротка «відмова» на тап по закритому рівню. */
    private fun rejectLocked(cell: Cell) {
        game.soundUtil.apply { play(CLICK, 0.15f) }
        cell.planet.clearActions()
        cell.planet.addAction(
            Actions.sequence(
                Actions.scaleTo(0.9f, 0.9f, 0.07f),
                Actions.scaleTo(1f, 1f, 0.12f),
            )
        )
    }

    /** Стартова позиція списку — на рівні, до якого гравець дійшов. */
    private fun scrollToCurrentLevel() {
        val row    = game.levelUtil.maxUnlocked / COLUMNS
        val center = (LIST_TOP + LIST_BOTTOM) / 2f

        offset = (center - LIST_TOP + (row + 1) * CELL - CELL / 2f).coerceIn(0f, maxOffset)
        applyOffset()
    }

    // ------------------------------------------------------------------------
    // Scroll
    // ------------------------------------------------------------------------
    private fun applyOffset() {
        content.y = LIST_TOP - contentHeight + offset
    }

    private fun applyFling(delta: Float) {
        if (isDragging || abs(velocity) < MIN_FLING) {
            velocity = 0f
            return
        }
        offset = (offset + velocity * delta).coerceIn(0f, maxOffset)
        applyOffset()

        velocity -= velocity * FLING_DECAY * delta
        if (offset <= 0f || offset >= maxOffset) velocity = 0f
    }

    /** Ховаємо клітинки поза видимою зоною — і візуально чисто, і дешевше для рендеру. */
    private fun cullCells() {
        val base = content.y
        cells.onEach { cell ->
            val top     = base + cell.y + CELL
            val bottom  = base + cell.y
            val visible = top > LIST_BOTTOM && bottom < LIST_TOP
            cell.planet.isVisible = visible
            cell.label.isVisible  = visible
        }
    }

    /** [x], [y] — у координатах перехоплювача (0..1080 / 0..viewHeight). */
    private fun cellAt(x: Float, y: Float): Cell? {
        val stageY   = y + LIST_BOTTOM
        val contentY = stageY - content.y

        return cells.firstOrNull { cell ->
            x >= cell.x && x <= cell.x + CELL && contentY >= cell.y && contentY <= cell.y + CELL
        }
    }

    private fun openLevel(index: Int) {
        game.soundUtil.apply { play(CLICK, 0.25f) }
        stageUI.root.animHide(TIME_ANIM_ALPHA) {
            AbstractGameScreen.level = index
            game.navigationManager.navigate(
                AbstractGameScreen::class.java.name,
                SpaceLevelsScreen::class.java.name,
            )
        }
    }

    private data class Cell(
        val index : Int,
        val x     : Float,
        val y     : Float,
        val planet: Image,
        val label : Label,
    )
}