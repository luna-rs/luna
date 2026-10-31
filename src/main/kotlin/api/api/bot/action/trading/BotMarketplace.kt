package api.bot.action.trading

import api.bot.zone.SubZone
import api.bot.zone.Zone
import api.bot.script.BotScript
import api.predef.*
import io.luna.game.model.EntityState
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
            val available = entries.filter { it.hasAvailableTradingSpot(bot) }
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
     * Maximum simultaneous trading sessions at this market. Production scripts still use the full work area.
     */
    val tradingCapacity: Int
        get() = when (this) {
            VARROCK -> 48
            EDGEVILLE, AL_KHARID -> 32
            SEERS_VILLAGE -> 24
        }

    /**
     * Session-owned positions, spaced apart so neighbouring traders remain distinguishable.
     */
    private val tradingSpots by lazy { BotMarketplaceSpotPool<BotScript, Position>(tradingCapacity) }

    /**
     * Currently unblocked work/trade tiles, with no duplicate weighting from overlapping rectangles.
     */
    val positions: List<Position> get() = tiles.filter { !world.collisionManager.isBlocked(it, false) }

    /**
     * Checks capacity without consuming a spot before a trading script starts.
     */
    fun hasAvailableTradingSpot(bot: Bot): Boolean {
        val occupied = occupiedPositions(bot)
        return tradingSpots.hasSpace(tradingCandidates(), { it !in occupied }, ::activeTrader)
    }

    /**
     * Reserves an exclusive, currently unoccupied trading tile until banking, pause, or completion releases it.
     */
    fun reserveTradingSpot(owner: BotScript): Position? {
        val occupied = occupiedPositions(owner.bot)
        return tradingSpots.claim(owner, tradingCandidates(), { it !in occupied }, ::activeTrader)
    }

    /**
     * Returns a session's current reservation without creating one.
     */
    fun tradingSpot(owner: BotScript): Position? = tradingSpots.position(owner)

    /**
     * Returns a vacated spot to the back of this market's randomized rotation.
     */
    fun releaseTradingSpot(owner: BotScript) = tradingSpots.release(owner)

    /**
     * Keeps portable production and social activity away from reserved trading positions.
     */
    fun canWorkAt(bot: Bot, position: Position): Boolean =
        !tradingSpots.reserved(position) && unoccupied(position, bot)

    /**
     * Supplies currently free work positions for marketplace activities that do not own a trading lease.
     */
    fun availableWorkPositions(bot: Bot): List<Position> {
        val occupied = occupiedPositions(bot)
        return positions.filter { !tradingSpots.reserved(it) && it !in occupied }
    }

    /**
     * Keeps one empty tile between the finite pool's possible trader positions.
     */
    private fun tradingCandidates(): List<Position> = positions.filter { it.x % 2 == 0 && it.y % 2 == 0 }

    /**
     * Revalidates real occupancy independently of collision geometry and outstanding reservations.
     */
    private fun unoccupied(position: Position, bot: Bot): Boolean =
        world.players.none { it !== bot && it.state == EntityState.ACTIVE && it.position == position } &&
                world.npcs.none { it.state == EntityState.ACTIVE && it.position == position }

    /**
     * Captures occupancy once when inspecting a whole position pool.
     */
    private fun occupiedPositions(bot: Bot): Set<Position> = buildSet {
        world.players.filter { it !== bot && it.state == EntityState.ACTIVE }.forEach { add(it.position) }
        world.npcs.filter { it.state == EntityState.ACTIVE }.forEach { add(it.position) }
    }

    /**
     * Inactive scripts cannot keep a trading position after interruption or logout.
     */
    private fun activeTrader(owner: BotScript): Boolean =
        owner.bot.state == EntityState.ACTIVE && owner.isRunning()

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
