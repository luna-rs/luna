package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.bot.zone.SubZone
import game.skill.runecrafting.essenceMine.EssenceMine
import game.skill.runecrafting.essenceMine.EssenceMineWizard
import kotlin.time.Duration.Companion.seconds

/**
 * Uses every essence wizard's teleport option and the mine's saved return destination.
 *
 * @author lare96
 */
object EssenceMineCrossing : CrossingHandler {

    const val ID = "essence_mine"

    /**
     * A portal route is available only for the wizard who last teleported the player.
     */
    fun exitFlag(wizard: EssenceMineWizard): String = "essence_exit_${wizard.name.lowercase()}"

    /**
     * Bot packet options include Attack in slot three; Aubury's teleport is therefore slot four.
     */
    fun option(wizard: EssenceMineWizard): Int = if (wizard == EssenceMineWizard.AUBURY) 4 else 2

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "essence mine crossing has no action"
        val bot = context.bot
        val npcId = action.npcId
        if (npcId != null) {
            val wizard = EssenceMineWizard.forNpc(npcId) ?: return "unsupported essence wizard $npcId"
            if (!SubZone.ESSENCE_MINE.area.contains(bot)) {
                context.walk(leg.from, 0)?.let { return it }
                val npc = context.findNpc(leg.from, setOf(npcId), 16) ?: return "essence wizard $npcId is missing"
                val clicked = context.handler.interactions.interactOnLeg(option(wizard), npc)
                if (!clicked && !SubZone.ESSENCE_MINE.area.contains(bot)) return "could not reach essence wizard $npcId"
                if (!waitFor(30.seconds) { SubZone.ESSENCE_MINE.area.contains(bot) && !bot.isLocked }) {
                    return "essence wizard $npcId did not teleport the bot"
                }
            }
            // Entry lands at a random point in the mine. Resume the route from its known node.
            return context.walk(leg.to, 0)
        }
        if (action.objectId != EssenceMine.PORTAL) return "unsupported essence mine exit"
        if (EssenceMine.returnWizard(bot).exit != leg.to) return "the mine portal returns to a different wizard"
        val position = action.objectPosition ?: return "essence portal has no position"
        val portal = context.find(position, EssenceMine.PORTAL) ?: return "essence portal is missing"
        val clicked = context.handler.interactions.interactOnLeg(1, portal)
        if (!clicked && !context.arrived(leg, 2)) return "could not reach essence portal"
        return if (context.awaitArrival(leg, 2)) null else "essence portal did not return the bot to ${leg.to}"
    }
}
