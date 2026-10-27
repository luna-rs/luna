package api.combat.death

import engine.combat.prayer.CombatPrayer
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.SkullIcon

/**
 * Shares the default death hook's item retention rules with read-only loadout planning.
 * Non-tradeable items stay in their containers. Tradeable stacks are ordered by unnoted base value;
 * each protection slot retains one unit, allowing several units from the same valuable stack to be protected.
 * Controller-specific deaths and essence pouch destruction are handled by their owning content.
 *
 * @author lare96
 */
object PlayerDeathItems {
    /**
     * Regular players and moderators lose items in production; administrators and developers are protected.
     * Beta mode enables item loss for every rank, matching the default hook's documented administrator exception.
     */
    fun losesItems(player: Player, betaMode: Boolean): Boolean =
        betaMode || player.rights < PlayerRights.ADMINISTRATOR

    /**
     * Tradeable items returned to inventory and those dropped, without changing the supplied containers.
     *
     * @author lare96
     */
    data class Selection(val kept: List<Item>, val lost: List<Item>)

    /**
     * Returns the number of protected item units under the default hook's current skull and prayer rules.
     */
    fun keepCount(player: Player): Int = (if (player.skullIcon == SkullIcon.WHITE) 0 else 3) +
        if (player.combat.prayers.isActive(CombatPrayer.PROTECT_ITEM)) 1 else 0

    /**
     * Partitions a snapshot using native base-value ordering, independently of economy guide prices.
     * Equal values preserve input order, so callers supply inventory before equipment as the death hook does.
     * Empty stacks never consume a protection slot. Input items and containers are not mutated.
     * Protection is spent on individual units of the most valuable remaining stack before considering cheaper
     * items, matching repeated one-item selection in the reference death handler. Stacks are split by quantity
     * without expanding them, so even maximum-size stacks need only one iteration each.
     */
    fun select(items: Iterable<Item>, keepCount: Int): Selection {
        require(keepCount >= 0)
        val sorted = items.filter { it.amount > 0 && it.itemDef.isTradeable }.sortedWith(Item.VALUE_COMPARATOR)
        val kept = ArrayList<Item>()
        val lost = ArrayList<Item>()
        var remaining = keepCount
        for (item in sorted) {
            val protectedAmount = minOf(item.amount, remaining)
            if (protectedAmount > 0) {
                kept += item.withAmount(protectedAmount)
                remaining -= protectedAmount
            }
            if (protectedAmount < item.amount) lost += item.addAmount(-protectedAmount)
        }
        return Selection(kept, lost)
    }
}
