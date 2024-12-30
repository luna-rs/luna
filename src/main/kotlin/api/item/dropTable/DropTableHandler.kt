package api.item.dropTable

import api.item.dropTable.dsl.DropTableItemChanceReceiver
import api.item.dropTable.dsl.DropTableItemReceiver
import api.item.dropTable.dsl.NpcDropSetReceiver
import api.item.dropTable.dsl.SpecializedTableReceiver
import api.predef.*
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.util.RandomUtils
import io.luna.util.Rational

/**
 * A type alias for lists of [DropTableItem] types.
 */
typealias DropTableItemList = List<DropTableItem>

/**
 * Handles global functions related to drop tables.
 *
 * @author lare96
 */
object DropTableHandler {

    /**
     * Represents a single conditional drop.
     */
    internal class ConditionalDrop(val condFunc: (Mob?, Npc) -> Boolean, val tableSet: NpcDropTableSet)

    /**
     * The conditional drop list.
     */
    private val conditionalDropList = arrayListOf<ConditionalDrop>()

    /**
     * The NPC id -> NpcDropTableSet map.
     */
    private val npcIdDropMap = hashMapOf<Int, NpcDropTableSet>()

    /**
     * Determines if [tableItem] will be picked based on its rarity.
     */
    internal fun rollSuccess(tableItem: DropTableItem): Boolean = RandomUtils.rollSuccess(tableItem.chance)

    /**
     * Creates a generic [DropTable] using our specialized DSL.
     */
    fun create(action: DropTableItemReceiver.() -> Unit): SpecializedTableReceiver {
        // Build the table.
        val items = arrayListOf<DropTableItem>()
        val builder = DropTableItemReceiver(items, false)
        action(builder)
        return SpecializedTableReceiver(builder)
    }

    /**
     * Creates a new [SimpleDropTable] instance using our specialized DSL.
     */
    fun createSimple(chance: Rational = Rational.ALWAYS, action: DropTableItemReceiver.() -> Unit): SimpleDropTable {
        return create(action).table { SimpleDropTable(items, chance) }
    }

    /**
     * Creates a new [SimpleDropTable] with a single item.
     */
    fun createSingleton(
        chance: Rational = Rational.ALWAYS,
        action: DropTableItemReceiver.() -> DropTableItemChanceReceiver,
    ): SimpleDropTable {
        return createSimple { action(this).chance(chance) }
    }

    /**
     * Creates a new [List] of [DropTableItem] instances using our specialized DSL.
     */
    fun createList(action: DropTableItemReceiver.() -> Unit): DropTableItemList {
        // Build the table.
        val items = arrayListOf<DropTableItem>()
        action(DropTableItemReceiver(items, false))
        return items
    }

    /**
     * Creates a new [NpcDropTableSet] instance using our specialized DSL.
     */
    fun createNpcSet(action: NpcDropSetReceiver.() -> Unit): NpcDropTableSet {
        // Build the tables.
        val tables = arrayListOf<DropTable>()
        action(NpcDropSetReceiver(tables))
        return NpcDropTableSet(tables)
    }

    /**
     * Registers a [NpcDropTableSet] to trigger on [condFunc] when an [Npc] dies.
     */
    fun register(condFunc: (Mob?, Npc) -> Boolean, tableSet: NpcDropTableSet) =
        conditionalDropList.add(ConditionalDrop(condFunc, tableSet))

    /**
     * Registers a [NpcDropTableSet] to trigger when an [Npc] with [id] dies.
     */
    fun register(id: Int, tableSet: NpcDropTableSet) {
        check(npcIdDropMap.putIfAbsent(id, tableSet) != null)
        { "NPC with id[$id] already has a registered NpcDropTableSet" }
    }

    /**
     * Combines [tables] into one standard [MergedDropTable].
     */
    fun createMerged(vararg tables: DropTable): DropTable {
        return MergedDropTable(tables.toList())
    }
}
