package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.predef.woodcutting
import game.content.canoes.CanoeStation
import game.content.canoes.CanoeType
import game.content.canoes.Canoes
import game.content.canoes.Canoes.canoeChoice
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlin.time.Duration.Companion.seconds

/**
 * Builds and launches a canoe through its native station interactions, then waits for the river journey.
 *
 * @author lare96
 */
object CanoeCrossing : CrossingHandler {

    const val ID = "canoe"

    /**
     * The flag for each type reflects current Woodcutting and a usable axe in hand or inventory.
     */
    fun flag(type: CanoeType): String = "canoe_${type.name.lowercase()}"

    /**
     * Mirrors the player requirements, including boosted and drained Woodcutting levels.
     */
    fun canBuild(player: Player, type: CanoeType): Boolean =
        player.woodcutting.level >= type.level && Axe.computeAxeType(player) != null

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        if (context.arrived(leg, 0)) return null
        val action = leg.action ?: return "the canoe leg has no station action"
        val position = action.objectPosition ?: return "the canoe leg has no tree position"
        val from = action.objectId?.let(CanoeStation::forTree) ?: return "unknown canoe station at $position"
        val to = CanoeStation.entries.firstOrNull { it.landing == leg.to } ?: return "unknown canoe landing ${leg.to}"
        val minimum = CanoeType.entries.firstOrNull { to in from.destinations(it) }
            ?: return "no canoe can travel from $from to $to"
        val bot = context.bot
        if (!canBuild(bot, minimum)) return "the bot lacks the Woodcutting level or axe to canoe to $to"
        context.walk(leg.from, 0)?.let { return it }
        val tree = context.find(position, checkNotNull(from.tree).id) ?: return "no canoe tree at $from"
        if (bot.overlays.hasWindow()) return "a window is already open at $from"

        fun state() = Canoes.state(bot, from)
        suspend fun clickStation() = context.handler.interactions.interactOnLeg(1, tree)
        val widgets = bot.overlays
        var warning: OptionDialogue? = null
        try {
            if (state() == Canoes.TREE && !clickStation()) return "could not chop the canoe tree at $from"
            if (state() in setOf(Canoes.TREE, Canoes.FALLING) &&
                !waitFor(90.seconds) { state() == Canoes.FALLEN }) return "the canoe tree at $from did not fall"

            val type = CanoeType.of(state()) ?: bot.canoeChoice ?: minimum
            if (to !in from.destinations(type)) return "the existing $type at $from cannot reach $to"
            if (state() == Canoes.FALLEN) {
                if (!canBuild(bot, type) || !clickStation()) return "could not start shaping a $type at $from"
                if (bot.canoeChoice == null) {
                    if (!waitFor { widgets.getOverlay(Canoes.ShapingInterface::class.java)?.station == from } ||
                        !bot.output.clickButton(type.button)) return "could not select a $type at $from"
                }
                if (!waitFor(120.seconds) { state() == type.shapedState }) return "the $type at $from was not shaped"
            }

            if (state() == type.shapedState && !clickStation()) return "could not push the $type at $from"
            if (!waitFor { state() == type.afloatState && !bot.isLocked }) return "the $type at $from did not float"
            if (!clickStation() || !waitFor {
                    widgets.getOverlay(Canoes.DestinationMap::class.java)?.let { it.station == from && it.type == type } == true
                } || !bot.output.clickButton(to.mapButton)) return "could not select $to on the canoe map"

            if (to == CanoeStation.WILDERNESS_POND) {
                if (!waitFor { widgets.getOverlay(OptionDialogue::class.java)?.options?.contains(Canoes.ACCEPT_WILDERNESS) == true }) {
                    return "the canoe did not offer the Wilderness warning"
                }
                warning = widgets.getOverlay(OptionDialogue::class.java)
                if (!context.handler.widgets.clickDialogueOption(Canoes.ACCEPT_WILDERNESS)) {
                    return "could not confirm the Wilderness canoe trip"
                }
            }
            return if (context.awaitArrival(leg, 0, 30.seconds)) null else "the canoe did not reach $to"
        } finally {
            if (widgets.getOverlay(Canoes.ShapingInterface::class.java)?.station == from ||
                widgets.getOverlay(Canoes.DestinationMap::class.java)?.station == from ||
                warning != null && widgets.getOverlay(OptionDialogue::class.java) === warning) {
                widgets.closeWindows()
            }
        }
    }
}
