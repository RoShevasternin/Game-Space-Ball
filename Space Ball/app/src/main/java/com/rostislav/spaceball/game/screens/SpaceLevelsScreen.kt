package com.rostislav.spaceball.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.rostislav.spaceball.game.GdxGame
import com.rostislav.spaceball.game.actors.ui.StarRating
import com.rostislav.spaceball.game.actors.ui.StarShape
import com.rostislav.spaceball.game.effects.NeonPanel
import com.rostislav.spaceball.game.effects.StarfieldActor
import com.rostislav.spaceball.game.utils.TIME_ANIM_ALPHA
import com.rostislav.spaceball.game.utils.actor.animHide
import com.rostislav.spaceball.game.utils.actor.animShow
import com.rostislav.spaceball.game.utils.actor.setOnClickListener
import com.rostislav.spaceball.game.utils.advanced.AdvancedScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedStage
import com.rostislav.spaceball.game.utils.dataStore.LevelUtil
import com.rostislav.spaceball.game.utils.font.FontParameter
import com.rostislav.spaceball.game.utils.level.LevelGenerator
import com.rostislav.spaceball.game.utils.level.Planet
import com.rostislav.spaceball.game.utils.region
import kotlin.math.abs

/**
 * Вертикальний список рівнів, згрупований по планетах: скляна панель планети
 * (картинка, назва, механіка, зібрані зірки) і сітка 3×5 рівнів із рейтингом.
 * Поточний рівень підсвічується пульсуючим кільцем.
 *
 * Рівні відкриваються послідовно: спочатку доступний лише перший, кожна перемога
 * відкриває наступний. Закриті рівні затемнені й на тап не реагують (лише коротка
 * анімація «не можна»).
 *
 * Скрол зроблений вручну (один прозорий актор-перехоплювач зверху), а не через ScrollPane,
 * бо стандартний setOnClickListener проекту з'їдає touchDown і конфліктує зі ScrollPane —
 * рівень відкривався б просто від протягування пальцем.
 */
class SpaceLevelsScreen(override val game: GdxGame): AdvancedScreen() {

    companion object {
        private const val COLUMNS     = 3
        private const val CELL        = 300f
        private const val PLANET      = 210f
        private const val HEADER_H    = 330f
        private const val LIST_TOP    = 1740f
        private const val LIST_BOTTOM = 40f
        private const val SIDE_PAD    = 90f

        /** Поріг у пікселях: якщо палець проїхав більше — це скрол, а не тап. */
        private const val TAP_SLOP = 24f

        private const val FLING_DECAY = 4.5f
        private const val MIN_FLING   = 40f
    }

    private val fontNumber = fontGenerator_InterBold.generateFont(FontParameter().setCharacters(FontParameter.CharType.NUMBERS).setSize(60).setOutline(3.5f).setShadow())
    private val fontTitle  = fontGenerator_InterBold.generateFont(FontParameter().ui(56))
    private val fontSmall  = fontGenerator_InterBold.generateFont(FontParameter().ui(30))

    // Кольори відкритого / закритого рівня
    private val colorOpen        = Color.WHITE
    private val colorLocked      = Color(0.30f, 0.32f, 0.42f, 1f)
    private val labelColor       = Color.valueOf("F4F0FF")
    private val labelTintLocked  = Color(0.55f, 0.57f, 0.68f, 1f)

    private val back = Image(game.assetsAllUtil.BACK)

    private val rowsPerPlanet = Planet.LEVELS_PER_PLANET / COLUMNS
    private val planetBlockH  = HEADER_H + rowsPerPlanet * CELL
    private val contentHeight = Planet.entries.size * planetBlockH
    private val viewHeight    = LIST_TOP - LIST_BOTTOM
    private val maxOffset     = (contentHeight - viewHeight).coerceAtLeast(0f)

    /** Клітинки в координатах контенту — для попадання тапом і відсікання невидимих. */
    private val cells   = ArrayList<Cell>(LevelGenerator.LEVEL_COUNT)
    private val headers = ArrayList<Header>(Planet.entries.size)

    private val content = object : Group() {
        override fun act(delta: Float) {
            super.act(delta)
            applyIntroScroll(delta)
            applyFling(delta)
            refreshLocksIfChanged()
            cullCells()
        }

        /**
         * Список обрізається по видимій зоні — не наїжджає на верхню панель і низ екрана.
         * clipBegin викликається до applyTransform, тож прямокутник — у координатах сцени.
         */
        override fun draw(batch: Batch, parentAlpha: Float) {
            batch.flush()
            if (clipBegin(0f, LIST_BOTTOM, 1080f, viewHeight)) {
                super.draw(batch, parentAlpha)
                batch.flush()
                clipEnd()
            }
        }
    }

    private var offset     = 0f
    private var velocity   = 0f
    private var isDragging = false
    private var time       = 0f

    /**
     * Вступний автоскрол: список відкривається на останніх рівнях (низ) і сам плавно
     * їде до поточного — гравець за секунду бачить, скільки в грі рівнів.
     */
    private var introTime   = -1f
    private var introFrom   = 0f
    private var introTarget = 0f

    /** Прогрес вантажиться з DataStore асинхронно — стежимо, коли він приїде. */
    private var knownRevision = -1
    private var currentCell: Cell? = null

    override fun show() {
        stageUI.root.animHide()
        setBackBackground(game.assetsLoaderUtil.backgrounds[Planet.of(game.levelUtil.currentLevel).theme].region)
        animateBackground()
        super.show()
        stageUI.root.animShow(TIME_ANIM_ALPHA)
    }

    override fun AdvancedStage.addActorsOnStageUI() {
        addActor(StarfieldActor(drawerUtil, seed = 77L, count = 50))
        addContent()
        addTouchCatcher()
        addTopBar()
        addBack()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun AdvancedStage.addContent() {
        content.setSize(1080f, contentHeight)
        addActor(content)

        Planet.entries.forEachIndexed { pi, planet ->
            val blockTop = contentHeight - pi * planetBlockH

            // --- Заголовок планети: скляна панель ------------------------------------
            val headerY = blockTop - HEADER_H
            val panel = NeonPanel(
                drawerUtil.whiteRegion, Color.valueOf("1E1246"), Color.valueOf("0A0620"), planet.accent, planet.accent2,
            ).apply {
                radius = 44f; glow = 0.55f; sheen = 1f
                setBounds(SIDE_PAD - 40f, headerY + 20f, 1080f - 2f * SIDE_PAD + 80f, HEADER_H - 40f)
            }
            val planetImg = Image(game.assetsAllUtil.pList[planet.theme]).apply {
                setBounds(SIDE_PAD - 24f, headerY + (HEADER_H - PLANET) / 2f, PLANET, PLANET)
                setOrigin(Align.center)
                addAction(Actions.forever(Actions.sequence(
                    Actions.moveBy(0f, 10f, 1.6f + pi * 0.2f, Interpolation.sine),
                    Actions.moveBy(0f, -10f, 1.6f + pi * 0.2f, Interpolation.sine),
                )))
            }
            val title = Label(planet.title, Label.LabelStyle(fontTitle, colorOpen)).apply {
                setBounds(SIDE_PAD + PLANET + 4f, headerY + HEADER_H / 2f + 14f, 660f, 64f)
                setAlignment(Align.left)
            }
            val sub = Label(planet.subtitle, Label.LabelStyle(fontSmall, planet.accent)).apply {
                setBounds(SIDE_PAD + PLANET + 4f, headerY + HEADER_H / 2f - 30f, 660f, 36f)
                setAlignment(Align.left)
            }
            val stars = Label("", Label.LabelStyle(fontSmall, StarRating.GOLD)).apply {
                setBounds(SIDE_PAD + PLANET + 46f, headerY + HEADER_H / 2f - 74f, 660f, 36f)
                setAlignment(Align.left)
            }
            val starIcon = object : Actor() {
                override fun draw(batch: Batch, parentAlpha: Float) {
                    StarShape.fill(drawerUtil.drawer, x + 16f, y + 18f, 16f, Color(StarRating.GOLD).also { it.a *= parentAlpha * color.a })
                }
            }.apply { setBounds(SIDE_PAD + PLANET + 4f, headerY + HEADER_H / 2f - 74f, 36f, 36f) }

            content.addActor(panel)
            content.addActor(planetImg)
            content.addActor(title); content.addActor(sub); content.addActor(stars); content.addActor(starIcon)
            headers.add(Header(planet, panel, planetImg, title, sub, stars, starIcon, headerY))

            // --- Рівні ------------------------------------------------------------------
            for (k in 0 until Planet.LEVELS_PER_PLANET) {
                val index = planet.firstLevel + k
                val row = k / COLUMNS
                val col = k % COLUMNS

                val cellX = SIDE_PAD + col * CELL
                val cellY = headerY - (row + 1) * CELL

                val badge = Image(game.assetsAllUtil.pList[planet.theme]).apply {
                    setBounds(cellX + (CELL - 200f) / 2f, cellY + (CELL - 200f) / 2f + 14f, 200f, 200f)
                    setOrigin(Align.center)
                }
                val label = Label("${index + 1}", Label.LabelStyle(fontNumber, labelColor)).apply {
                    setBounds(cellX, cellY + CELL / 2f - 26f, CELL, 80f)
                    setAlignment(Align.center)
                }
                val rating = StarRating(drawerUtil, 0, 30f, gap = 8f).apply {
                    setPosition(cellX + (CELL - width) / 2f, cellY + 22f)
                }
                val glow = object : Actor() {
                    override fun draw(batch: Batch, parentAlpha: Float) {
                        val pulse = 0.5f + 0.5f * MathUtils.sin(time * 4f)
                        val cx = x + width / 2f; val cy = y + height / 2f
                        drawerUtil.additive {
                            val c = Color(planet.accent); c.a = (0.25f + 0.25f * pulse) * parentAlpha
                            filledCircle(cx, cy, 112f + 10f * pulse, c)
                            c.a = 0.6f * parentAlpha
                            setColor(c)
                            circle(cx, cy, 116f + 10f * pulse, 5f)
                        }
                        drawerUtil.drawer.setColor(Color.WHITE)
                    }
                }.apply { setBounds(badge.x, badge.y, badge.width, badge.height); isVisible = false }

                content.addActor(glow)
                content.addActor(badge)
                content.addActor(label)
                content.addActor(rating)
                cells.add(Cell(index, cellX, cellY, badge, label, rating, glow))
            }
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
                introTime  = -1f          // дотик перериває вступний автоскрол
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
                        if (game.levelUtil.isUnlocked(cell.index)) openLevel(cell)
                        else rejectLocked(cell)
                    }
                }
            }
        })
    }

    private fun AdvancedStage.addTopBar() {
        // Затемнення під верхньою панеллю, щоб список не наїжджав на кнопку «назад»
        addActor(object : Actor() {
            override fun draw(batch: Batch, parentAlpha: Float) {
                val c = Color(0.02f, 0.02f, 0.08f, 0.6f * parentAlpha)
                drawerUtil.drawer.filledRectangle(0f, LIST_TOP, 1080f, 1920f - LIST_TOP, c)
                c.set(Color.WHITE); c.a = 0.18f * parentAlpha
                drawerUtil.drawer.filledRectangle(0f, LIST_TOP, 1080f, 2f, c)
            }
        })
        val total = Label("", Label.LabelStyle(fontSmall, StarRating.GOLD)).apply {
            setBounds(0f, 1800f, 1080f, 40f); setAlignment(Align.center)
        }
        addActor(total)
        total.addAction(Actions.forever(Actions.sequence(Actions.run {
            total.setText("${game.levelUtil.totalRatingStars} / ${LevelGenerator.LEVEL_COUNT * 3} STARS")
        }, Actions.delay(0.5f))))
        val title = Label("LEVELS", Label.LabelStyle(fontTitle, colorOpen)).apply {
            setBounds(0f, 1836f, 1080f, 64f); setAlignment(Align.center)
        }
        addActor(title)
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

    override fun render(delta: Float) {
        time += delta
        super.render(delta)
    }

    // ------------------------------------------------------------------------
    // Прогрес / замки
    // ------------------------------------------------------------------------
    private fun refreshLocksIfChanged() {
        val revision = game.levelUtil.revision
        if (revision == knownRevision) return
        knownRevision = revision

        currentCell?.glow?.isVisible = false
        currentCell = cells.getOrNull(game.levelUtil.currentLevel)
        // Прогрес міг довантажитись уже під час автоскролу — перецілюємось
        if (introTime >= 0f) introTarget = offsetFor(focusLevel())

        cells.onEach { cell ->
            val unlocked = game.levelUtil.isUnlocked(cell.index)
            // Колір актора множиться на колір шрифту/текстури — тому просто притемнюємо
            cell.planet.color.set(if (unlocked) colorOpen else colorLocked)
            cell.label.color.set(if (unlocked) colorOpen else labelTintLocked)
            cell.rating.earned = game.levelUtil.rating(cell.index)
            cell.rating.color.a = if (unlocked) 1f else 0.4f
            cell.glow.isVisible = cell === currentCell
            cell.planet.clearActions()
            if (cell === currentCell) cell.planet.addAction(Actions.forever(Actions.sequence(
                Actions.scaleTo(1.07f, 1.07f, 0.7f, Interpolation.sine),
                Actions.scaleTo(1f, 1f, 0.7f, Interpolation.sine),
            ))) else cell.planet.setScale(1f)
        }
        headers.onEach { h ->
            val unlocked = game.levelUtil.isPlanetUnlocked(h.planet)
            val stars = game.levelUtil.planetStars(h.planet)
            h.stars.setText("$stars / ${Planet.LEVELS_PER_PLANET * 3}")
            h.planet_.color.set(if (unlocked) colorOpen else colorLocked)
            h.title.color.a = if (unlocked) 1f else 0.6f
            h.panel.glow    = if (unlocked) 0.55f else 0.15f
            if (unlocked) {
                h.sub.setText(h.planet.subtitle)
                h.sub.color.set(h.planet.accent)
            } else {
                // Що треба зробити, щоб відкрити планету
                val prev = Planet.entries[h.planet.ordinal - 1]
                val done = game.levelUtil.planetCompleted(prev).coerceAtMost(LevelUtil.PLANET_UNLOCK)
                h.sub.setText("COMPLETE $done/${LevelUtil.PLANET_UNLOCK} ${prev.title} LEVELS")
                h.sub.color.set(StarRating.GOLD)
            }
        }
    }

    /** Коротка «відмова» на тап по закритому рівню. */
    private fun rejectLocked(cell: Cell) {
        game.soundUtil.apply { play(LOCKED, 0.6f) }
        cell.planet.clearActions()
        cell.planet.addAction(
            Actions.sequence(
                Actions.scaleTo(0.9f, 0.9f, 0.07f),
                Actions.scaleTo(1f, 1f, 0.12f),
            )
        )
    }

    /** Рівень, на який націлюється список: останній зіграний за цей запуск або поточний. */
    private fun focusLevel(): Int =
        if (AbstractGameScreen.hasPlayed) AbstractGameScreen.level else game.levelUtil.currentLevel

    /** Зсув, за якого клітинка рівня [index] опиняється в центрі видимої зони. */
    private fun offsetFor(index: Int): Float {
        val cell = cells.getOrNull(index) ?: return maxOffset
        val center = (LIST_TOP + LIST_BOTTOM) / 2f
        // content.y + cell.y + CELL/2 == center
        return (center - cell.y - CELL / 2f - LIST_TOP + contentHeight).coerceIn(0f, maxOffset)
    }

    /** Старт: показуємо низ списку (останні рівні) і запускаємо автоскрол до потрібного. */
    private fun scrollToCurrentLevel() {
        introTarget = offsetFor(focusLevel())
        introFrom   = maxOffset
        offset      = introFrom
        introTime   = 0f
        applyOffset()
    }

    // ------------------------------------------------------------------------
    // Scroll
    // ------------------------------------------------------------------------
    private fun applyOffset() {
        content.y = LIST_TOP - contentHeight + offset
    }

    private fun applyIntroScroll(delta: Float) {
        if (introTime < 0f || isDragging) return
        introTime += delta

        // Пауза на fade-in екрана, тривалість залежить від відстані (≈1.1–1.9 с)
        val start    = 0.45f
        val distance = abs(introTarget - introFrom)
        val duration = 1.1f + 0.8f * (distance / maxOffset.coerceAtLeast(1f))
        val t = ((introTime - start) / duration).coerceIn(0f, 1f)

        offset = MathUtils.lerp(introFrom, introTarget, Interpolation.pow3Out.apply(t))
        applyOffset()
        if (t >= 1f) introTime = -1f
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
            cell.rating.isVisible = visible
            cell.glow.isVisible   = visible && cell === currentCell
        }
        headers.onEach { h ->
            val top     = base + h.y + HEADER_H
            val bottom  = base + h.y
            val visible = top > LIST_BOTTOM && bottom < LIST_TOP
            h.panel.isVisible = visible
            h.planet_.isVisible = visible; h.title.isVisible = visible; h.sub.isVisible = visible
            h.stars.isVisible = visible; h.icon.isVisible = visible
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

    private fun openLevel(cell: Cell) {
        game.soundUtil.apply { play(CLICK, 0.25f) }
        cell.planet.clearActions()
        cell.planet.addAction(Actions.sequence(Actions.scaleTo(1.25f, 1.25f, 0.12f, Interpolation.swingOut)))
        stageUI.root.animHide(TIME_ANIM_ALPHA) {
            AbstractGameScreen.level = cell.index
            AbstractGameScreen.isDaily = false
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
        val rating: StarRating,
        val glow  : Actor,
    )

    private data class Header(
        val planet : Planet,
        val panel  : NeonPanel,
        val planet_: Image,
        val title  : Label,
        val sub    : Label,
        val stars  : Label,
        val icon   : Actor,
        val y      : Float,
    )
}
