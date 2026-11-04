package api.bot.social

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.world
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.area.SimpleBoxArea

/**
 * Shared standing places for idle visitors and alchemists around Varrock's fountain and castle approach.
 * Small patches leave the fountain, central approach, doors and the east-west road free. Live collision,
 * local reachability, nearby footprints and session reservations are checked before each journey.
 * Reservations belong to the game thread and are released on banking, pause, completion or abandonment.
 *
 * @author lare96
 */
object BotHomeGathering {
    /**
     * Inclusive ground-floor standing patches, grouped by landmark to distribute visitors between both.
     */
    val landmarks: List<List<SimpleBoxArea>> = listOf(
        listOf(
            SimpleBoxArea.of(3207, 3433, 3210, 3438),
            SimpleBoxArea.of(3215, 3433, 3218, 3438),
            SimpleBoxArea.of(3207, 3421, 3210, 3425),
            SimpleBoxArea.of(3216, 3421, 3221, 3425)
        ),
        listOf(
            SimpleBoxArea.of(3207, 3443, 3210, 3444),
            SimpleBoxArea.of(3215, 3443, 3218, 3444),
            SimpleBoxArea.of(3206, 3445, 3209, 3453),
            SimpleBoxArea.of(3216, 3445, 3220, 3453)
        )
    )

    /**
     * One clear tile between standing players; no fallback crowds them onto occupied or adjacent tiles.
     */
    const val MINIMUM_SPACING = 2

    /**
     * Idle visitors reconsider their place infrequently, after finishing their current exchange.
     */
    val REPOSITION_MILLIS = 90_000L..240_000L

    private val tiles by lazy { landmarks.map { patches -> patches.flatMap { it.computePositions() }.distinct() } }
    private val reservations = LinkedHashMap<BotScript, Position>()

    /**
     * Whether a position is a standing patch, rather than merely somewhere in the enclosing travel subzone.
     */
    fun contains(position: Position): Boolean = position.z == 0 &&
        landmarks.any { patches -> patches.any { position in it } }

    /**
     * Keeps a valid reserved place without rescanning the local population each script cycle.
     */
    fun settled(owner: BotScript): Boolean {
        val tile = reservations[owner] ?: return false
        return owner.bot.position == tile && !world.collisionManager.isBlocked(tile, false)
    }

    /**
     * Chooses a reachable free place, favouring the less occupied landmark and retaining loose spacing.
     * A failed reposition retains the old reservation so a full area does not displace settled visitors.
     */
    fun claim(owner: BotScript, reposition: Boolean = false): Position? {
        reservations.keys.removeIf { !active(it) }
        val bot = owner.bot
        val origin = bot.position
        if (origin.z != 0 || origin !in SubZone.VARROCK_HOME_GATHERING.area) return null
        val players = world.locator.findPlayers(SubZone.VARROCK_HOME_GATHERING.inside, 32) {
            it !== bot && it.state == EntityState.ACTIVE
        }.map { it.position }
        val npcs = world.locator.findNpcs(SubZone.VARROCK_HOME_GATHERING.inside, 32) {
            it.state == EntityState.ACTIVE
        }.flatMap { npc ->
            (0 until npc.size()).flatMap { x -> (0 until npc.size()).map { y -> npc.position.translate(x, y) } }
        }.toSet()
        val others = (reservations.filterKeys { it !== owner }.values + players).distinct()
        fun free(tile: Position): Boolean = !world.collisionManager.isBlocked(tile, false) &&
            tile !in npcs && others.none { it.isWithinDistance(tile, MINIMUM_SPACING - 1) }
        val old = reservations[owner]
        if (!reposition && old != null && free(old)) return old
        val reached = reachable(origin)
        val groups = tiles.indices.shuffled().sortedBy { group -> others.count { it in tiles[group] } }
        val selected = groups.firstNotNullOfOrNull { group ->
            val available = tiles[group].filter { it in reached && free(it) && (!reposition || it != old) }
            val company = available.filter { tile -> others.count { it.isWithinDistance(tile, 5) } in 1..4 }
            company.ifEmpty { available }.randomOrNull()
        }
        if (selected != null) reservations[owner] = selected
        return selected ?: old?.takeIf { free(it) && it in reached }
    }

    /**
     * Frees this activity's place without affecting a replacement script's reservation.
     */
    fun release(owner: BotScript) {
        reservations.remove(owner)
    }

    /**
     * Floods only the bounded square and castle approach through live cardinal player collision.
     * The exact web-walk must still succeed before the caller exposes social or casting availability.
     */
    private fun reachable(origin: Position): Set<Position> {
        val bounds = SubZone.VARROCK_HOME_GATHERING.area
        val collision = world.collisionManager
        val reached = hashSetOf(origin)
        val queue = ArrayDeque<Position>().also { it.add(origin) }
        while (queue.isNotEmpty()) {
            val from = queue.removeFirst()
            for (direction in Direction.NESW) {
                val next = from.translate(1, direction)
                if (next in bounds && next !in reached && collision.traversable(from, EntityType.PLAYER, direction)) {
                    reached.add(next)
                    queue.add(next)
                }
            }
        }
        return reached
    }

    /**
     * Interrupted, replaced and offline owners cannot strand a standing place.
     */
    private fun active(owner: BotScript): Boolean = owner.bot.state == EntityState.ACTIVE && owner.isRunning() &&
        owner.bot.scriptStack.current() === owner && owner.bot.position in SubZone.VARROCK_HOME_GATHERING.area
}
