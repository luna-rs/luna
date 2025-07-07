package api.item.dropTable.dsl

import api.item.dropTable.DropTable

/**
 * Enables the transfer of items to a specialized [DropTable] type in our DSL, within a merged drop table.
 *
 * @author lare96
 */
class SpecializedMergedTableReceiver(private val tableList: ArrayList<DropTable>,
                                     private val receiver: DropTableItemReceiver) {

    /**
     * Transfers the items in [receiver] to the returned table and adds it to the merged table.
     */
    fun <E : DropTable> table(function: DropTableItemReceiver.() -> E) {
        val table = function(receiver)
        tableList += table
    }
}