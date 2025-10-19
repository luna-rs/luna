package api.dropTable.dsl

import api.dropTable.DropTable
import api.dropTable.DropTableHandler
import api.dropTable.DropTableItem
import api.dropTable.DropTableItemList
import api.dropTable.SimpleDropTable
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