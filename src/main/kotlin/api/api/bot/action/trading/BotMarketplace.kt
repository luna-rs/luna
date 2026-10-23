package api.bot.action.trading

import api.bot.zone.SubZone
import api.bot.zone.Zone
import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.area.SimpleBoxArea
import io.luna.game.model.mob.bot.Bot

/**
 * Trading rectangles and suitable portable production skills for each bot marketplace.
 *
 * Arrival uses the parent zone's existing anchor and travel fallback. The activity subzone identifies
 * the session destination; [anchors] identify the precise ground-floor tiles used for work and trading.
 * Overlapping rectangles are deduplicated. Collision is checked when positions are requested so closed
 * doors or newly blocked tiles are not permanently cached as usable destinations.
 *
 * Skill associations describe bank-only production. They never provide a furnace, range, anvil, wheel
 * or resource that the selected recipe still needs. Varrock remains the general-purpose marketplace.
 *
 * @property parent Existing parent zone supplying arrival, banking and travel.
 * @property activityZone Saved zoned-script destination for this market.
 * @property skills Skills whose portable production fits the market; empty means general-purpose.
 * @property anchors Rectangles supplied for trading and stationary production.
 * @author lare96
 */
enum class BotMarketplace(val parent: Zone, val activityZone: SubZone, val skills: Set<Int>,
                          val anchors: List<SimpleBoxArea>) {
    VARROCK(Zone.VARROCK, SubZone.VARROCK_MARKETPLACE, emptySet(), listOf(
        SimpleBoxArea.of(3173, 3433, 3178, 3445),
        SimpleBoxArea.of(3180, 3433, 3184, 3447),
        SimpleBoxArea.of(3191, 3433, 3197, 3447),
        SimpleBoxArea.of(3173, 3448, 3190, 3454),
        SimpleBoxArea.of(3173, 3427, 3197, 3432)
    )),
    EDGEVILLE(Zone.EDGEVILLE, SubZone.EDGEVILLE_MARKETPLACE,
        setOf(SKILL_FLETCHING, SKILL_CRAFTING, SKILL_HERBLORE, SKILL_MAGIC, SKILL_PRAYER), listOf(
            SimpleBoxArea.ofRadius(3092, 3488, 3, 11),
            SimpleBoxArea.ofRadius(3095, 3496, 3, 3),
            SimpleBoxArea.ofRadius(3086, 3488, 4, 13),
            SimpleBoxArea.ofRadius(3092, 3485, 7, 3),
            SimpleBoxArea.ofRadius(3090, 3500, 9, 2)
        )),
    SEERS_VILLAGE(Zone.SEERS_VILLAGE, SubZone.SEERS_VILLAGE_MARKETPLACE,
        setOf(SKILL_FLETCHING, SKILL_CRAFTING, SKILL_HERBLORE), listOf(
            SimpleBoxArea.ofRadius(2721, 3490, 9, 4),
            SimpleBoxArea.ofRadius(2720, 3484, 10, 3)
        )),
    AL_KHARID(Zone.AL_KHARID, SubZone.AL_KHARID_MARKETPLACE,
        setOf(SKILL_CRAFTING, SKILL_COOKING, SKILL_SMITHING, SKILL_MAGIC), listOf(
            SimpleBoxArea.ofRadius(3269, 3161, 9, 13)
        ));

    companion object {
        /**
         * Resolves marketplace overlays without changing ordinary resource and facility subzones.
         */
        fun forZone(zone: SubZone): BotMarketplace? = entries.firstOrNull { it.activityZone == zone }

        /**
         * Selects an available trading market, using proximity for dextrous bots and variety otherwise.
         * An unavailable world falls back to Varrock, whose normal script startup rejects missing tiles.
         */
        fun select(bot: Bot): BotMarketplace {
            val available = entries.filter { it.positions.isNotEmpty() }
            return if (bot.personality.isDextrous) {
                available.minByOrNull { it.arrival.computeLongestDistance(bot.position) } ?: VARROCK
            } else available.randomOrNull() ?: VARROCK
        }
    }

    /**
     * Parent-zone arrival anchor; precise work tiles are selected after travel or banking.
     */
    val arrival: Position get() = parent.anchor

    /**
     * Unique geometry retained independently of mutable collision state.
     */
    private val tiles by lazy { anchors.flatMap { it.computePositions() }.distinct() }

    /**
     * Currently unblocked work/trade tiles, with no duplicate weighting from overlapping rectangles.
     */
    val positions: List<Position> get() = tiles.filter { !world.collisionManager.isBlocked(it, false) }

    /**
     * Checks the precise ground-floor work area, excluding gaps inside the enclosing subzone box.
     */
    fun contains(position: Position): Boolean = position.z == 0 && anchors.any { position in it }

    /**
     * Selects a usable trading position. Callers handling a changing world may use positions.randomOrNull().
     *
     * @throws NoSuchElementException If no configured tile is currently available.
     */
    fun randomPosition(): Position = positions.random()
}
