package world.player.skill.magic.teleportSpells

import world.player.skill.magic.Magic

/**
 * An enum representing the different teleport styles.
 */
enum class TeleportStyle(val action: (TeleportAction) -> Boolean) {
    REGULAR({ Magic.regularStyle(it) }),
    ANCIENT({ Magic.ancientStyle(it) })
}