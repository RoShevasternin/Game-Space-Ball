package com.rostislav.spaceball.game.actors.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.rostislav.spaceball.game.effects.NeonPanel
import com.rostislav.spaceball.game.manager.util.SoundUtil
import com.rostislav.spaceball.game.utils.ShapeDrawerUtil

/**
 * Неонова скляна кнопка-«пігулка» на шейдері [com.rostislav.spaceball.game.effects.UiShader]:
 * плавні краї, глянець, обідок зі світінням, натискання з анімацією.
 */
class PillButton(
    drawerUtil: ShapeDrawerUtil,
    text: String,
    font: BitmapFont,
    style: Style = Style.PURPLE,
    textColor: Color = Color.valueOf("F4F0FF"),
    private val soundUtil: SoundUtil? = null,
) : Group() {

    /** Кольори скла (top/bottom) і неонового обідка (rimA → rimB). */
    enum class Style(val top: String, val bottom: String, val rimA: String, val rimB: String) {
        PURPLE("3A2380", "140B34", "FF5CC8", "5C8CFF"),
        GREEN ("1B5A3C", "07221A", "7CFF6B", "2EE6C8"),
        ORANGE("6A3A12", "24100A", "FFB35C", "FF5CC8"),
        GRAY  ("3A3F52", "141826", "8C93A8", "5C6478"),
        CYAN  ("15487A", "071A38", "6FE3FF", "B07BFF"),
        GOLD  ("6A4E12", "241A06", "FFD33D", "FF8A3D"),
    }

    val panel = NeonPanel(
        drawerUtil.whiteRegion,
        Color.valueOf(style.top), Color.valueOf(style.bottom),
        Color.valueOf(style.rimA), Color.valueOf(style.rimB),
    )
    val label = Label(text, Label.LabelStyle(font, textColor))

    private var onClick: () -> Unit = {}

    var enabledLook = true
        set(value) {
            field = value
            touchable = if (value) Touchable.enabled else Touchable.disabled
            panel.color.a = if (value) 1f else 0.45f
            label.color.a = if (value) 1f else 0.6f
            panel.glow = if (value) 1f else 0.2f
            panel.sheen = if (value) 1f else 0f
        }

    init {
        addActor(panel)
        addActor(label)
        label.setAlignment(Align.center)
        isTransform = true

        addListener(object : InputListener() {
            var within = false
            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                soundUtil?.apply { play(CLICK, 0.25f) }
                within = true
                panel.press = 1f
                clearActions()
                addAction(Actions.scaleTo(0.95f, 0.95f, 0.06f))
                return true
            }
            override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                within = x in 0f..width && y in 0f..height
                panel.press = if (within) 1f else 0f
            }
            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                clearActions()
                addAction(Actions.scaleTo(1f, 1f, 0.18f, Interpolation.swingOut))
                if (within) { within = false; onClick() }
                addAction(Actions.sequence(Actions.delay(0.12f), Actions.run { panel.press = 0f }))
            }
        })
    }

    fun setOnClickListener(block: () -> Unit) { onClick = block }

    fun setText(text: CharSequence) { label.setText(text) }

    override fun sizeChanged() {
        super.sizeChanged()
        panel.setBounds(0f, 0f, width, height)
        label.setBounds(0f, 0f, width, height)
        setOrigin(Align.center)
    }
}
