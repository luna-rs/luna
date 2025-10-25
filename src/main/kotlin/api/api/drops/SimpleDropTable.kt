package api.drops

import api.predef.*
import io.luna.game.model.Entity
import io.luna.game.model.mob.Mob
import io.luna.util.Rational

/**
 * A basic implementation of [DropTable] that returns a predefined list of items.
 *
 * This table does not perform any dynamic roll logic or branching; it simply yields the provided [items]
 * if the chance to roll on the table succeeds.
 *
 * @param items The static list of items to be returned when this table is rolled.
 * @param chance The chance to roll on this table. Defaults to [ALWAYS].
 *
 * @author lare96
 */
class SimpleDropTable(private val items: DropTableItemList, chance: Rational = ALWAYS) : DropTable(chance) {
    override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList = items
    override fun computePossibleItems(): DropTableItemList = items
}