package game.content.pestControl

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.NpcAggressionProfile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy

/**
 * A round-owned NPC. Target selection belongs to the minigame and deaths never schedule a respawn.
 *
 * @author lare96
 */
class PestControlNpc(id: Int, position: Position, val portal: Int = -1) : Npc(ctx, id, position) {
    init {
        respawnTicks = -1
        combat.retreatPolicy = RetreatPolicy.NONE
        if (id in 3777..3782) combat.isDisabled = true
    }

    override fun getAggressionProfile(): NpcAggressionProfile? = null

    override fun onDamageApplied(damage: CombatDamage) {
        (damage.attacker as? Player)?.let { PestControl.credit(it, this, damage.rawAmount) }
    }
}
