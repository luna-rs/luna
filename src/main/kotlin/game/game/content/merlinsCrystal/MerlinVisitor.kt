package game.content.merlinsCrystal

import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.NpcAggressionProfile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatRestriction

/**
 * A short-lived NPC summoned for one player's Merlin's Crystal scene.
 *
 * @author lare96
 */
internal class MerlinVisitor(val owner: Player, id: Int, at: Position) :
    Npc(owner.context, id, at), CombatRestriction {

    init {
        respawnTicks = -1
    }

    override fun getAggressionProfile(): NpcAggressionProfile? = null

    override fun canFight(opponent: Mob): Boolean = id == MerlinsCrystal.THRANTAX && opponent === owner
}
