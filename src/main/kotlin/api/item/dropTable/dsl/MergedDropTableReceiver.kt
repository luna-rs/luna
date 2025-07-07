package api.item.dropTable.dsl

import api.item.dropTable.DropTable
import api.item.dropTable.DropTableHandler
import api.item.dropTable.DropTableItem
import api.item.dropTable.DropTableItemList
import api.item.dropTable.SimpleDropTable
import api.predef.*
import io.luna.util.Rational

/**
 * Represents the receiver for building [EntityDropTableSet] types in our DSL.
 *
 * @author lare96
 */
class MergedDropTableReceiver(val tables: ArrayList<DropTable>) {

    /**
     * Builds a [SimpleDropTable] to be used within the drop table set.
     */
    fun table(chance: Rational = ALWAYS, receiver: DropTableItemReceiver.() -> Unit) {
        tables += DropTableHandler.createSimple(chance, receiver)
    }

    /**
     * Builds a specialized table to be used within the drop table set.
     */
    fun build(receiver: DropTableItemReceiver.() -> Unit): SpecializedMergedTableReceiver {
        val items = arrayListOf<DropTableItem>()
        val builder = DropTableItemReceiver(items, false)
        receiver(builder)
        return SpecializedMergedTableReceiver(tables, builder)
    }
}