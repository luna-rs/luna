
package game.content.seasonalEvents

import api.drops.DropTable
import api.drops.DropTableItem
import api.drops.DropTableItemList
import api.predef.ALWAYS
import io.luna.game.model.Entity
import io.luna.game.model.mob.Mob
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * A seasonal drop table that guarantees its configured drops during the final week of each month.
 *
 * Normally preserves the original drop probabilities of [items]. During the final seven calendar
 * days of the month, every configured item is reconstructed with an [ALWAYS] drop chance while
 * retaining its original item id and quantity.
 *
 * The active drop probabilities are evaluated whenever [computeTable] is called, allowing them
 * to change automatically as the current date enters or leaves the final week of the month.
 *
 * @param items The configured drop items and their normal probabilities.
 * @author lare96
 */
class SeasonalEventDropTable(private val items: DropTableItemList) : DropTable() {

    override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList {
        val now = LocalDate.now()
        val lastDayOfMonth = now.with(TemporalAdjusters.lastDayOfMonth())
        val startOfLastWeek = lastDayOfMonth.minusDays(6)
        if (!now.isBefore(startOfLastWeek) && !now.isAfter(lastDayOfMonth)) {
            // All items have an ALWAYS chance to be dropped on the last week of the event.
            return items.map { DropTableItem(it.id, it.amount, ALWAYS) }
        }
        return items
    }

    override fun computePossibleItems(): DropTableItemList = items
}
