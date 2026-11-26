package game.content.templeTrekking

import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.NpcAggressionProfile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatRestriction
import io.luna.game.model.mob.combat.damage.CombatDamage

/**
 * The villager being guided, inside an event. Only the event's monsters can fight it.
 *
 * @author TheLining
 */
class TrekFollower(val owner: Player, val trek: Trek, position: Position) :
    Npc(owner.context, trek.villager.npc, position), CombatRestriction {

    override fun onDamageApplied(damage: CombatDamage) {
        super.onDamageApplied(damage)
        (damage.attacker as? TrekMonster)?.reachedVillager = true
    }

    init {
        respawnTicks = -1
        combat.retreatPolicy = RetreatPolicy.NONE
        // The weakest villagers can't fight back at all.
        combat.isDisabled = !trek.villager.fights
    }

    override fun getAggressionProfile(): NpcAggressionProfile? = null

    override fun canFight(opponent: Mob) = opponent is TrekMonster && opponent.owner === owner
}

/**
 * A monster in an event, there only for the player and their villager.
 *
 * @property owner The player whose event it is.
 * @property sticky Whether the monster stays on the villager once it has reached it, however the player draws it.
 */
class TrekMonster(val owner: Player, id: Int, position: Position, val sticky: Boolean = false) :
    Npc(owner.context, id, position), CombatRestriction {

    /**
     * Set once the monster has hit the villager.
     */
    var reachedVillager = false

    /**
     * Set once the monster is beaten some other way than dying, as a cured juvinate is.
     */
    var beaten = false

    init {
        respawnTicks = -1
        combat.retreatPolicy = RetreatPolicy.NONE
    }

    override fun getAggressionProfile(): NpcAggressionProfile? = null

    override fun canFight(opponent: Mob) =
        opponent === owner || (opponent is TrekFollower && opponent.owner === owner)

    override fun onDamageApplied(damage: CombatDamage) {
        super.onDamageApplied(damage)
        if (damage.attacker === owner) {
            // A hit from the player draws the monster off the villager, unless it has its teeth in.
            val target = combat.target
            if (target is TrekFollower && !(sticky && reachedVillager) && isAlive && owner.isAlive) {
                combat.attack(owner)
            }
        }
    }
}
