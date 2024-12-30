package api.item.dropTable

import api.predef.*
import io.luna.util.Rational

/**
 * Used as a general purpose table to randomly select a node (type [T]) from a list of possible [Rational] instances.
 * Used under-the-hood in [DropTable] to randomly select an item from a table.
 *
 * @author lare96
 */
class RationalTable<T>(private val nodeList: List<Pair<Rational, T>>) {

    /**
     * Creates a virtual table using all the rationals in [nodeList], and rolls on that table to select on a single
     * random node [T].
     */
    fun roll(): T? {
        // Add the chance rationals together to get the total chance of getting any node. We need this to form
        // a rational table with empty slots later on.
        var totalChance: Rational? = null
        for (node in nodeList) {
            val chance = node.first
            totalChance = if (totalChance != null) totalChance.add(chance) else chance
        }

        // Represents a node on the rational table and the slots they occupy.
        class RationalTableNode(val slots: LongRange, val node: T)

        // Create our rational table and use the total chance to determine how many slots nodes will occupy.
        var lastFactor = 0L // Start at slot 0.
        val virtualTableSize = totalChance!!.denominator
        val virtualTable = nodeList.map {
            val chance = it.first
            val spreadFactor = virtualTableSize / chance.denominator
            val startSlot = lastFactor // Starting slot is the last slot we just assigned + 1.
            val endSlot = startSlot + (chance.numerator * spreadFactor) // Ending slot is the relational numerator.
            lastFactor = endSlot
            RationalTableNode(startSlot until endSlot, it.second) // Add to our rational table.
        }

        // Get a random slot within the rational table.
        val randomSlot = rand().nextLong(0, virtualTableSize)
        for (virtualNode in virtualTable) {
            // If the slot is within the virtual node's range, return it.
            if (virtualNode.slots.contains(randomSlot)) {
                return virtualNode.node
            }
        }
        return null
    }

}