package game.item.degradable

/**
 * Represents one stage in a degradable item chain.
 *
 * Each instance links the current item [id] to the item that came before it, [prevId], and the item that follows it,
 * [nextId]. A value of `-1` for [prevId] indicates the first stage in the chain. A value of `-1` for [nextId]
 * indicates the final stage has no further degraded form.
 *
 * Equality and hash code are based only on [id], so two degradable items with the same item id are treated as the
 * same entry regardless of their surrounding chain links.
 *
 * @param prevId The previous item id in the degradation chain, or `-1` if this is the starting stage.
 * @param id The item id represented by this degradation stage.
 * @param nextId The next item id in the degradation chain, or `-1` if the final stage has no item.
 * @author lare96
 */
class DegradableItem(val prevId: Int, val id: Int, val nextId: Int) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DegradableItem) return false
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int {
        return id
    }
}