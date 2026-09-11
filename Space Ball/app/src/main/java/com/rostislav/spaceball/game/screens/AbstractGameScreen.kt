package com.rostislav.spaceball.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.rostislav.spaceball.game.GdxGame
import com.rostislav.spaceball.game.actors.button.AButton
import com.rostislav.spaceball.game.actors.ui.HintBubble
import com.rostislav.spaceball.game.actors.ui.PillButton
import com.rostislav.spaceball.game.actors.ui.StarRating
import com.rostislav.spaceball.game.box2d.AbstractBody
import com.rostislav.spaceball.game.box2d.BodyId
import com.rostislav.spaceball.game.box2d.WorldUtil
import com.rostislav.spaceball.game.box2d.bodies.BAsteroid
import com.rostislav.spaceball.game.box2d.bodies.BBall
import com.rostislav.spaceball.game.box2d.bodies.BBlackHole
import com.rostislav.spaceball.game.box2d.bodies.BGate
import com.rostislav.spaceball.game.box2d.bodies.BHorizontal
import com.rostislav.spaceball.game.box2d.bodies.BLaser
import com.rostislav.spaceball.game.box2d.bodies.BPlat
import com.rostislav.spaceball.game.box2d.bodies.BPortal
import com.rostislav.spaceball.game.box2d.bodies.BStar
import com.rostislav.spaceball.game.box2d.bodies.BTriangle
import com.rostislav.spaceball.game.box2d.bodiesGroup.BGBorders
import com.rostislav.spaceball.game.box2d.destroyAll
import com.rostislav.spaceball.game.effects.CameraShake
import com.rostislav.spaceball.game.effects.FxLayer
import com.rostislav.spaceball.game.effects.FxSystem
import com.rostislav.spaceball.game.effects.NeonPanel
import com.rostislav.spaceball.game.effects.StarfieldActor
import com.rostislav.spaceball.game.utils.DebugFlags
import com.rostislav.spaceball.game.utils.TIME_ANIM_ALPHA
import com.rostislav.spaceball.game.utils.actor.animHide
import com.rostislav.spaceball.game.utils.actor.animShow
import com.rostislav.spaceball.game.utils.actor.setOnClickListener
import com.rostislav.spaceball.game.utils.advanced.AdvancedBox2dScreen
import com.rostislav.spaceball.game.utils.advanced.AdvancedStage
import com.rostislav.spaceball.game.utils.dataStore.DailyUtil
import com.rostislav.spaceball.game.utils.font.FontParameter
import com.rostislav.spaceball.game.utils.level.LevelGenerator
import com.rostislav.spaceball.game.utils.level.Planet
import com.rostislav.spaceball.game.utils.level.PlatformKind
import com.rostislav.spaceball.game.utils.region
import com.rostislav.spaceball.game.utils.runGDX
import com.rostislav.spaceball.game.utils.toB2
import com.rostislav.spaceball.game.utils.toUI
import com.rostislav.spaceball.services.ads.AdPolicy
import com.rostislav.spaceball.util.log
import kotlin.math.abs

/**
 * Ігровий екран.
 *
 * Керування: з платформи стрибати можна скільки завгодно, у повітрі — ще [MAX_AIR_JUMPS] раз.
 * Мета: дістатись варп-воріт на верхній платформі; зірки — необов'язкові, це рейтинг 0..3.
 * Смерть → «Continue» за rewarded-рекламу (раз на спробу) або рестарт.
 * Перші два рівні ведуть гравця покроковими підказками ([Tutorial]).
 */
class AbstractGameScreen(override val game: GdxGame): AdvancedBox2dScreen(WorldUtil()) {

    companion object {
        var level: Int = 0
        /** Щоденний рівень замість [level]. */
        var isDaily = false
        /** Чи грали звичайний рівень за цей запуск — тоді список рівнів відкривається на [level]. */
        var hasPlayed = false
        /** Результат останнього пройденого рівня — читає екран перемоги. */
        var lastResult: LevelResult? = null

        const val MAX_AIR_JUMPS     = 1
        private const val COYOTE_TIME       = 0.10f
        private const val TRAMPOLINE_VY     = 8.6f
        private const val INVULNERABLE_TIME = 2.5f
        private const val PORTAL_COOLDOWN   = 0.9f
        private const val MAX_ASTEROIDS     = 6
        private const val ASTEROID_WARNING  = 0.8f
        private const val MAX_SPEED_X       = 9f
        private const val MAX_SPEED_Y       = 9.5f
        /**
         * Гальмування на платформі: м'яч зупиняється за ~0.5 с після приземлення,
         * а не котиться з розгону за край. У повітрі не діє — дальність стрибків та сама.
         */
        private const val GROUND_DAMPING    = 4f
    }

    data class LevelResult(val level: Int, val stars: Int, val isDaily: Boolean, val bonusStars: Int, val isNewBest: Boolean)

    private val levelData = if (isDaily) LevelGenerator.daily(DailyUtil.todayEpochDay()) else LevelGenerator.get(level)
    private val planet: Planet = levelData.planet
    private val theme = planet.theme
    private val skin  = game.skinUtil.selected

    private val fx    = FxSystem(drawerUtil)
    private val shake = CameraShake()

    // Fonts
    private val fontTitle = fontGenerator_InterBold.generateFont(FontParameter().ui(46))
    private val fontSmall = fontGenerator_InterBold.generateFont(FontParameter().ui(28))
    private val fontBig   = fontGenerator_InterBold.generateFont(FontParameter().ui(66))
    private val fontBtn   = fontGenerator_InterBold.generateFont(FontParameter().ui(44))
    private val fontHint  = fontGenerator_InterBold.generateFont(FontParameter().ui(34))
    private val fontPop   = fontGenerator_InterBold.generateFont(FontParameter().ui(40))

    // Actor
    private val aUp    = AButton(this, AButton.Static.Type.UP)
    private val aLeft  = AButton(this, AButton.Static.Type.LEFT)
    private val aRight = AButton(this, AButton.Static.Type.RIGHT)
    private val aUpUp  = AButton(this, AButton.Static.Type.UPUP)
    private val back   = Image(game.assetsAllUtil.BACK)
    private val hud    = Group()
    private val starHud = StarRating(drawerUtil, 0, 44f)
    private val flash  = object : Actor() {
        override fun draw(batch: Batch, parentAlpha: Float) {
            if (color.a <= 0.005f) return
            tmpColor.set(1f, 1f, 1f, color.a * parentAlpha)
            drawerUtil.drawer.filledRectangle(-60f, -60f, WIDTH + 120f, HEIGHT + 120f, tmpColor)
        }
    }.apply { color.a = 0f; touchable = Touchable.disabled }

    // BodyGroup
    private val bgBorders = BGBorders(this)

    // Body
    private val bDown = BHorizontal(this)
    private val bBall = BBall(this, skin)
    private val bPlatList  = levelData.plats.map { BPlat(this, it, planet, fx) }
    private val bTriList   = levelData.tris.map { BTriangle(this, it, theme) }
    private val bStaList   = levelData.stars.map { BStar(this, theme) }
    private val bPortals   = levelData.portals.flatMap { listOf(BPortal(this, planet.accent), BPortal(this, planet.accent)) }
    private val bHoles     = levelData.blackHoles.map { BBlackHole(this, it, fx, planet.accent) }
    private val bLasers    = levelData.lasers.map { BLaser(this, it, planet.accent) }
    private val bGate      = BGate(this, planet.accent)
    private val bAsteroids = ArrayList<BAsteroid>()

    // State
    private var levelTime        = 0f
    private var isDead           = false
    private var isWon            = false
    private var continueUsed     = false
    private var starsCollected   = 0
    private var airJumpsLeft     = MAX_AIR_JUMPS
    private var lastGroundTime   = -10f
    private var invulnerableTime = 0f
    private var portalCooldown   = 0f
    private val groundBodies     = HashSet<AbstractBody>()
    private val safePos          = Vector2(LevelGenerator.BALL_START_X + 59.5f, LevelGenerator.BALL_START_Y + 59.5f)
    private val tmp              = Vector2()
    private val tmpColor         = Color()

    // Asteroids
    private var asteroidTimer = 2.0f
    private var asteroidWarnX = -1f

    private val tutorial = Tutorial()

    private val isGrounded get() = groundBodies.isNotEmpty() || (levelTime - lastGroundTime) < COYOTE_TIME
    private val ballCenter: Vector2 get() = bBall.body?.let { tmp.set(it.worldCenter).toUI } ?: tmp.set(safePos)

    override fun show() {
        if (!isDaily) hasPlayed = true
        stageUI.root.animHide()
        setBackBackground(game.assetsLoaderUtil.backgrounds[theme].region)
        worldUtil.world.gravity = Vector2(0f, levelData.gravity)
        super.show()
        stageUI.root.animShow(TIME_ANIM_ALPHA)
        showIntroBanner()
    }

    override fun AdvancedStage.addActorsOnStageUI() {
        addActor(StarfieldActor(drawerUtil, seed = level * 977L + 11L, tint = planet.accent))

        createBG_Borders()
        createB_Down()
        createB_PlatList()
        createB_Lasers()
        createB_Portals()
        createB_Holes()
        createB_Gate()
        createB_StaList()
        createB_TriList()

        addActor(FxLayer(fx, front = false))
        createB_Ball()
        addActor(FxLayer(fx, front = true))
        addActor(flash)

        addHud()
        addButtons()
        addBack()
        addActor(tutorial.bubble)

        if (DebugFlags.autoWin(game.activity)) addAction(Actions.delay(2f, Actions.run {
            if (DebugFlags.shots) starsCollected = 3
            win(bGate)
        }))
        DebugFlags.freezeAt(game.activity).takeIf { it >= 0f }?.let { t ->
            addAction(Actions.delay(1.5f + t, Actions.run { isPauseWorld = true; fx.frozen = true }))
        }
        if (DebugFlags.autoDie(game.activity)) addAction(Actions.delay(2f, Actions.run { die(BodyId.TRI) }))
        DebugFlags.playScript(game.activity).forEach { (key, t) ->
            addAction(Actions.delay(1.5f + t, Actions.run {
                val c = ballCenter
                log("play $key@$t  ball=(${c.x.toInt()}, ${c.y.toInt()}) grounded=$isGrounded air=$airJumpsLeft")
                when (key) {
                    'U' -> jump(5f); 'D' -> jump(10f); 'L' -> push(-4f); 'R' -> push(4f)
                }
            }))
        }
    }

    override fun render(delta: Float) {
        if (!isPauseWorld) update(delta)

        shake.update(delta)
        stageUI.camera.position.set(WIDTH / 2f + shake.offset.x, HEIGHT / 2f + shake.offset.y, 0f)

        super.render(delta)
    }

    override fun dispose() {
        listOf(bgBorders).destroyAll()
        super.dispose()
    }

    // ------------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------------
    private fun update(delta: Float) {
        levelTime += delta
        if (portalCooldown > 0f) portalCooldown -= delta
        if (invulnerableTime > 0f) {
            invulnerableTime -= delta
            if (invulnerableTime <= 0f) bBall.ballActor.invulnerable = false
        }

        bBall.body?.let { b ->
            // Обмеження швидкості — щоб батут + стрибок не вистрілював у стелю
            val v = b.linearVelocity
            var vx = v.x; var vy = v.y
            if (abs(vx) > MAX_SPEED_X) vx = MAX_SPEED_X * Math.signum(vx)
            if (vy > MAX_SPEED_Y) vy = MAX_SPEED_Y

            // Гальмування на землі — відносно швидкості платформи, щоб рухома несла м'яч.
            // Слиз навмисно не гальмує.
            val ground = groundBodies.firstOrNull()
            if (ground != null && (ground as? BPlat)?.kind != PlatformKind.SLIME) {
                val k = (1f - GROUND_DAMPING * delta).coerceAtLeast(0f)
                val pvx = ground.body?.linearVelocity?.x ?: 0f
                vx = pvx + (vx - pvx) * k
                b.angularVelocity = b.angularVelocity * k
            }
            if (vx != v.x || vy != v.y) b.setLinearVelocity(vx, vy)

            val c = ballCenter
            fx.trailColor.set(bBall.ballActor.glowColor)
            fx.trailPush(c.x, c.y, 59.5f)
        }

        // Платформи, що зникли з-під м'яча, більше не «земля»
        groundBodies.removeAll { it.body == null || it.body?.isActive == false }

        val dim = !isGrounded && airJumpsLeft <= 0
        aUp.color.a   = if (dim) 0.45f else 1f
        aUpUp.color.a = if (dim) 0.45f else 1f

        updateAsteroids(delta)
        tutorial.update(delta)
    }

    // ------------------------------------------------------------------------
    // Керування
    // ------------------------------------------------------------------------
    private fun AdvancedStage.addButtons() {
        addActors(aUp, aUpUp, aLeft, aRight)
        aUp.apply {
            setBounds(139f, 43f, 156f, 165f)
            touchDownBlock = { _, _ -> jump(5f) }
        }
        aUpUp.apply {
            setBounds(321f, 43f, 156f, 165f)
            touchDownBlock = { _, _ -> jump(10f) }
        }
        aLeft.apply {
            setBounds(669f, 43f, 156f, 165f)
            touchDownBlock = { _, _ -> push(-4f) }
        }
        aRight.apply {
            setBounds(851f, 43f, 156f, 165f)
            touchDownBlock = { _, _ -> push(4f) }
        }
    }

    private fun push(impulseX: Float) {
        if (isDead || isWon || isPauseWorld) return
        bBall.body?.apply { applyLinearImpulse(Vector2(impulseX, 0f), worldCenter, true) }
        tutorial.onMove()
    }

    private fun jump(impulse: Float) {
        if (isDead || isWon || isPauseWorld) return
        val b = bBall.body ?: return

        val grounded = isGrounded
        if (!grounded) {
            if (airJumpsLeft <= 0) {
                game.soundUtil.apply { play(TICK, 0.3f, 0.6f) }
                return
            }
            airJumpsLeft--
        }

        // Стрибок задає вертикальну швидкість з нуля: падіння не «з'їдає» його,
        // а ранній подвійний стрибок не складається з першим у політ до стелі
        val v = b.linearVelocity
        b.setLinearVelocity(v.x, 0f)
        b.applyLinearImpulse(Vector2(0f, impulse), b.worldCenter, true)
        bBall.ballActor.stretch()

        val c = ballCenter
        if (grounded) {
            game.soundUtil.apply { play(JUMP, 0.7f, if (impulse > 7f) 1f else 1.25f) }
            fx.dust(c.x, c.y - 56f, planet.accent, count = 12, spread = 75f)
            fx.ring(c.x, c.y - 52f, planet.accent, size = 26f, life = 0.3f)
        } else {
            game.soundUtil.apply { play(JUMP2, 0.8f) }
            fx.ring(c.x, c.y - 30f, bBall.ballActor.glowColor, size = 34f, life = 0.35f)
            fx.burst(c.x, c.y - 20f, bBall.ballActor.glowColor, 8, speedMin = 80f, speedMax = 220f, sizeMin = 2f, sizeMax = 4f, life = 0.4f, gravity = -300f)
        }
        lastGroundTime = -10f
        tutorial.onJump(air = !grounded)
    }

    private fun AdvancedStage.addBack() {
        addActor(back)
        back.setBounds(25f, 1776f, 119f, 119f)
        back.setOnClickListener(game.soundUtil) { leave() }
    }

    private fun leave() {
        stageUI.root.animHide(TIME_ANIM_ALPHA) { game.navigationManager.back() }
    }

    override fun keyDown(keycode: Int): Boolean {
        if (keycode == com.badlogic.gdx.Input.Keys.BACK) { leave(); return true }
        return super.keyDown(keycode)
    }

    // ------------------------------------------------------------------------
    // HUD
    // ------------------------------------------------------------------------
    private fun AdvancedStage.addHud() {
        addActor(hud)
        hud.setSize(WIDTH, HEIGHT)
        hud.touchable = Touchable.disabled

        val title = if (isDaily) "DAILY CHALLENGE" else "LEVEL ${level + 1}"
        val titleLbl = Label(title, Label.LabelStyle(fontTitle, Color.valueOf("F4F0FF"))).apply {
            setBounds(0f, 1820f, WIDTH, 60f); setAlignment(Align.center)
        }
        val planetLbl = Label(planet.title, Label.LabelStyle(fontSmall, planet.accent)).apply {
            setBounds(0f, 1786f, WIDTH, 34f); setAlignment(Align.center)
        }
        hud.addActor(titleLbl)
        hud.addActor(planetLbl)

        starHud.setPosition(WIDTH - 40f - starHud.width, 1815f)
        hud.addActor(starHud)

        // Попередження про астероїд + вказівник туторіалу
        hud.addActor(object : Actor() {
            override fun draw(batch: Batch, parentAlpha: Float) {
                tutorial.drawPointer(parentAlpha)
                if (asteroidWarnX < 0f) return
                val blink = 0.5f + 0.5f * MathUtils.sin(levelTime * 25f)
                val x = asteroidWarnX
                tmpColor.set(1f, 0.35f, 0.3f, (0.5f + 0.5f * blink) * parentAlpha)
                drawerUtil.additive {
                    tmpColor.a = 0.35f * blink * parentAlpha
                    filledCircle(x, 1880f, 34f, tmpColor)
                }
                tmpColor.a = (0.6f + 0.4f * blink) * parentAlpha
                drawerUtil.drawer.filledTriangle(x - 22f, 1905f, x + 22f, 1905f, x, 1862f, tmpColor)
                drawerUtil.drawer.setColor(Color.WHITE)
            }
        })

        if (isDaily) {
            val bonusLbl = Label(
                if (game.dailyUtil.isDoneToday()) "BONUS CLAIMED" else "BONUS +${DailyUtil.BONUS_STARS} STARS",
                Label.LabelStyle(fontSmall, StarRating.GOLD)
            ).apply { setBounds(0f, 1752f, WIDTH, 34f); setAlignment(Align.center) }
            hud.addActor(bonusLbl)
        }
    }

    /** Банер «LEVEL N» вилітає на старті, тримається мить і зникає. */
    private fun showIntroBanner() {
        val title = if (isDaily) "DAILY CHALLENGE" else "LEVEL ${level + 1}"
        val big = Label(title, Label.LabelStyle(fontBig, Color.valueOf("F4F0FF"))).apply {
            setBounds(0f, 1120f, WIDTH, 90f); setAlignment(Align.center)
            setOrigin(Align.center); setScale(1.8f); color.a = 0f
            touchable = Touchable.disabled
        }
        val subText = when {
            isDaily -> planet.title
            level <= 1 -> "TUTORIAL"
            LevelGenerator.introHint(level) != null -> "NEW: " + planet.subtitle
            else -> planet.title
        }
        val sub = Label(subText, Label.LabelStyle(fontSmall, planet.accent)).apply {
            setBounds(0f, 1070f, WIDTH, 40f); setAlignment(Align.center)
            color.a = 0f; touchable = Touchable.disabled
        }
        stageUI.addActor(big); stageUI.addActor(sub)
        big.addAction(Actions.sequence(
            Actions.parallel(Actions.fadeIn(0.25f), Actions.scaleTo(1f, 1f, 0.45f, Interpolation.swingOut)),
            Actions.delay(0.9f),
            Actions.parallel(Actions.fadeOut(0.35f), Actions.moveBy(0f, 90f, 0.35f, Interpolation.pow2In)),
            Actions.removeActor(),
        ))
        sub.addAction(Actions.sequence(
            Actions.delay(0.2f), Actions.fadeIn(0.25f), Actions.delay(0.85f),
            Actions.parallel(Actions.fadeOut(0.3f), Actions.moveBy(0f, 60f, 0.3f, Interpolation.pow2In)),
            Actions.removeActor(),
        ))
        game.soundUtil.apply { play(PORTAL, 0.35f, 0.8f) }
        fx.ring(WIDTH / 2f, 1165f, planet.accent, size = 120f, life = 0.7f)

        stageUI.addAction(Actions.delay(1.3f, Actions.run { tutorial.start() }))
    }

    /** «+1» над зібраною зіркою. */
    private fun popup(text: String, x: Float, y: Float, color: Color) {
        val lbl = Label(text, Label.LabelStyle(fontPop, color)).apply {
            pack()
            setPosition(x - width / 2f, y)
            setOrigin(Align.center); setScale(0.5f)
            touchable = Touchable.disabled
        }
        stageUI.addActor(lbl)
        lbl.addAction(Actions.sequence(
            Actions.parallel(
                Actions.scaleTo(1.15f, 1.15f, 0.2f, Interpolation.swingOut),
                Actions.moveBy(0f, 90f, 0.8f, Interpolation.pow2Out),
                Actions.sequence(Actions.delay(0.45f), Actions.fadeOut(0.35f)),
            ),
            Actions.removeActor(),
        ))
    }

    // ------------------------------------------------------------------------
    // Create Body Group
    // ------------------------------------------------------------------------
    private fun createBG_Borders() {
        bgBorders.create(0f,0f,1080f,1920f)
        // Астероїди залітають згори крізь стелю
        bgBorders.bTop.collisionList.remove(BodyId.ASTEROID)
    }

    // ------------------------------------------------------------------------
    // Create Body
    // ------------------------------------------------------------------------
    private fun createB_Down() {
        val aDownImg = Image(drawerUtil.getRegion(planet.groundColor))
        stageUI.addActor(aDownImg)
        aDownImg.setBounds(0f, 250f, 1080f, 28f)
        // Неонова кромка землі
        stageUI.addActor(object : Actor() {
            override fun draw(batch: Batch, parentAlpha: Float) {
                drawerUtil.additive {
                    tmpColor.set(planet.accent); tmpColor.a = 0.25f * parentAlpha
                    filledRectangle(0f, 270f, 1080f, 14f, tmpColor)
                }
                tmpColor.set(planet.accent).lerp(Color.WHITE, 0.5f); tmpColor.a = 0.9f * parentAlpha
                drawerUtil.drawer.filledRectangle(0f, 275f, 1080f, 3f, tmpColor)
            }
        })

        bDown.apply {
            id = BodyId.BORDERS
            collisionList.addAll(arrayOf(BodyId.BALL, BodyId.ASTEROID))
        }
        bDown.create(0f,268f,1080f,10f)
    }

    private fun createB_Ball() {
        bBall.apply {
            id = BodyId.BALL
            collisionList.addAll(arrayOf(
                BodyId.BORDERS, BodyId.WALL, BodyId.TRI, BodyId.STAR,
                BodyId.PORTAL, BodyId.HOLE, BodyId.LASER, BodyId.ASTEROID, BodyId.GATE,
            ))
            beginContactBlockArray.add(AbstractBody.ContactBlock { onBallContact(it) })
            endContactBlockArray.add(AbstractBody.ContactBlock { if (it.id == BodyId.BORDERS) groundBodies.remove(it) })
        }
        bBall.create(LevelGenerator.BALL_START_X, LevelGenerator.BALL_START_Y, 119f, 119f)
        bHoles.forEach { it.target = bBall }
    }

    private fun createB_PlatList() {
        bPlatList.onEachIndexed { index, bPlat ->
            bPlat.apply {
                id = BodyId.BORDERS
                collisionList.addAll(arrayOf(BodyId.BALL, BodyId.ASTEROID))
            }
            bPlat.create(levelData.plats[index].position, LevelGenerator.PLAT_SIZE)
        }
    }

    private fun createB_TriList() {
        bTriList.onEachIndexed { index, bTri ->
            bTri.apply {
                id = BodyId.TRI
                collisionList.add(BodyId.BALL)
                if (data.attachedTo >= 0) attachedPlat = bPlatList.getOrNull(data.attachedTo)
            }
            bTri.create(levelData.tris[index].position, LevelGenerator.TRI_SIZE)
        }
    }

    private fun createB_StaList() {
        bStaList.onEachIndexed { index, bSta ->
            bSta.apply {
                id = BodyId.STAR
                collisionList.add(BodyId.BALL)
            }
            bSta.create(levelData.stars[index], LevelGenerator.STAR_SIZE)
            // Зірки легенько «дихають»
            bSta.actor?.let { a ->
                a.setOrigin(Align.center)
                a.addAction(Actions.forever(Actions.sequence(
                    Actions.scaleTo(1.12f, 1.12f, 0.7f + index * 0.1f, Interpolation.sine),
                    Actions.scaleTo(1f, 1f, 0.7f + index * 0.1f, Interpolation.sine),
                )))
            }
        }
    }

    private fun createB_Portals() {
        levelData.portals.forEachIndexed { i, data ->
            val a = bPortals[i * 2]
            val b = bPortals[i * 2 + 1]
            a.other = b; b.other = a
            listOf(a to Vector2(data.ax, data.ay), b to Vector2(data.bx, data.by)).forEach { (portal, center) ->
                portal.apply { id = BodyId.PORTAL; collisionList.add(BodyId.BALL) }
                val s = LevelGenerator.PORTAL_SIZE
                portal.create(center.x - s / 2f, center.y - s / 2f, s, s)
            }
        }
    }

    private fun createB_Holes() {
        bHoles.forEach { hole ->
            hole.apply { id = BodyId.HOLE; collisionList.add(BodyId.BALL) }
            val s = LevelGenerator.HOLE_CORE
            hole.create(hole.data.x - s / 2f, hole.data.y - s / 2f, s, s)
        }
    }

    private fun createB_Lasers() {
        bLasers.forEach { laser ->
            laser.apply { id = BodyId.LASER; collisionList.add(BodyId.BALL) }
            laser.create(laser.data.x, laser.data.y - LevelGenerator.LASER_H / 2f, laser.data.w, LevelGenerator.LASER_H)
            laser.applyInitialState()
        }
    }

    private fun createB_Gate() {
        bGate.apply { id = BodyId.GATE; collisionList.add(BodyId.BALL) }
        val s = LevelGenerator.GATE_SIZE
        bGate.create(levelData.gate.x - s / 2f, levelData.gate.y - s / 2f, s, s)
    }

    // ------------------------------------------------------------------------
    // Астероїди
    // ------------------------------------------------------------------------
    private fun updateAsteroids(delta: Float) {
        val rain = levelData.asteroids ?: return
        if (isDead || isWon) return

        asteroidTimer -= delta
        if (asteroidWarnX < 0f && asteroidTimer <= ASTEROID_WARNING) {
            asteroidWarnX = MathUtils.random(120f, 960f)
            game.soundUtil.apply { play(TICK, 0.6f) }
        }
        if (asteroidTimer <= 0f) {
            spawnAsteroid(asteroidWarnX)
            asteroidWarnX = -1f
            asteroidTimer = rain.interval
        }
    }

    private fun spawnAsteroid(x: Float) {
        bAsteroids.removeAll { it.body == null }
        if (bAsteroids.size >= MAX_ASTEROIDS) return

        val size = MathUtils.random(70f, 100f)
        val asteroid = BAsteroid(this, fx).apply {
            id = BodyId.ASTEROID
            collisionList.addAll(arrayOf(BodyId.BORDERS, BodyId.WALL, BodyId.BALL))
        }
        asteroid.create(x - size / 2f, 1960f, size, size)
        asteroid.body?.setLinearVelocity(MathUtils.random(-1f, 1f), -3f)
        // Під м'ячем і HUD, але над платформами
        asteroid.actor?.zIndex = bBall.actor?.zIndex ?: 0
        bAsteroids.add(asteroid)
    }

    // ------------------------------------------------------------------------
    // Контакти м'яча
    // ------------------------------------------------------------------------
    private fun onBallContact(other: AbstractBody) {
        when (other.id) {
            BodyId.BORDERS -> onLanded(other)
            BodyId.WALL    -> Gdx.input.vibrate(20)
            BodyId.STAR    -> collectStar(other)
            BodyId.PORTAL  -> teleport(other as BPortal)
            BodyId.GATE    -> win(other as BGate)
            BodyId.TRI, BodyId.LASER, BodyId.HOLE, BodyId.ASTEROID -> die(other.id)
        }
    }

    private fun onLanded(other: AbstractBody) {
        val plat = other as? BPlat
        // Удар об низ платформи — не приземлення (інакше стрибок «відновлювався» б від стелі)
        if (plat != null) {
            val ballY = bBall.body?.worldCenter?.y?.toUI ?: 0f
            if (ballY < plat.position.y + plat.size.y - 4f) return
        }

        groundBodies.add(other)
        lastGroundTime = levelTime
        airJumpsLeft = MAX_AIR_JUMPS

        val vy = bBall.body?.linearVelocity?.y ?: 0f

        runGDX {
            val c = ballCenter
            if (vy < -2.5f) {
                fx.dust(c.x, c.y - 56f, planet.accent, count = 8, spread = 75f)
                Gdx.input.vibrate(30)
                bBall.ballActor.squash()
                if (vy < -8f) shake.shake(5f, 0.15f)
            }
            if (plat == null || plat.kind != PlatformKind.TRAMPOLINE) {
                safePos.set(c.x, c.y + 20f)
            }
            plat?.touched()
            if (plat?.kind == PlatformKind.TRAMPOLINE && !isDead && !isWon) {
                bBall.body?.let { b -> b.setLinearVelocity(b.linearVelocity.x, TRAMPOLINE_VY) }
                airJumpsLeft = MAX_AIR_JUMPS
                game.soundUtil.apply { play(TRAMPOLINE, 0.9f) }
                fx.ring(c.x, c.y - 50f, planet.accent, size = 40f, life = 0.4f)
                bBall.ballActor.stretch()
            }
            plat?.let { tutorial.onLanded(bPlatList.indexOf(it)) }
        }
    }

    private fun collectStar(star: AbstractBody) {
        if (star.id == BodyId.NONE) return
        star.setNoneId()
        starsCollected++

        game.soundUtil.apply { play(BONUS, 10f); play(SPARKLE, 0.8f, 0.9f + 0.1f * starsCollected) }

        val newStars = game.starsUtil.stars + 1
        game.starsUtil.update(newStars)
        game.activity.submitLeaderboardScore(newStars)

        val cx = star.position.x + star.size.x / 2f
        val cy = star.position.y + star.size.y / 2f
        runGDX {
            fx.burst(cx, cy, StarRating.GOLD, 26, speedMin = 120f, speedMax = 420f, sizeMin = 3f, sizeMax = 7f, life = 0.7f)
            fx.burst(cx, cy, Color.WHITE, 8, speedMin = 200f, speedMax = 500f, sizeMin = 2f, sizeMax = 4f, life = 0.45f, kind = 1)
            fx.ring(cx, cy, StarRating.GOLD, size = 40f, life = 0.5f)
            popup("+1", cx, cy + 30f, StarRating.GOLD)
            starHud.earned = starsCollected
            starHud.clearActions()
            starHud.addAction(Actions.sequence(Actions.scaleTo(1.25f, 1.25f, 0.1f), Actions.scaleTo(1f, 1f, 0.15f)))
            star.destroy()
            tutorial.onStar()
        }
    }

    private fun teleport(portal: BPortal) {
        if (portalCooldown > 0f || isDead || isWon) return
        val other = portal.other ?: return
        portalCooldown = PORTAL_COOLDOWN

        runGDX {
            val b = bBall.body ?: return@runGDX
            val from = portal.centerUI
            val to   = other.centerUI
            val v = b.linearVelocity
            b.setTransform(to.x.toB2, to.y.toB2, b.angle)
            b.setLinearVelocity(v.x * 0.8f, maxOf(v.y, 1.5f))

            fx.trailClear()
            groundBodies.clear()
            fx.ring(from.x, from.y, planet.accent, size = 60f, life = 0.5f)
            fx.ring(to.x, to.y, planet.accent, size = 60f, life = 0.5f)
            fx.burst(to.x, to.y, planet.accent, 18, speedMin = 100f, speedMax = 350f, sizeMin = 2f, sizeMax = 5f, life = 0.5f, gravity = 0f)
            portal.portalActor.flash(); other.portalActor.flash()
            game.soundUtil.apply { play(PORTAL, 0.9f) }
            Gdx.input.vibrate(40)
        }
    }

    // ------------------------------------------------------------------------
    // Смерть / Continue
    // ------------------------------------------------------------------------
    private fun die(cause: String) {
        if (isDead || isWon || invulnerableTime > 0f) return
        isDead = true

        runGDX {
            isPauseWorld = true
            val c = ballCenter
            val glow = bBall.ballActor.glowColor
            fx.burst(c.x, c.y, glow, 42, speedMin = 150f, speedMax = 650f, sizeMin = 3f, sizeMax = 9f, life = 0.9f)
            fx.burst(c.x, c.y, Color.WHITE, 16, speedMin = 250f, speedMax = 700f, sizeMin = 2f, sizeMax = 5f, life = 0.5f, kind = 1)
            fx.ring(c.x, c.y, glow, size = 70f, life = 0.6f)
            fx.ring(c.x, c.y, Color.WHITE, size = 40f, life = 0.4f)
            bBall.actor?.isVisible = false
            fx.trailClear()
            shake.shake(20f, 0.5f)
            tutorial.hideAll()

            game.soundUtil.apply {
                play(EXPLODE, 1f)
                play(FAIL, 0.7f)
                if (cause == BodyId.HOLE) play(BLACKHOLE, 1f)
            }
            Gdx.input.vibrate(200)

            stageUI.addAction(Actions.delay(0.85f, Actions.run { showDeathOverlay() }))
        }
    }

    private fun showDeathOverlay() {
        val overlay = Group().apply { setSize(WIDTH, HEIGHT); color.a = 0f }

        overlay.addActor(object : Actor() {
            override fun draw(batch: Batch, parentAlpha: Float) {
                tmpColor.set(0.02f, 0.02f, 0.08f, 0.62f * parentAlpha)
                drawerUtil.drawer.filledRectangle(-40f, -40f, WIDTH + 80f, HEIGHT + 80f, tmpColor)
            }
        })

        val panel = NeonPanel(drawerUtil.whiteRegion, Color.valueOf("241454"), Color.valueOf("0C0724"), planet.accent, planet.accent2).apply {
            radius = 48f; glow = 0.8f; sheen = 0f
            setBounds(110f, 560f, WIDTH - 220f, 800f)
        }
        overlay.addActor(panel)

        val title = Label("LOST IN SPACE", Label.LabelStyle(fontBig, Color.valueOf("FFE9F4"))).apply {
            setBounds(0f, 1230f, WIDTH, 80f); setAlignment(Align.center)
        }
        val sub = Label("STARS COLLECTED: $starsCollected / 3", Label.LabelStyle(fontSmall, Color.valueOf("D9CFFF"))).apply {
            setBounds(0f, 1172f, WIDTH, 40f); setAlignment(Align.center)
        }
        overlay.addActor(title); overlay.addActor(sub)

        val btnW = 600f; val btnH = 150f; val bx = (WIDTH - btnW) / 2f
        var y = 960f

        if (!continueUsed) {
            val canAd = game.activity.isRewardedReady()
            val btn = PillButton(drawerUtil, if (canAd) "CONTINUE  (AD)" else "CONTINUE  (NO AD)", fontBtn, PillButton.Style.GREEN, soundUtil = game.soundUtil)
            btn.setBounds(bx, y, btnW, btnH)
            btn.enabledLook = canAd
            btn.setOnClickListener {
                btn.enabledLook = false
                var rewarded = false
                game.activity.showRewarded(
                    onReward = { rewarded = true },
                    onDone   = { runGDX { if (rewarded) revive(overlay) else btn.enabledLook = game.activity.isRewardedReady() } },
                )
            }
            overlay.addActor(btn)
            y -= 180f
        }

        val retry = PillButton(drawerUtil, "RETRY", fontBtn, PillButton.Style.PURPLE, soundUtil = game.soundUtil).apply {
            setBounds(bx, y, btnW, btnH)
            setOnClickListener { retryLevel() }
        }
        y -= 180f
        val menu = PillButton(drawerUtil, "MENU", fontBtn, PillButton.Style.GRAY, soundUtil = game.soundUtil).apply {
            setBounds(bx, y, btnW, btnH)
            setOnClickListener { leave() }
        }
        overlay.addActor(retry); overlay.addActor(menu)

        stageUI.addActor(overlay)
        overlay.addAction(Actions.fadeIn(0.3f))
        listOf(aUp, aUpUp, aLeft, aRight).forEach { it.touchable = Touchable.disabled }
    }

    private fun retryLevel() {
        // Реклама — лише після кожної другої поразки
        val showAd = AdPolicy.onLevelLost()
        stageUI.root.animHide(TIME_ANIM_ALPHA) {
            if (showAd) game.activity.showInterstitial {
                runGDX { game.navigationManager.navigate(AbstractGameScreen::class.java.name) }
            }
            else game.navigationManager.navigate(AbstractGameScreen::class.java.name)
        }
    }

    private fun revive(overlay: Group) {
        overlay.addAction(Actions.sequence(Actions.fadeOut(0.25f), Actions.removeActor()))
        continueUsed = true
        isDead = false

        val b = bBall.body ?: return
        b.setTransform(safePos.x.toB2, safePos.y.toB2, 0f)
        b.setLinearVelocity(0f, 0f)
        b.angularVelocity = 0f
        bBall.actor?.isVisible = true
        bBall.ballActor.invulnerable = true
        invulnerableTime = INVULNERABLE_TIME
        groundBodies.clear()
        airJumpsLeft = MAX_AIR_JUMPS
        fx.trailClear()

        fx.ring(safePos.x, safePos.y, bBall.ballActor.glowColor, size = 80f, life = 0.7f)
        fx.burst(safePos.x, safePos.y, Color.WHITE, 20, speedMin = 100f, speedMax = 300f, sizeMin = 2f, sizeMax = 5f, life = 0.6f, gravity = 0f)
        game.soundUtil.apply { play(REVIVE, 1f) }

        listOf(aUp, aUpUp, aLeft, aRight).forEach { it.touchable = Touchable.enabled }
        isPauseWorld = false
    }

    // ------------------------------------------------------------------------
    // Перемога
    // ------------------------------------------------------------------------
    private fun win(gate: BGate) {
        if (isWon || isDead) return
        isWon = true

        runGDX {
            isPauseWorld = true
            gate.gateActor.activate()
            val gc = gate.centerUI
            game.soundUtil.apply { play(WARP, 1f) }
            fx.ring(gc.x, gc.y, planet.accent, size = 90f, life = 0.8f)
            fx.ring(gc.x, gc.y, Color.WHITE, size = 60f, life = 1.0f)
            fx.burst(gc.x, gc.y, Color.WHITE, 30, speedMin = 80f, speedMax = 420f, sizeMin = 2f, sizeMax = 6f, life = 0.8f, gravity = 0f)
            fx.trailClear()
            shake.shake(6f, 0.6f)
            tutorial.hideAll()
            tutorial.markCompleted()

            val ballActor = bBall.ballActor
            ballActor.clearActions()
            ballActor.addAction(Actions.parallel(
                Actions.moveTo(gc.x - ballActor.width / 2f, gc.y - ballActor.height / 2f, 0.45f, Interpolation.pow2In),
                Actions.scaleTo(0.03f, 0.03f, 0.65f, Interpolation.pow2In),
                Actions.rotateBy(900f, 0.65f, Interpolation.pow2In),
            ))
            listOf(aUp, aUpUp, aLeft, aRight).forEach { it.touchable = Touchable.disabled; it.addAction(Actions.fadeOut(0.4f)) }

            // Спалах при вході у ворота
            flash.clearActions()
            flash.addAction(Actions.sequence(Actions.delay(0.55f), Actions.alpha(0.85f, 0.08f), Actions.fadeOut(0.5f)))

            stageUI.addAction(Actions.delay(1.05f, Actions.run { finishLevel() }))
        }
    }

    private fun finishLevel() {
        var bonus = 0
        var newBest = false
        if (isDaily) {
            if (!game.dailyUtil.isDoneToday()) {
                bonus = DailyUtil.BONUS_STARS
                game.starsUtil.add(bonus.toLong())
                game.dailyUtil.markDone()
            }
        } else {
            newBest = starsCollected > game.levelUtil.rating(level)
            game.levelUtil.complete(level, starsCollected)
        }
        game.activity.submitLeaderboardScore(game.starsUtil.stars)
        lastResult = LevelResult(level, starsCollected, isDaily, bonus, newBest)

        stageUI.root.animHide(TIME_ANIM_ALPHA) {
            game.navigationManager.navigate(SpaceWinScreen::class.java.name)
        }
    }

    // ------------------------------------------------------------------------
    // Туторіал
    // ------------------------------------------------------------------------
    /**
     * Покрокові підказки перших двох рівнів + одноразові інтро для нових механік.
     * Показуються, поки рівень не пройдено; вказівник підсвічує потрібну кнопку.
     */
    private inner class Tutorial {
        val bubble = HintBubble(drawerUtil, fontHint, planet.accent, planet.accent2)

        private val enabled = DebugFlags.forceTutorial(game.activity) ||
            (!isDaily && level <= 1 && game.levelUtil.rating(level) == 0 && !game.hintUtil.isSeen("tut$level"))
        private var step = -1
        private var autoHide = 0f
        private var pointerActor: Actor? = null
        private var pointerPoint: Vector2? = null
        private var pointerTime = 0f

        fun start() {
            if (DebugFlags.shots) return
            if (enabled) {
                if (level == 0) showStep(0) else showStep(10)
                return
            }
            // Інтро нової механіки — один раз
            if (!isDaily) LevelGenerator.introHint(level)?.let { hint ->
                if (!game.hintUtil.isSeen(hint.id)) {
                    game.hintUtil.markSeen(hint.id)
                    toast(hint.text, 4.2f)
                }
            }
        }

        private fun showStep(s: Int) {
            step = s
            autoHide = 0f
            pointerActor = null; pointerPoint = null
            when (s) {
                // --- Рівень 1: стрибок → рух у повітрі → ліворуч → подвійний стрибок → ворота ---
                0 -> hint("TAP TO JUMP", aUpUp)
                1 -> hint("TAP TO MOVE\nWHILE IN THE AIR", aRight)
                2 -> hint("NOW JUMP AND TAP LEFT", aLeft)
                3 -> hint("TAP JUMP AGAIN IN THE AIR\nDOUBLE JUMP GOES HIGHER", aUpUp)
                4 -> hint("REACH THE WARP GATE", null, levelData.gate)
                5 -> toast("COLLECT STARS\nTHEY UNLOCK BALL SKINS", 2.6f)
                // --- Рівень 2 ---
                10 -> { toast("AVOID THE SPIKES\nTHEY POP YOUR BALL", 3.5f); levelData.tris.firstOrNull()?.let { pointerPoint = Vector2(it.x + 29f, it.y + 35f) } }
                11 -> { hint("SMALL ARROW = SHORT HOP\nFOR TINY ADJUSTMENTS", aUp); autoHide = 3.5f }
                else -> hideAll()
            }
        }

        private fun hint(text: String, target: Actor?, point: Vector2? = null) {
            pointerActor = target
            pointerPoint = point
            bubble.show(text, WIDTH / 2f, 1640f)
            game.soundUtil.apply { play(SPARKLE, 0.35f, 1.3f) }
        }

        private fun toast(text: String, seconds: Float) {
            bubble.show(text, WIDTH / 2f, 1640f)
            autoHide = seconds
            game.soundUtil.apply { play(SPARKLE, 0.35f, 1.3f) }
        }

        /** Найдальший крок, який гравець уже пройшов, — щоб не показувати підказки повторно. */
        private var reached = -1

        fun update(delta: Float) {
            pointerTime += delta
            if (autoHide > 0f) {
                autoHide -= delta
                if (autoHide <= 0f) { bubble.hide(); pointerActor = null; pointerPoint = null; if (step == 5) step = -1 }
            }
        }

        private fun done() { bubble.hide(); pointerActor = null; pointerPoint = null; step = -1 }

        fun onJump(air: Boolean) {
            if (!enabled) return
            when {
                step == 0 -> if (level == 0) showStep(1) else done()   // перший стрибок → рух у повітрі
                step == 2 && !air -> {}                                 // чекаємо на тап «вліво»
                step == 3 && air -> { reached = maxOf(reached, 3); done() }
            }
        }

        fun onMove() {
            if (!enabled) return
            if (step == 1 || step == 2) { reached = maxOf(reached, step); done() }
        }

        fun onLanded(index: Int) {
            if (!enabled || index < 0) return
            // У туторіалі подвійні зони посадки рахуються як одна платформа
            val platIndex = LevelGenerator.tutorialLogicalIndex(level, index)
            if (level == 0) when (platIndex) {
                0 -> if (reached < 2 && step != 2) showStep(2)
                1 -> if (reached < 3 && step != 3) showStep(3)
                2 -> if (reached < 4 && step != 4) { reached = 4; showStep(4) }
            } else if (level == 1) when (platIndex) {
                0 -> if (reached < 11) { reached = 11; showStep(11) }
                2 -> if (step == 10) done()
            }
        }

        fun onStar() {
            if (!enabled || level != 0) return
            // Підказка про зірки — лише коли нічого важливішого не показується
            if (step == -1 && reached < 5) { reached = maxOf(reached, 5); showStep(5) }
        }

        fun hideAll() { bubble.hide(); pointerActor = null; pointerPoint = null; autoHide = 0f }

        fun markCompleted() { if (enabled) game.hintUtil.markSeen("tut$level") }

        /** Пульсуюче кільце навколо цілі й стрілка, що підстрибує над нею. */
        fun drawPointer(parentAlpha: Float) {
            val cx: Float; val cy: Float; val r: Float
            val a = pointerActor
            val p = pointerPoint
            when {
                a != null -> { cx = a.x + a.width / 2f; cy = a.y + a.height / 2f; r = a.width * 0.62f }
                p != null -> { cx = p.x; cy = p.y; r = 80f }
                else -> return
            }
            val pulse = 0.5f + 0.5f * MathUtils.sin(pointerTime * 5f)
            val d = drawerUtil.drawer
            drawerUtil.additive {
                tmpColor.set(planet.accent); tmpColor.a = 0.35f * parentAlpha * (0.6f + 0.4f * pulse)
                setColor(tmpColor)
                circle(cx, cy, r + 10f * pulse, 10f)
                tmpColor.a = 0.15f * parentAlpha
                filledCircle(cx, cy, r + 10f * pulse, tmpColor)
            }
            tmpColor.set(Color.WHITE); tmpColor.a = 0.95f * parentAlpha
            d.setColor(tmpColor)
            d.circle(cx, cy, r + 10f * pulse, 3f)
            // Стрілка вниз, що підстрибує
            val ay = cy + r + 40f + 18f * pulse
            d.filledTriangle(cx - 22f, ay + 30f, cx + 22f, ay + 30f, cx, ay, tmpColor)
            d.setColor(Color.WHITE)
        }
    }

}
