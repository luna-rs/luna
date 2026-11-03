package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.skill.woodcutting.cutTree.Axe
import game.skill.woodcutting.jungle.KharaziJungle
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import kotlin.time.Duration.Companion.seconds

/**
 * Uses the native weak jungle-chopping interaction until its real two-tile landing. A cleared stump can be
 * walked normally; every retry retains the source's usable-axe and machete requirements and ordinary failure.
 * No log, experience, depletion, quest state or movement is manufactured by the walker.
 *
 * @author lare96
 */
object KharaziJungleCrossing : CrossingHandler {
    const val ID = "kharazi_jungle"
    const val READY = "kharazi_jungle_tools"

    /**
     * Native current-level tool checks; banked tools are not treated as carried admission.
     */
    fun ready(player: Player): Boolean = Axe.computeAxeType(player) != null && KharaziJungle.hasMachete(player)

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the jungle crossing has no action"
        val id = action.objectId ?: return "the jungle crossing has no object"
        val position = action.objectPosition ?: return "the jungle crossing has no placement"
        if (id !in KharaziJungle.STUMPS || KharaziJungle.destination(leg.from, position) != leg.to)
            return "the crossing does not match a native two-tile jungle cut"
        repeat(3) {
            if (context.arrived(leg, 0)) return null
            if (context.interrupted || context.bot.state != EntityState.ACTIVE || !context.bot.isAlive || !ready(context.bot))
                return "the native jungle crossing needs a usable carried axe and machete"
            context.walk(leg.from, 0)?.let { return it }
            val obj = context.find(position, id)
            if (obj == null) {
                val stump = context.find(position, KharaziJungle.STUMPS.getValue(id))
                    ?: return "the source jungle obstacle is missing"
                if (stump.state == EntityState.ACTIVE) return context.walk(leg.to, 0)
            } else {
                if (!ready(context.bot) || !context.handler.interactions.interactOnLeg(1, obj))
                    return "could not start the native jungle cut"
                if (context.awaitArrival(leg, 0, 40.seconds)) return null
            }
        }
        return "the native jungle cut did not reach its successful landing"
    }
}
