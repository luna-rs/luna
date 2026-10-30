package engine.bot.combat

import api.bot.zone.SubZone
import api.combat.npc.NpcCombatHandler
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.collision.CollisionFlag
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.PursuitAction
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.game.model.path.route.RouteStrategy
import kotlin.math.max
import kotlin.math.min

/**
 * Finds local firing positions where ordinary melee pursuit is stopped by map collision.
 *
 * All calls belong to the game thread. Candidates are predictions until a bot confirms them in combat; only those
 * confirmations enter the bounded, server-lifetime cache. Every reuse checks current collision and the NPC's new
 * position. Mob occupancy is deliberately excluded so another player's temporary obstruction never creates a spot.
 *
 * @author lare96
 */
object NpcSafespotMap {

    private const val MAX_CACHE_SIZE = 512
    private const val MAX_CANDIDATES = 192
    private const val MAX_CHASE_STEPS = 32
    private const val REJECT_TICKS = 100L
    private const val TILE_BLOCKERS = CollisionFlag.LOC or CollisionFlag.GROUND_DECOR or CollisionFlag.BLOCK_WALK

    /**
     * A firing tile and the NPC position predicted after its greedy chase stops.
     *
     * @author lare96
     */
    data class Candidate internal constructor(
        val stand: Position,
        val trap: Position,
        val cached: Boolean,
        internal val key: Key
    )

    /**
     * Cache identity includes world ownership, monster spawn, movement rules and weapon reach.
     *
     * @author lare96
     */
    internal data class Key(
        val world: World,
        val npcId: Int,
        val spawn: Position,
        val size: Int,
        val strategy: RouteStrategy,
        val zone: SubZone,
        val range: Int,
        val stand: Position
    )

    private val cache = LinkedHashMap<Key, Candidate>(16, 0.75f, true)
    private val rejectedUntil = LinkedHashMap<Key, Long>()

    var proposals = 0L
        private set
    var cacheHits = 0L
        private set
    var confirmations = 0L
        private set
    var rejections = 0L
        private set
    val cacheSize: Int
        get() = cache.size

    /**
     * Finds a currently shootable position in [zone], preferring a previously confirmed spot.
     * Approach routing remains owned by the bot controller, which rejects unreachable positions.
     */
    fun propose(bot: Bot, npc: Npc, zone: SubZone, policy: InteractionPolicy): Candidate? {
        checkThread(bot.world)
        proposals++
        if (!eligible(bot, npc, policy)) {
            return null
        }
        val known = cache.values.filter { matches(it.key, npc, zone, policy) }
            .sortedBy { distance(bot.position, it.stand) }
        for (entry in known) {
            val trap = predict(bot, npc, zone, entry.stand, policy)
            if (trap != null && !rejected(entry.key)) {
                cacheHits++
                cache[entry.key] // Refresh access order only after iteration has finished.
                return Candidate(entry.stand, trap, true, entry.key)
            }
            cache.remove(entry.key)
        }
        val origin = npc.position
        val radius = policy.distance
        val area = zone.area
        val options = ArrayList<Position>()
        for (x in max(origin.x - radius, area.southWest.x)..min(origin.x + radius, area.northEast.x)) {
            for (y in max(origin.y - radius, area.southWest.y)..min(origin.y + radius, area.northEast.y)) {
                options.add(Position(x, y, origin.z))
            }
        }
        options.sortBy { distance(bot.position, it) }
        for (stand in options.take(MAX_CANDIDATES)) {
            val key = key(npc, zone, policy, stand)
            if (rejected(key)) {
                continue
            }
            val trap = predict(bot, npc, zone, stand, policy) ?: continue
            return Candidate(stand, trap, false, key)
        }
        return null
    }

    /**
     * Rechecks a plan against live map collision and the NPC's current position before using it.
     */
    fun valid(bot: Bot, npc: Npc, candidate: Candidate, policy: InteractionPolicy): Boolean {
        checkThread(bot.world)
        val key = candidate.key
        val valid = eligible(bot, npc, policy) && matches(key, npc, key.zone, policy) &&
                predict(bot, npc, key.zone, candidate.stand, policy) == candidate.trap
        if (!valid) {
            cache.remove(key)
        }
        return valid
    }

    /**
     * Remembers a position after the controller observes successful stationary attacks without retaliation.
     */
    fun verify(candidate: Candidate) {
        checkThread(candidate.key.world)
        confirmations++
        rejectedUntil.remove(candidate.key)
        cache[candidate.key] = candidate.copy(cached = true)
        trim(cache)
    }

    /**
     * Evicts an unsuccessful plan and prevents immediate retries of the same tile.
     */
    fun reject(candidate: Candidate) {
        checkThread(candidate.key.world)
        rejections++
        cache.remove(candidate.key)
        rejectedUntil[candidate.key] = candidate.key.world.currentTick + REJECT_TICKS
        trim(rejectedUntil)
    }

    private fun eligible(bot: Bot, npc: Npc, policy: InteractionPolicy): Boolean =
        bot.world === npc.world && bot.state == EntityState.ACTIVE && npc.state == EntityState.ACTIVE &&
                bot.isAlive && npc.isAlive && bot.z == npc.z &&
                policy.type == InteractionType.LINE_OF_SIGHT && policy.distance in 2..10 &&
                npc.size() in 1..8 && NpcCombatHandler.usesStandardAttack(npc) &&
                !npc.status.isImmobilized() && !npc.isLocked && !npc.walking.isLocked && !npc.isStationary &&
                !npc.combat.isDisabled && !npc.combat.isRetreating && !bot.status.isImmobilized()

    private fun predict(bot: Bot, npc: Npc, zone: SubZone, stand: Position,
                        policy: InteractionPolicy): Position? {
        if (stand.z != npc.z || !zone.area.contains(stand) || !zone.area.contains(npc.position) ||
            !withinLeash(npc, stand)) {
            return null
        }
        val collision = bot.world.collisionManager
        if (collision.view(false).get(stand.x, stand.y, stand.z) and TILE_BLOCKERS != 0 ||
            !collision.reached(stand, npc.position, policy)) {
            return null
        }
        var position = npc.position
        repeat(MAX_CHASE_STEPS) {
            // Current combat's melee checks are anchored to one tile. Reject all adjacency conservatively, including
            // wall/diagonal cases, rather than claiming a spot safe through a different hypothetical-target check.
            if (position.isWithinDistance(stand, 1) || !position.isWithinDistance(stand, Position.VIEWING_DISTANCE)) {
                return null
            }
            val from = position
            val step = PursuitAction.nextStep(from, stand, 1, false) { direction ->
                collision.traversable(from, EntityType.PLAYER, direction, npc.size(), npc.routeStrategy)
            }
            if (step == Direction.NONE) {
                return if (zone.area.contains(position) && collision.reached(stand, position, policy)) position else null
            }
            position = from.translate(1, step)
        }
        return null
    }

    private fun withinLeash(npc: Npc, stand: Position): Boolean =
        npc.combat.retreatPolicy == RetreatPolicy.NONE || npc.combat.retreatPolicy == RetreatPolicy.LOW_HEALTH ||
                npc.combatDef().movement.containsTarget(npc.basePosition, stand)

    private fun matches(key: Key, npc: Npc, zone: SubZone, policy: InteractionPolicy): Boolean =
        key.world === npc.world && key.npcId == npc.id && key.spawn == npc.basePosition && key.size == npc.size() &&
                key.strategy == npc.routeStrategy && key.zone == zone && key.range == policy.distance

    private fun key(npc: Npc, zone: SubZone, policy: InteractionPolicy, stand: Position): Key =
        Key(npc.world, npc.id, npc.basePosition, npc.size(), npc.routeStrategy, zone, policy.distance, stand)

    private fun rejected(key: Key): Boolean {
        val until = rejectedUntil[key] ?: return false
        if (key.world.currentTick >= until) {
            rejectedUntil.remove(key)
            return false
        }
        return true
    }

    private fun <T> trim(map: LinkedHashMap<Key, T>) {
        while (map.size > MAX_CACHE_SIZE) {
            map.remove(map.keys.first())
        }
    }

    private fun distance(first: Position, second: Position): Int =
        max(kotlin.math.abs(first.x - second.x), kotlin.math.abs(first.y - second.y))

    private fun checkThread(world: World) {
        check(Thread.currentThread() === world.context.game.thread) { "Safespot planning requires the game thread." }
    }
}
