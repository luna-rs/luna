package game.skill.magic.teleportSpells

import game.skill.magic.Magic
import io.luna.game.model.mob.Spellbook

/**
 * An enum representing the different teleport styles.
 *
 * @author lare96
 */
enum class TeleportStyle(val spellbook: Spellbook, val action: (TeleportAction) -> Boolean) {
    REGULAR(Spellbook.REGULAR, { Magic.regularStyle(it) }),
    ANCIENT(Spellbook.ANCIENT, { Magic.ancientStyle(it) });

    companion object {

        /**
         * Mappings of [Spellbook] -> [TeleportStyle].
         */
        val SPELLBOOK_TO_STYLE = entries.associateBy { it.spellbook }
    }
}