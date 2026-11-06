package game.combat.npcHooks.tzhaar

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Tz-Kih takes one Prayer point with every attack, even one that hits 0 or that a protection prayer blocks.
 *
 * @author TheLining
 */
combat(2734, 2735, 2627, 2628) {
    attack {
        melee {
            val target = other
            if (target is Player) {
                val prayer = target.skill(Skill.PRAYER)
                prayer.level = (prayer.level - 1).coerceAtLeast(0)
                if (prayer.level == 0) target.combat.prayers.deactivateAll()
            }
            it
        }
    }
}
