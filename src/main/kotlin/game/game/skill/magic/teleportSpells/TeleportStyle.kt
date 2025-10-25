package game.skill.magic.teleportSpells

import io.luna.game.model.mob.Spellbook
import game.skill.magic.Magic

/**
 * An enum representing the different teleport styles.
 *
 * @author lare96
 */
enum class TeleportStyle(val spellbook: Spellbook, val action: (TeleportAction) -> Boolean) {
    REGULAR(Spellbook.REGULAR, { Magic.regularStyle(it) }),
    ANCIENT(Spellbook.ANCIENT, { Magic.ancientStyle(it) })
}