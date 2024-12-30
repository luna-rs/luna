package api.item.dropTable.dsl

import api.item.dropTable.DropTable
import api.item.dropTable.DropTableItem
import api.item.dropTable.NpcDropTableSet
import api.item.dropTable.SimpleDropTable
import api.predef.*

/**
 * Represents the receiver for building [NpcDropTableSet] types in our DSL.
 *
 * @author lare96
 */
class NpcDropSetReceiver(private val tables: ArrayList<DropTable>) {

    /**
     * Builds a [SimpleDropTable] to be used within the drop table set.
     */
    fun table(receiver: DropTableItemReceiver.() -> Unit) {
        val newTable = arrayListOf<DropTableItem>()
        val builder = DropTableItemReceiver(newTable, false)
        receiver(builder)
        SpecializedNpcTableSetReceiver(builder, tables)
    }

    /**
     * Builds a [SimpleDropTable] to be used within the drop table set.
     */
    fun simpleTable(receiver: DropTableItemReceiver.() -> Unit) {
        val newTable = arrayListOf<DropTableItem>()
        receiver(DropTableItemReceiver(newTable, false))
        tables += SimpleDropTable(newTable, ALWAYS)
    }
}