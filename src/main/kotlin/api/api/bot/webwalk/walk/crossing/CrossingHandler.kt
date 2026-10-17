package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg

/**
 * Crosses the edges of the web that are more than a click on a door, ladder or the like, such as a gate that charges a toll.
 * An edge names its handler in the `handler` field of its data, and [CrossingHandlers] finds it.
 *
 * @author Hydrozoa
 */
fun interface CrossingHandler {

    /**
     * Takes the bot across the edge of a leg. The bot is at or near where the leg starts, and the requirements of the leg
     * have just been checked.
     *
     * @param context What a handler needs to move the bot and find things.
     * @param leg The leg to cross.
     * @return `null` if the bot crossed, or why it didn't.
     */
    suspend fun cross(context: CrossingContext, leg: PlanLeg): String?
}

/**
 * The crossing handlers, keyed by the name that the data gives them.
 *
 * @author Hydrozoa
 */
object CrossingHandlers {

    /**
     * The handlers. A crossing in the data whose handler is not here is a mistake, which [api.bot.webwalk.walk.WebWalker]
     * reports when the web is loaded.
     */
    private val handlers: Map<String, CrossingHandler> = mapOf(BorderGateCrossing.ID to BorderGateCrossing)

    /**
     * The names of every handler.
     */
    val ids: Set<String>
        get() = handlers.keys

    /**
     * @return The handler with [id], or `null` if there is none.
     */
    fun forId(id: String): CrossingHandler? = handlers[id]
}
