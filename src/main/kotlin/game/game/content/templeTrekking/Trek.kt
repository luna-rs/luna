package game.content.templeTrekking

import api.predef.*
import kotlin.random.Random
import game.content.templeTrekking.TempleTrekking.trek
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.Varbit

/**
 * The kinds of event a trek can run into.
 *
 * @property weight How often the event comes up, against the others, where the place doesn't decide it.
 * @property points Trek points for completing the event, on top of points for kills.
 */
enum class TrekEventType(val weight: Int, val points: Int = 0) {
    SNAKES(4),
    SNAILS(4),
    GHASTS(4),
    JUVINATES(3),
    SHADES(0),
    TENTACLES(0, 4_000),
    BOG(2, 500),
    RIVER(2, 500),
    BRIDGE(2, 500),
    ABIDOR_CRANK(1);

    /**
     * Whether the event is a fight.
     */
    val fight get() = this in setOf(SNAKES, SNAILS, GHASTS, JUVINATES, SHADES, TENTACLES)

    companion object {

        /**
         * The events that can come up anywhere.
         */
        private val ANYWHERE = entries.filter { it.weight > 0 }

        /**
         * Picks the event for a place on the route.
         */
        fun pick(spot: TrekSpot?): TrekEventType = when (spot) {
            TrekSpot.MORTTON -> SHADES
            TrekSpot.BOAT -> TENTACLES
            null -> {
                var roll = Random.nextInt(ANYWHERE.sumOf { it.weight })
                ANYWHERE.first { roll -= it.weight; roll < 0 }
            }
        }
    }
}

/**
 * A trek under way.
 *
 * @property player The guide.
 * @property villager The villager being guided.
 * @property route The route taken.
 */
class Trek(val player: Player, val villager: TrekVillager, val route: TrekRoute) {

    companion object {

        /**
         * The fewest and most points a trek can be worth.
         */
        const val MIN_POINTS = 325
        const val MAX_POINTS = 8_000
    }

    /**
     * The events still to come, by the index of the leg they end.
     */
    val plan = HashMap<Int, TrekEventType>()

    /**
     * The next leg to travel.
     */
    var leg = 0

    /**
     * The trek's points so far.
     */
    var points = 0
        private set

    /**
     * How many events the trek has run into.
     */
    var events = 0

    /**
     * The villager's hitpoints, carried from one event to the next, or -1 before the first.
     */
    var villagerHealth = -1

    /**
     * Set while the player is being moved from one event's room into the next.
     */
    var moving = false

    init {
        val count = weighted(route.events)
        val spots = route.legs.indices.filter { route.legs[it].eventful }.shuffled().take(count)
        for (index in spots) {
            plan[index] = TrekEventType.pick(route.legs[index].spot)
        }
    }

    /**
     * Adds [amount] points, up to the most a trek can earn.
     */
    fun addPoints(amount: Int) {
        points = (points + amount).coerceAtMost(MAX_POINTS)
        sendPoints()
    }

    /**
     * Shows the trek's points on the trek varp, as the game kept them.
     */
    fun sendPoints() {
        player.sendVarbit(Varbit(TempleTrekking.POINTS_VARBIT, points))
    }

    /**
     * Evading an event makes another more likely: one more is put on a later leg that has none yet.
     */
    fun evaded(from: Int) {
        val later = route.legs.indices.filter { it > from && route.legs[it].eventful && it !in plan }
        if (later.isNotEmpty()) {
            val index = later.random()
            plan[index] = TrekEventType.pick(route.legs[index].spot)
        }
    }

    /**
     * Whether this trek is still the player's.
     */
    val active get() = player.trek === this

    private fun weighted(weights: Map<Int, Int>): Int {
        var roll = Random.nextInt(weights.values.sum())
        return weights.entries.first { roll -= it.value; roll < 0 }.key
    }
}
