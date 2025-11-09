package api.predef.ext

import io.luna.game.model.mob.overlay.AbstractOverlay
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.mob.overlay.StandardInterface
import kotlin.reflect.KClass

/**
 * All the option dialogue interfaces.
 */
val OPTION_DIALOGUES = setOf(14443, 2469, 8207, 8219)

/**
 * All the NPC dialogue interfaces.
 */
val NPC_DIALOGUES = setOf(4882, 4887, 4893, 4900)

/**
 * All the player dialogue interfaces.
 */
val PLAYER_DIALOGUES = setOf(968, 973, 979, 986)

/**
 * All the text dialogue interfaces.
 */
val TEXT_DIALOGUES = setOf(356, 359, 363, 368, 374)

/**
 * All the make item dialogue interfaces.
 */
val MAKE_ITEM_DIALOGUES = setOf(8880, 8866, 8899, 8938)

/**
 * All the dialogue interfaces.
 */
val ALL_DIALOGUES = OPTION_DIALOGUES + NPC_DIALOGUES + PLAYER_DIALOGUES + TEXT_DIALOGUES + MAKE_ITEM_DIALOGUES

/**
 * Returns the currently open [StandardInterface] if it matches [interClass].
 */
operator fun <T : AbstractOverlay> AbstractOverlaySet.get(interClass: KClass<T>): T? =
    player.overlays.getOverlay(interClass.java)

/**
 * Determines if the currently open [AbstractOverlay] matches [interClass].
 */
operator fun <T : AbstractOverlay> AbstractOverlaySet.contains(interClass: KClass<T>): Boolean = player.overlays.contains(interClass.java)