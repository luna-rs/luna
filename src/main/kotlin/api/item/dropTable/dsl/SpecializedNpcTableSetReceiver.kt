package api.item.dropTable.dsl

import api.item.dropTable.DropTable
import api.item.dropTable.NpcDropTableSet

/**
 * Enables the transfer of items to a specialized [DropTable] type within a [NpcDropTableSet], in our DSL.
 *
 * @author lare96
 */
class SpecializedNpcTableSetReceiver(val receiver: DropTableItemReceiver, val tables: ArrayList<DropTable>) {

    /**
     * Transfers the items in [receiver] to the returned table.
     */
    fun to(function: DropTableItemReceiver.() -> DropTable) {
        val newTable = function(receiver)
        tables += newTable
    }
}