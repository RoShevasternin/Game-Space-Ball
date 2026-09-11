package com.rostislav.spaceball.game.manager.util

import com.badlogic.gdx.audio.Sound
import com.rostislav.spaceball.game.manager.AudioManager
import com.rostislav.spaceball.game.manager.SoundManager
import com.rostislav.spaceball.game.utils.DebugFlags
import com.rostislav.spaceball.game.utils.runGDX
import com.rostislav.spaceball.util.log

class SoundUtil {

    // Common
    val BONUS = SoundManager.EnumSound.BONUS.data.sound
    val CLICK = SoundManager.EnumSound.CLICK.data.sound
    val DOWN  = SoundManager.EnumSound.DOWN.data.sound
    val FAIL  = SoundManager.EnumSound.FAIL.data.sound

    // Gameplay
    val JUMP       = SoundManager.EnumSound.JUMP.data.sound
    val JUMP2      = SoundManager.EnumSound.JUMP2.data.sound
    val TRAMPOLINE = SoundManager.EnumSound.TRAMPOLINE.data.sound
    val PORTAL     = SoundManager.EnumSound.PORTAL.data.sound
    val CRUMBLE    = SoundManager.EnumSound.CRUMBLE.data.sound
    val LASER      = SoundManager.EnumSound.LASER.data.sound
    val WARP       = SoundManager.EnumSound.WARP.data.sound
    val ASTEROID   = SoundManager.EnumSound.ASTEROID.data.sound
    val EXPLODE    = SoundManager.EnumSound.EXPLODE.data.sound
    val BLACKHOLE  = SoundManager.EnumSound.BLACKHOLE.data.sound
    val PURCHASE   = SoundManager.EnumSound.PURCHASE.data.sound
    val REVIVE     = SoundManager.EnumSound.REVIVE.data.sound
    val SPARKLE    = SoundManager.EnumSound.SPARKLE.data.sound
    val LOCKED     = SoundManager.EnumSound.LOCKED.data.sound
    val TICK       = SoundManager.EnumSound.TICK.data.sound

    var volumeLevel = AudioManager.volumeLevelPercent

    var isPause = (volumeLevel <= 0f)

    fun play(sound: Sound, volumePercent: Float = 1f) = runGDX {
        logForVideo(sound, volumePercent, 1f)
        if (isPause.not()) sound.play((volumeLevel / 100f) * volumePercent)
    }

    /** Відтворення зі зміненою висотою (0.5..2.0) — для варіацій одного й того ж звуку. */
    fun play(sound: Sound, volumePercent: Float, pitch: Float) = runGDX {
        logForVideo(sound, volumePercent, pitch)
        if (isPause.not()) sound.play((volumeLevel / 100f) * volumePercent, pitch, 0f)
    }

    // ------------------------------------------------------------------------
    // Звукова доріжка для рекламного відео
    // ------------------------------------------------------------------------

    private val names by lazy { SoundManager.EnumSound.entries.associate { it.data.sound to it.data.path } }

    /**
     * У режимі зйомки (`--ez shots true`) кожен звук пишеться в лог із часом:
     * `SFX <шлях> <epoch ms> <гучність> <висота>`. screenrecord не пише аудіо, тому
     * tools/build_tiktok_video.py відтворює звукову доріжку з цих подій.
     */
    private fun logForVideo(sound: Sound, volumePercent: Float, pitch: Float) {
        if (DebugFlags.shots) log("SFX ${names[sound]} ${System.currentTimeMillis()} $volumePercent $pitch")
    }
}
