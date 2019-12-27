package world.player.settings

import api.predef.*
import io.luna.game.model.mob.PlayerSettings.*

/**
 * Running settings.
 */
button(152) { plr.isRunning = false }

button(153) { plr.isRunning = true }


/**
 * Auto-retaliate settings.
 */
button(150) { plr.settings.isAutoRetaliate = true }

button(151) { plr.settings.isAutoRetaliate = false }


/**
 * Screen brightness settings.
 */
button(906) { plr.settings.brightnessLevel = BrightnessLevel.DARK }

button(908) { plr.settings.brightnessLevel = BrightnessLevel.NORMAL }

button(910) { plr.settings.brightnessLevel = BrightnessLevel.BRIGHT }

button(912) { plr.settings.brightnessLevel = BrightnessLevel.V_BRIGHT }


/**
 * Mouse button settings.
 */
button(914) { plr.settings.mouseType = MouseType.ONE_BUTTON }

button(913) { plr.settings.mouseType = MouseType.TWO_BUTTONS }


/**
 * world.player.privateChat.Chat effect settings.
 */
button(915) { plr.settings.isChatEffects = true }

button(916) { plr.settings.isChatEffects = false }


/**
 * Split private chat settings.
 */
button(957) { plr.settings.isSplitPrivateChat = true }

button(958) { plr.settings.isSplitPrivateChat = false }


/**
 * Accept aid settings.
 */
button(12464) { plr.settings.isAcceptAid = true }

button(12465) { plr.settings.isAcceptAid = false }


/**
 * Music volume settings.
 */
button(930) { plr.settings.musicVolume = VolumeLevel.OFF }

button(931) { plr.settings.musicVolume = VolumeLevel.ONE }

button(932) { plr.settings.musicVolume = VolumeLevel.TWO }

button(933) { plr.settings.musicVolume = VolumeLevel.THREE }

button(934) { plr.settings.musicVolume = VolumeLevel.FOUR }


/**
 * Effect volume settings.
 */
button(941) { plr.settings.effectsVolume = VolumeLevel.OFF }

button(942) { plr.settings.effectsVolume = VolumeLevel.ONE }

button(943) { plr.settings.effectsVolume = VolumeLevel.TWO }

button(944) { plr.settings.effectsVolume = VolumeLevel.THREE }

button(945) { plr.settings.effectsVolume = VolumeLevel.FOUR }
