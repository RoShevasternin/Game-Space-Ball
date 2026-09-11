package com.rostislav.spaceball.game.actors.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.rostislav.spaceball.game.effects.NeonPanel
import com.rostislav.spaceball.game.utils.ShapeDrawerUtil

/** Підказка туторіалу: скляна неонова панель із текстом, з'являється «вистрибуючи». */
class HintBubble(
    drawerUtil: ShapeDrawerUtil,
    font: BitmapFont,
    accent: Color,
    accent2: Color,
) : Group() {

    private val panel = NeonPanel(
        drawerUtil.whiteRegion,
        Color.valueOf("1E1246"), Color.valueOf("0B0622"),
        Color(accent), Color(accent2),
    ).apply { radius = 34f; glow = 0.7f; sheen = 0f }

    private val label = Label("", Label.LabelStyle(font, Color.valueOf("F4F0FF"))).apply {
        setAlignment(Align.center)
    }

    val isShown get() = isVisible

    init {
        addActor(panel)
        addActor(label)
        isTransform = true
        isVisible = false
        touchable = Touchable.disabled
    }

    /** Показує текст, центр панелі — в ([cx], [cy]). */
    fun show(text: String, cx: Float, cy: Float) {
        label.setText(text)
        label.pack()
        val w = label.prefWidth + 80f
        val h = label.prefHeight + 44f
        setSize(w, h)
        setPosition(cx - w / 2f, cy - h / 2f)
        panel.setBounds(0f, 0f, w, h)
        label.setBounds(0f, 0f, w, h)
        setOrigin(Align.center)

        clearActions()
        isVisible = true
        color.a = 0f
        setScale(0.6f)
        addAction(Actions.parallel(
            Actions.fadeIn(0.25f),
            Actions.scaleTo(1f, 1f, 0.35f, Interpolation.swingOut),
        ))
    }

    fun hide() {
        if (!isVisible) return
        clearActions()
        addAction(Actions.sequence(
            Actions.parallel(Actions.fadeOut(0.22f), Actions.scaleTo(0.85f, 0.85f, 0.22f)),
            Actions.visible(false),
        ))
    }
}
