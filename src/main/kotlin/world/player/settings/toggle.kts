package world.player.settings

import api.predef.*
import io.luna.game.model.mob.varp.PersistentVarp

// Brightness constants.
val DEFAULT_BRIGHTNESS = 0
val LOW_BRIGHTNESS = 1
val NORMAL_BRIGHTNESS = 2
val MEDIUM_BRIGHTNESS = 3
val HIGH_BRIGHTNESS = 4

// Mouse button constants.
val DEFAULT_MOUSE_BUTTONS = 0
val ONE_MOUSE_BUTTON = 1
val TWO_MOUSE_BUTTONS = 2

// Volume constants.
val VOLUME_OFF = 4
val VOLUME_LOW = 3
val VOLUME_MEDIUM = 2
val VOLUME_HIGH = 1
val VOLUME_MAX = 0

/**
 * Running settings.
 */
button(152) { plr.isRunning = false }

button(153) { plr.isRunning = true }


/**
 * Auto-retaliate settings.
 */
button(150) { plr.sendVarp(PersistentVarp.AUTO_RETALIATE, 0) }

button(151) { plr.sendVarp(PersistentVarp.AUTO_RETALIATE, 1) }


/**
 * Screen brightness settings.
 */
button(906) { plr.sendVarp(PersistentVarp.BRIGHTNESS_LEVEL, LOW_BRIGHTNESS) }

button(908) { plr.sendVarp(PersistentVarp.BRIGHTNESS_LEVEL, NORMAL_BRIGHTNESS) }

button(910) { plr.sendVarp(PersistentVarp.BRIGHTNESS_LEVEL, MEDIUM_BRIGHTNESS) }

button(912) { plr.sendVarp(PersistentVarp.BRIGHTNESS_LEVEL, HIGH_BRIGHTNESS) }


/**
 * Mouse button settings.
 */
button(914) { plr.sendVarp(PersistentVarp.MOUSE_TYPE, ONE_MOUSE_BUTTON) }

button(913) { plr.sendVarp(PersistentVarp.MOUSE_TYPE, TWO_MOUSE_BUTTONS) }


/**
 * Chat effect settings.
 */
button(915) { plr.sendVarp(PersistentVarp.CHAT_EFFECTS, true) }

button(916) { plr.sendVarp(PersistentVarp.CHAT_EFFECTS, false) }


/**
 * Split private chat settings.
 */
button(957) { plr.sendVarp(PersistentVarp.SPLIT_PRIVATE_CHAT, true) }

button(958) { plr.sendVarp(PersistentVarp.SPLIT_PRIVATE_CHAT, false) }


/**
 * Accept aid settings.
 */
button(12464) { plr.sendVarp(PersistentVarp.ACCEPT_AID, true) }

button(12465) { plr.sendVarp(PersistentVarp.ACCEPT_AID, false) }


/**
 * Music volume settings.
 */
button(930) { plr.sendVarp(PersistentVarp.MUSIC_VOLUME, VOLUME_OFF) }

button(931) { plr.sendVarp(PersistentVarp.MUSIC_VOLUME, VOLUME_LOW) }

button(932) { plr.sendVarp(PersistentVarp.MUSIC_VOLUME, VOLUME_MEDIUM) }

button(933) { plr.sendVarp(PersistentVarp.MUSIC_VOLUME, VOLUME_HIGH) }

button(934) { plr.sendVarp(PersistentVarp.MUSIC_VOLUME, VOLUME_MAX) }


/**
 * Effect volume settings.
 */
button(941) { plr.sendVarp(PersistentVarp.EFFECTS_VOLUME, VOLUME_OFF) }

button(942) { plr.sendVarp(PersistentVarp.EFFECTS_VOLUME, VOLUME_LOW) }

button(943) { plr.sendVarp(PersistentVarp.EFFECTS_VOLUME, VOLUME_MEDIUM) }

button(944) { plr.sendVarp(PersistentVarp.EFFECTS_VOLUME, VOLUME_HIGH) }

button(945) { plr.sendVarp(PersistentVarp.EFFECTS_VOLUME, VOLUME_MAX) }
