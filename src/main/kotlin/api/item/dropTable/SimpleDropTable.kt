package api.item.dropTable

import api.predef.*
import io.luna.game.model.Entity
import io.luna.game.model.mob.Mob
import io.luna.util.Rational

/**
 * A simple [DropTable] implementation that takes a [DropTableItemList] and transfers it into this table.
 *
 * @author lare96
 */
class SimpleDropTable(private val items: DropTableItemList, chance: Rational = ALWAYS) : DropTable(chance) {
    override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList = items
    override fun computePossibleItems(): DropTableItemList = items
}