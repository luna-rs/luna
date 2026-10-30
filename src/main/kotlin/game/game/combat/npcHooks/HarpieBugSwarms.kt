package game.combat.npcHooks

import game.skill.firemaking.lightSource.LightSource
import io.luna.Luna
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player

/**
 * Checks the lit bug lantern needed to harm harpie bug swarms. The existing light-source definition owns the
 * required item and lighting level, and the shared Slayer setting controls whether equipment is mandatory.
 * Both attack preparation and delivered hits use this check so removing or extinguishing the lantern cannot
 * bypass the creature's protection while a projectile is in flight.
 *
 * @author lare96
 */
object HarpieBugSwarms {

    /**
     * The revision 377 harpie bug swarm NPC.
     */
    const val NPC_ID = 3153

    /**
     * Returns the unmet requirement, or null when the player can harm the swarm. An unlit lantern or one carried
     * in inventory is insufficient. Lighting retains its native Firemaking requirement; an already-lit lantern
     * does not gain an additional current-level check during combat.
     */
    fun failureMessage(player: Player,
                       equipmentNeeded: Boolean = Luna.settings().skills().slayerEquipmentNeeded()): String? {
        if (!equipmentNeeded) return null
        return if (player.equipment[Equipment.SHIELD]?.id != LightSource.BUG_LANTERN.lit)
            "You need to wield a lit bug lantern to harm harpie bug swarms." else null
    }
}
