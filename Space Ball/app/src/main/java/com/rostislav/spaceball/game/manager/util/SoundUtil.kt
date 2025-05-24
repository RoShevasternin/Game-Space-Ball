package com.rostislav.spaceball.game.manager.util

import com.badlogic.gdx.audio.Sound
import com.rostislav.spaceball.game.manager.AudioManager
import com.rostislav.spaceball.game.manager.SoundManager
import com.rostislav.spaceball.game.utils.runGDX

class SoundUtil {

    // Common
    val BONUS = SoundManager.EnumSound.BONUS.data.sound
    val CLICK = SoundManager.EnumSound.CLICK.data.sound
    val DOWN  = SoundManager.EnumSound.DOWN.data.sound
    val FAIL  = SoundManager.EnumSound.FAIL.data.sound

    var volumeLevel = AudioManager.volumeLevelPercent

    var isPause = (volumeLevel <= 0f)

    fun play(sound: Sound, volumePercent: Float = 1f) = runGDX { if (isPause.not()) sound.play((volumeLevel / 100f) * volumePercent) }
}