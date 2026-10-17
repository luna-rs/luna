package api.bot.webwalk.walk

import api.bot.Suspendable.waitFor
import api.bot.action.BotActionHandler
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.plan.BotCapabilities
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.TeleportAvailability
import api.bot.webwalk.plan.WebWalkPlan
import api.bot.webwalk.walk.crossing.CrossingContext
import api.bot.webwalk.walk.crossing.CrossingHandlers
import api.predef.*
import api.predef.ext.*
import engine.obj.Trapdoor
import game.item.degradable.jewellery.TeleportJewellery
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.`object`.GameObject

/**
 * Takes a bot along a [WebWalkPlan], one leg at a time, until it is at the destination.
 *
 * - Walks go through the navigator of the bot. A walk that is followed by another walk is let go of a few tiles short, so
 *   that the bot doesn't stop between them.
 * - Doors, gates and curtains are opened if they are closed, and then walked through. If another bot opened the door in the
 *   meantime, that's fine.
 * - Ladders and stairs are climbed with the option that the plan says, and when that is "Climb" the bot also chooses up or
 *   down in the dialogue that asks.
 * - Trapdoors are opened if they are closed, then climbed down.
 * - Teleports are cast, rubbed or typed as a player would. Ships and fairy rings are not supported yet, so a plan with one
 *   of those fails when it gets to it.
 * - Crossings, such as the Al Kharid toll gate, are left to the [api.bot.webwalk.walk.crossing.CrossingHandler] that the
 *   data names.
 *
 * Before a leg that has requirements the bot is checked against them again, because it may have spent or dropped what it
 * needed since the trip was planned.
 *
 * Nothing is retried beyond a door that fails to open, and the walk stops at the first leg that fails. The bot is also done
 * as soon as it is within the radius of the destination, even if the plan has legs left.
 *
 * @param bot The bot.
 * @param handler The actions of the bot.
 * @param destination Where the bot is going.
 * @param radius How close to the destination, in tiles on the same plane, is close enough.
 *
 * @author Hydrozoa
 */
class WebWalkExecutor(private val bot: Bot,
                      private val handler: BotActionHandler,
                      private val destination: Position,
                      private val radius: Int = 0) {

    companion object {

        /**
         * How long to wait for a door to open, a ladder to be climbed, or a dialogue to show.
         */
        private val ACTION_TIMEOUT = CrossingContext.ACTION_TIMEOUT

        /**
         * How many times a door is tried before giving up on it.
         */
        private const val DOOR_ATTEMPTS = 3

        /**
         * How close, in tiles, a bot has to be to the end of a walk for it to carry on with another walk.
         */
        private const val WALK_SLACK = 3

        /**
         * Determines if a climb goes up, according to where it starts and where it ends.
         *
         * A climb changes the plane, or else it goes into or out of a dungeon, which is 6400 tiles north of the surface.
         */
        fun isUp(from: Position, to: Position): Boolean {
            if (from.z != to.z) {
                return to.z > from.z
            }
            val north = to.y - from.y
            return if (Math.abs(north) > 3000) north < 0 else true
        }

        /**
         * Finds the number of the menu option of an object, from 1, which is the first one that has a name.
         *
         * @param actions The actions of the object.
         * @param name The name of the option wanted, with any case.
         * @return The number of the option, or `null` if the object has none of that name, or it is past the third.
         */
        fun optionNumber(actions: List<String?>, name: String): Int? {
            val index = actions.indexOfFirst { it != null && it.equals(name, ignoreCase = true) }
            return if (index in 0..2) index + 1 else null
        }
    }

    /**
     * What the bot is moved about with, which the crossing handlers use too.
     */
    private val context = CrossingContext(bot, handler)

    /**
     * Takes the bot along a plan.
     *
     * @param plan The plan.
     * @return How it went.
     */
    suspend fun execute(plan: WebWalkPlan): WebWalkResult {
        for ((index, leg) in plan.legs.withIndex()) {
            if (atDestination()) {
                return done(plan, index, "Arrived early, before leg ${index + 1} of ${plan.legs.size}.")
            }
            val next = plan.legs.getOrNull(index + 1)
            val failure = unmet(leg) ?: when (leg.type) {
                EdgeType.WALK -> walk(leg.to, if (next?.type == EdgeType.WALK) WALK_SLACK else 0)
                EdgeType.DOOR, EdgeType.GATE, EdgeType.CURTAIN -> crossDoor(leg)
                EdgeType.CROSSING -> crossing(leg)
                EdgeType.LADDER, EdgeType.STAIR -> climb(leg)
                EdgeType.TRAPDOOR -> climbTrapdoor(leg)
                EdgeType.TELEPORT -> teleport(leg)
                else -> "${leg.type} legs are not supported yet"
            }
            if (failure != null) {
                return WebWalkResult(false, "Leg ${index + 1} of ${plan.legs.size} (${leg.type} to ${leg.to}) failed: " +
                        failure, plan, index)
            }
        }
        if (!atDestination()) {
            return WebWalkResult(false, "The plan is done, but the bot is at ${bot.position}, not near $destination.",
                                 plan, plan.legs.size)
        }
        return done(plan, plan.legs.size, "Arrived.")
    }

    /**
     * Makes the result of a walk that worked.
     */
    private fun done(plan: WebWalkPlan, legs: Int, message: String): WebWalkResult {
        bot.navigator.cancel()
        return WebWalkResult(true, message, plan, legs)
    }

    /**
     * Determines if the bot is close enough to the destination.
     */
    private fun atDestination(): Boolean =
        bot.position.z == destination.z && bot.position.computeLongestDistance(destination) <= radius

    /**
     * Determines if the bot is within a number of tiles of a position, on the same plane.
     */
    private fun near(position: Position, tiles: Int): Boolean = context.near(position, tiles)

    /**
     * Checks that the bot still meets the requirements of a leg, because it may have spent or dropped what it needed since
     * the trip was planned.
     *
     * @return `null` if it does, or why it doesn't.
     */
    private fun unmet(leg: PlanLeg): String? {
        if (leg.requirements.isEmpty || BotCapabilities.snapshot(bot).meets(leg.requirements)) {
            return null
        }
        val coins = if (leg.requirements.coins > 0) " (it has ${bot.inventory.computeAmountForId(CapabilitySnapshot.COINS)} coins)" else ""
        return "the bot no longer meets ${leg.requirements}$coins"
    }

    /**
     * Walks to a position.
     *
     * @param target The position.
     * @param slack How many tiles short the bot may be let go, because another walk comes next, or `0` to stop exactly.
     * @return `null` if the bot got there, or why it didn't.
     */
    private suspend fun walk(target: Position, slack: Int): String? = context.walk(target, slack, ::atDestination)

    /**
     * Finds an object by position and one of its ids, or `null` if it is not there.
     */
    private fun find(position: Position, vararg ids: Int): GameObject? = context.find(position, *ids)

    /**
     * Crosses a crossing with the handler that its data names.
     *
     * @return `null` if the bot got through, or why it didn't.
     */
    private suspend fun crossing(leg: PlanLeg): String? {
        val name = leg.action?.handler ?: return "the leg has no handler"
        val crossingHandler = CrossingHandlers.forId(name) ?: return "there is no crossing handler '$name'"
        return crossingHandler.cross(context, leg)
    }

    /**
     * Opens a door, gate or curtain if it is closed, and walks through it.
     *
     * @return `null` if the bot got through, or why it didn't.
     */
    private suspend fun crossDoor(leg: PlanLeg): String? {
        val action = leg.action ?: return "the leg has no object"
        val closedId = action.objectId ?: return "the leg has no object id"
        val position = action.objectPosition ?: return "the leg has no object position"
        var lastProblem = "the door did not open"
        repeat(DOOR_ATTEMPTS) {
            val door = find(position, closedId)
            if (door != null) {
                if (!handler.interactions.interact(1, door)) {
                    lastProblem = "could not reach the door $closedId at $position"
                    return@repeat
                }
                if (!waitFor(ACTION_TIMEOUT) { find(position, closedId) == null }) {
                    lastProblem = "the door $closedId at $position did not open"
                    return@repeat
                }
            }
            val failure = walk(leg.to, 0)
            if (failure == null) {
                return null
            }
            lastProblem = failure
        }
        return lastProblem
    }

    /**
     * Climbs a ladder or staircase.
     *
     * @return `null` if the bot arrived, or why it didn't.
     */
    private suspend fun climb(leg: PlanLeg): String? {
        val action = leg.action ?: return "the leg has no object"
        val id = action.objectId ?: return "the leg has no object id"
        val position = action.objectPosition ?: return "the leg has no object position"
        val obj = find(position, id) ?: return "there is no object $id at $position"
        val name = action.option ?: "Climb"
        val option = optionNumber(obj.def().actions, name) ?: return "object $id has no '$name' option"

        if (!handler.interactions.interact(option, obj)) {
            return "could not reach the object $id at $position"
        }
        if (name.equals("Climb", ignoreCase = true)) {
            // "Climb" asks which way, unless the bot is already on its way.
            waitFor(ACTION_TIMEOUT) { bot.overlays[OptionDialogue::class] != null || arrived(leg) }
            if (!arrived(leg) && !handler.widgets.clickDialogueOption(if (isUp(leg.from, leg.to)) 1 else 2)) {
                return "could not choose a direction"
            }
        }
        return if (awaitArrival(leg)) null else
            "the bot did not arrive near ${leg.to}, it is at ${bot.position}"
    }

    /**
     * Opens a trapdoor if it is closed, then climbs down it.
     *
     * @return `null` if the bot arrived, or why it didn't.
     */
    private suspend fun climbTrapdoor(leg: PlanLeg): String? {
        val action = leg.action ?: return "the leg has no object"
        val id = action.objectId ?: return "the leg has no object id"
        val position = action.objectPosition ?: return "the leg has no object position"
        val trapdoor = Trapdoor.entries.firstOrNull { it.closed == id || it.open == id }
            ?: return "object $id is not a known trapdoor"
        val ids = listOfNotNull(trapdoor.closed, trapdoor.open).toIntArray()

        var obj = find(position, *ids) ?: return "there is no trapdoor $id at $position"
        if (obj.id == trapdoor.closed) {
            if (!handler.interactions.interact(1, obj)) {
                return "could not reach the trapdoor at $position"
            }
            if (!waitFor(ACTION_TIMEOUT) { find(position, trapdoor.open) != null }) {
                return "the trapdoor at $position did not open"
            }
            obj = find(position, trapdoor.open) ?: return "the trapdoor at $position closed again"
        }
        if (!handler.interactions.interact(1, obj)) {
            return "could not reach the open trapdoor at $position"
        }
        return if (awaitArrival(leg)) null else
            "the bot did not arrive near ${leg.to}, it is at ${bot.position}"
    }

    /**
     * Teleports the bot, by casting a spell, using a piece of jewellery, or going home.
     *
     * The teleport spells and jewellery are taken from the game, and so are the ways that they are used: a spell is cast by
     * clicking its button, jewellery is rubbed and a destination is chosen from the dialogue that it opens, and going home
     * is the `home` command. The jewellery is taken out of the equipment into the inventory if it is worn, as it is only
     * rubbed from there.
     *
     * @return `null` if the bot arrived, or why it didn't.
     */
    private suspend fun teleport(leg: PlanLeg): String? {
        val teleport = leg.teleport ?: return "the leg has no teleport"
        when (teleport.kind) {
            TeleportKind.SPELL -> {
                val spell = TeleportSpell.entries.firstOrNull { it.name == teleport.key }
                    ?: return "there is no spell '${teleport.key}'"
                bot.output.clickButton(spell.button)
            }

            TeleportKind.HOME -> bot.output.sendCommand("home")
            TeleportKind.JEWELLERY -> {
                val failure = useJewellery(teleport.key, teleport.option)
                if (failure != null) {
                    return failure
                }
            }
        }
        return if (awaitArrival(leg)) null else
            "the teleport ${teleport.id} did not take the bot near ${leg.to}, it is at ${bot.position}"
    }

    /**
     * Rubs a piece of teleport jewellery and chooses a destination.
     *
     * @param key The name of the jewellery.
     * @param option The number of the destination, from 1.
     * @return `null` if the destination was chosen, or why it wasn't.
     */
    private suspend fun useJewellery(key: String?, option: Int?): String? {
        val jewellery = TeleportJewellery.entries.firstOrNull { it.name == key } ?: return "there is no jewellery '$key'"
        val choice = option ?: return "the teleport has no destination option"
        val item = handler.retrieveAny(TeleportAvailability.chargedIds(jewellery).map { Item(it) })
            ?: return "the bot has no ${jewellery.name.lowercase()} with a charge"
        if (!handler.inventory.clickItem(4, item.id)) {
            return "could not rub ${item.name}"
        }
        if (!waitFor(ACTION_TIMEOUT) { OptionDialogue::class in bot.overlays }) {
            return "the destinations of ${item.name} were not shown"
        }
        return if (handler.widgets.clickDialogueOption(choice)) null else "could not choose destination $choice"
    }

    /**
     * Determines if the bot has landed where a climb leads.
     */
    private fun arrived(leg: PlanLeg): Boolean = context.arrived(leg)

    /**
     * Waits until the bot has landed where a climb leads and is free to move again. The climb locks the bot until it is
     * done, and a walk that starts while it is locked is dropped, which is why being in place is not enough.
     *
     * @return `true` if the bot landed and was free before the timeout.
     */
    private suspend fun awaitArrival(leg: PlanLeg): Boolean = context.awaitArrival(leg)
}
