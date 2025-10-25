package api.predef.ext

import io.luna.game.model.mob.inter.AbstractInterfaceSet
import io.luna.game.model.mob.inter.StandardInterface
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
fun <T : StandardInterface> AbstractInterfaceSet.get(interClass: KClass<T>): T? {
    val jClass = interClass.java
    return currentStandard.filter { jClass.isInstance(it) }.map { jClass.cast(it) }.orElse(null)
}

/**
 * Determines if the currently open [StandardInterface] matches [interClass].
 */
fun <T : StandardInterface> AbstractInterfaceSet.isOpen(interClass: KClass<T>): Boolean {
    val jClass = interClass.java
    return currentStandard.filter { jClass.isInstance(it) }.isPresent
}

/**
 * Determines if the currently open [StandardInterface] matches [id].
 */
fun AbstractInterfaceSet.isOpen(id: Int): Boolean {
    return currentStandard.filter { it.id.orElse(-1) == id }.isPresent
}

/**
 * Determines if the currently open [StandardInterface] matches any of [ids].
 */
fun AbstractInterfaceSet.anyOpen(ids: Collection<Int>): Boolean {
    return currentStandard.filter { ids.contains(it.id.orElse(-1)) }.isPresent
}
