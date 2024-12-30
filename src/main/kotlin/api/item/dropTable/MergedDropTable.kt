package api.item.dropTable

import io.luna.game.model.Entity
import io.luna.game.model.mob.Mob

/**
 * Represents a collection of [DropTable] types merged into a single table.
 */
class MergedDropTable(val tables: List<DropTable>) : DropTable() {

    override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList {
        return DropTableHandler.createList {
            for (drops in tables) {
                if (drops.canRollOnTable(mob, source)) {
                    items += drops.computeTable(mob, source)
                }
            }
        }
    }

    override fun computePossibleItems(): DropTableItemList {
        return DropTableHandler.createList {
            for (drops in tables) {
                items += drops.computeTable(null, null)
            }
        }
    }
}