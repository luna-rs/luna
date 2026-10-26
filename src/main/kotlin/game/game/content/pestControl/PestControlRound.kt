package game.content.pestControl

/**
 * Game-thread state for the original, single-lander Pest Control rules.
 * Jagex's 24 April 2006 update requires 50 damage and credits five damage per repaired section.
 *
 * @author lare96
 */
class PestControlRound<T> {
    companion object {
        const val MIN_PLAYERS = 5
        const val MAX_PLAYERS = 25
        const val WAIT_TICKS = 500
        const val ROUND_TICKS = 2000
        const val POINT_CAP = 15
        const val REQUIRED_DAMAGE = 50
    }

    private val queue = linkedSetOf<T>()
    private val damage = linkedMapOf<T, Int>()
    private val destroyed = mutableSetOf<Int>()
    var active = false
        private set
    var remaining = 0
        private set
    var departure = WAIT_TICKS
        private set

    fun waiting(): List<T> = queue.toList()
    fun players(): List<T> = damage.keys.toList()
    fun playing(player: T): Boolean = active && player in damage
    fun contribution(player: T): Int = damage[player] ?: 0

    fun join(player: T): Boolean = player !in damage && queue.add(player)

    fun leave(player: T) {
        queue.remove(player)
        damage.remove(player)
        if (queue.isEmpty()) departure = WAIT_TICKS
    }

    /**
     * Advances the lobby clock. Leftover passengers keep their FIFO priority for the next round.
     */
    fun launch(advance: Boolean = true): List<T> {
        if (active || queue.isEmpty()) return emptyList()
        if (advance && departure > 0) departure--
        if (queue.size < MIN_PLAYERS || departure > 0 && queue.size < MAX_PLAYERS) return emptyList()
        val passengers = queue.take(MAX_PLAYERS)
        passengers.forEach { queue.remove(it); damage[it] = 0 }
        destroyed.clear()
        active = true
        remaining = ROUND_TICKS
        departure = WAIT_TICKS
        return passengers
    }

    fun credit(player: T, amount: Int) {
        if (playing(player) && amount > 0) {
            damage[player] = (contribution(player).toLong() + amount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    fun destroyPortal(index: Int): Boolean = active && index in 0..3 && destroyed.add(index)

    /**
     * Checks for victory/loss. The caller settles with [finish] before advancing another tick.
     * A dead knight always loses, including on the last tick.
     */
    fun tick(knightAlive: Boolean): Boolean? {
        if (!active) return null
        remaining--
        return when {
            !knightAlive || damage.isEmpty() -> false
            destroyed.size == 4 || remaining <= 0 -> true
            else -> null
        }
    }

    /**
     * Takes the settlement before callbacks run, so repeated cleanup cannot award a second reward.
     */
    fun finish(won: Boolean): Map<T, Boolean> {
        if (!active) return emptyMap()
        active = false
        val result = damage.mapValues { won && it.value >= REQUIRED_DAMAGE }
        damage.clear()
        destroyed.clear()
        return result
    }
}
