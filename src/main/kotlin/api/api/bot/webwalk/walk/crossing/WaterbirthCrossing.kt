package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.content.waterbirth.Waterbirth
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.combat.damage.CombatDamageType
import kotlin.time.Duration.Companion.seconds

/**
 * Cooperates with actual adventurers at Waterbirth's gates. A missing partner is a bounded route failure.
 * Own ammunition and native combat open opposing supports; the handler never changes a door or creates supplies.
 *
 * @author lare96
 */
object WaterbirthCrossing : CrossingHandler {
    const val ID = "waterbirth"
    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "Waterbirth crossing has no action"
        val position = action.objectPosition ?: return "Waterbirth crossing has no position"
        if (context.arrived(leg, 0)) return null
        val gate = Waterbirth.Gate.entries.firstOrNull { it.position == position }
        if (gate != null) return gate(context, leg, gate)
        val support = Waterbirth.support(position) ?: return "unknown Waterbirth barrier at $position"
        return support(context, leg, support)
    }

    private suspend fun gate(context: LegContext, leg: PlanLeg, gate: Waterbirth.Gate): String? {
        val bot = context.bot
        if (!Waterbirth.open(bot.world, gate)) {
            if (leg.from.x < gate.position.x) {
                val pad = gate.pads.firstOrNull { tile -> bot.world.locator.findPlayers(tile, 0) {
                    it !== bot && it.state == EntityState.ACTIVE && it.isAlive && it.position == tile
                }.isEmpty() } ?: return "both Waterbirth pressure pads are occupied"
                context.walk(pad, 0)?.let { return it }
                if (!waitFor(45.seconds) { Waterbirth.open(bot.world, gate) || !bot.isAlive || bot.state != EntityState.ACTIVE })
                    return "no partner opened the Waterbirth pressure door"
            } else {
                val door = context.find(gate.position, gate.id) ?: return "the Waterbirth pressure door is missing"
                if (!context.handler.interactions.interactOnLeg(1, door) ||
                    !waitFor(5.seconds) { Waterbirth.open(bot.world, gate) }) return "could not open the return pressure door"
            }
        }
        if (!bot.isAlive || bot.state != EntityState.ACTIVE) return "the Waterbirth trip was interrupted"
        context.walk(leg.to, 0)?.let { return it }
        return null
    }

    private suspend fun support(context: LegContext, leg: PlanLeg, support: Waterbirth.Support): String? {
        val bot = context.bot
        var assisted: Npc? = null
        try {
            if (!Waterbirth.open(bot.world, support)) {
                context.walk(leg.from, 0)?.let { return it }
                if (leg.from == support.inside) {
                    val prop = context.findNpc(support.position, setOf(support.npc), 0) ?: return "the return support is missing"
                    assisted = prop
                    if (!context.handler.interactions.interactOnLeg(2, prop)) return "could not strike the return support"
                } else {
                    if (!bot.combat.weapon.isRanged && !bot.combat.magic.isCasting) return "Waterbirth support cooperation needs a ranged or magic loadout"
                    assisted = Waterbirth.Support.entries.filter { it != support }.mapNotNull { other ->
                        context.findNpc(other.position, setOf(other.npc), 0)
                    }.firstOrNull {
                        Waterbirth.canStrike(Waterbirth.support(it)!!, bot.position, CombatDamageType.RANGED, it, it)
                    } ?: return "no opposing Waterbirth support can be assisted"
                    if (!context.handler.interactions.interactOnLeg(2, assisted)) return "could not strike the opposing support"
                }
                if (!waitFor(45.seconds) { Waterbirth.open(bot.world, support) || !bot.isAlive || bot.state != EntityState.ACTIVE })
                    return "a partner did not knock down this Waterbirth support"
            }
            if (!bot.isAlive || bot.state != EntityState.ACTIVE) return "the Waterbirth trip was interrupted"
            return context.walk(leg.to, 0)
        } finally {
            if (assisted != null && bot.combat.target === assisted) bot.combat.target = null
        }
    }
}
