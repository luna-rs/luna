package api.drops

import api.drops.dsl.DropTableItemChanceReceiver
import api.drops.dsl.DropTableItemReceiver
import api.drops.dsl.MergedDropTableReceiver
import api.drops.dsl.SpecializedTableReceiver
import api.predef.*
import api.predef.ext.*
import com.google.common.base.Preconditions.checkState
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.util.Rational
import kotlin.reflect.KClass

/**
 * A type alias for lists of [DropTableItem] types.
 */
typealias DropTableItemList = List<DropTableItem>

/**
 * Central manager for all drop table-related operations. Provides DSL-based creation, registration, and evaluation
 * of drop tables.
 *
 * Features include:
 * - NPC-specific and type-based drop table registration
 * - Combined drop tables using [MergedDropTable]
 * - Builder DSLs for constructing item lists and specialized drop tables
 *
 * @author lare96
 */
object DropTableHandler {

    /**
     * Map of NPC IDs to their assigned [MergedDropTable] instances.
     */
    private val npcIdMap = hashMapOf<Int, MergedDropTable>()

    /**
     * Map of NPC types (classes) to their assigned [MergedDropTable] instances.
     */
    private val npcTypeMap = hashMapOf<Class<out Npc>, MergedDropTable>()

    /**
     * @return The drop table for [npc].
     */
    fun getDropTable(npc: Npc): MergedDropTable? = npcTypeMap.getOrDefault(npc.javaClass, npcIdMap[npc.id])

    /**
     * Constructs a generic drop table using a DSL-style builder.
     *
     * @param action The block defining the drop table items.
     * @return A specialized table receiver to define further table behavior.
     */
    fun create(action: DropTableItemReceiver.() -> Unit): SpecializedTableReceiver {
        val items = arrayListOf<DropTableItem>()
        val builder = DropTableItemReceiver(items, false)
        action(builder)
        return SpecializedTableReceiver(builder)
    }

    /**
     * Creates a [SimpleDropTable] using a DSL and an optional drop [chance].
     *
     * @param chance The chance for the table to be rolled. Defaults to [Rational.ALWAYS].
     * @param action The block defining the drop table items.
     * @return A simple drop table instance.
     */
    fun createSimple(chance: Rational = Rational.ALWAYS, action: DropTableItemReceiver.() -> Unit): SimpleDropTable {
        return create(action).table { SimpleDropTable(table, chance) }
    }

    /**
     * Creates a [SimpleDropTable] with a single item and specified drop [chance].
     *
     * @param chance The chance for the table to be rolled.
     * @param action The block returning a single drop item.
     * @return A simple drop table with one item.
     */
    fun createSingleton(
        chance: Rational = Rational.ALWAYS,
        action: DropTableItemReceiver.() -> DropTableItemChanceReceiver,
    ): SimpleDropTable {
        return createSimple { action(this).chance(chance) }
    }

    /**
     * Builds a raw [List] of [DropTableItem]s using the item DSL.
     *
     * @param action The block that defines item entries.
     * @return A list of drop table items.
     */
    fun createList(action: DropTableItemReceiver.() -> Unit): DropTableItemList {
        val items = arrayListOf<DropTableItem>()
        action(DropTableItemReceiver(items, false))
        return items
    }

    /**
     * Constructs a combined [MergedDropTable] from multiple tables using the DSL.
     *
     * @param action The block defining the merged table structure.
     * @return A merged drop table instance.
     */
    fun createMerged(action: MergedDropTableReceiver.() -> Unit): MergedDropTable {
        val tables = arrayListOf<DropTable>()
        action(MergedDropTableReceiver(tables))
        return MergedDropTable(tables)
    }

    /**
     * Registers a [MergedDropTable] for a specific NPC ID.
     *
     * @param npcId The ID of the NPC to assign the drop table to.
     * @param action The block defining the table structure.
     * @return The constructed merged drop table.
     */
    fun createNpc(npcId: Int, action: MergedDropTableReceiver.() -> Unit): MergedDropTable {
        val table = createMerged(action)
        checkState(npcIdMap.putIfAbsent(npcId, table) == null,
                   "NPC ID $npcId already registered to a table.")
        return table
    }

    /**
     * Registers a [MergedDropTable] for a specific NPC type/class.
     *
     * @param npcType The Kotlin class of the NPC type to assign the drop table to.
     * @param action The block defining the table structure.
     * @return The constructed merged drop table.
     */
    fun createNpc(npcType: KClass<out Npc>, action: MergedDropTableReceiver.() -> Unit): MergedDropTable {
        val table = createMerged(action)
        checkState(npcTypeMap.putIfAbsent(npcType.java, table) == null,
                   "Class ${npcType.java} already registered to a table.")
        return table
    }
}