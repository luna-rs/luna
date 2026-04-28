package game.item.degradable

/**
 * Groups all degradable stages that belong to a single degradation chain.
 *
 * Each set contains the degradation [type] and the ordered list of [items] that make up that chain. The order of
 * [items] should reflect the natural degradation sequence from the starting item to the final degraded form.
 *
 * @param type The degradation type represented by this set.
 * @param items The ordered degradable items that belong to this chain.
 * @author lare96
 */
class DegradableItemSet(val type: DegradableItemType, val items: List<DegradableItem>) {

    /**
     * A lookup set containing every item id present in this degradation chain. This is useful for quick membership
     * checks without iterating through [items].
     */
    val all = items.map { it.id }.toSet()
}