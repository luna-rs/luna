package api.bot.action.trading

import java.util.ArrayDeque
import kotlin.random.Random

/**
 * Owns a bounded, shuffled rotation of exclusive marketplace positions.
 *
 * Callers provide current geometry, occupancy, and owner liveness on every claim. Released positions return to
 * the back of the rotation; blocked positions and abandoned owners cannot permanently consume capacity.
 * This state belongs to the game thread, like the scripts that use it.
 *
 * @author lare96
 */
internal class BotMarketplaceSpotPool<Owner : Any, Spot : Any>(
    private val capacity: Int,
    private val random: Random = Random.Default
) {
    private val leases = LinkedHashMap<Owner, Spot>()
    private val selected = LinkedHashSet<Spot>()
    private val rotation = ArrayDeque<Spot>()

    init {
        require(capacity > 0)
    }

    /**
     * Keeps a valid current lease or claims the first available position in the shuffled rotation.
     */
    fun claim(owner: Owner, candidates: List<Spot>, available: (Spot) -> Boolean,
              active: (Owner) -> Boolean): Spot? {
        refresh(candidates, active)
        leases[owner]?.let { spot ->
            if (available(spot)) return spot
            release(owner)
        }
        repeat(rotation.size) {
            val spot = rotation.removeFirst()
            if (available(spot)) {
                leases[owner] = spot
                return spot
            }
            rotation.addLast(spot)
        }
        return null
    }

    /**
     * Reports capacity without reserving a position for a script that has not started.
     */
    fun hasSpace(candidates: List<Spot>, available: (Spot) -> Boolean, active: (Owner) -> Boolean): Boolean {
        refresh(candidates, active)
        return rotation.any(available)
    }

    /**
     * Returns the position currently owned by this session.
     */
    fun position(owner: Owner): Spot? = leases[owner]

    /**
     * Prevents unrelated marketplace activities from using a currently leased trader position.
     */
    fun reserved(spot: Spot): Boolean = spot in leases.values

    /**
     * Releases a session's position exactly once and rotates it behind the other free positions.
     */
    fun release(owner: Owner) {
        val spot = leases.remove(owner) ?: return
        if (spot in selected && spot !in rotation) rotation.addLast(spot)
    }

    /**
     * Replaces invalid geometry and reclaims leases whose scripts have stopped or gone offline.
     */
    private fun refresh(candidates: List<Spot>, active: (Owner) -> Boolean) {
        val allowed = candidates.toSet()
        leases.keys.filterNot(active).forEach(::release)
        leases.filterValues { it !in allowed }.keys.toList().forEach(::release)
        selected.retainAll(allowed)
        rotation.removeIf { it !in selected }
        candidates.distinct().filter { it !in selected }.shuffled(random)
            .take((capacity - selected.size).coerceAtLeast(0)).forEach {
                selected += it
                rotation.addLast(it)
            }
    }
}
