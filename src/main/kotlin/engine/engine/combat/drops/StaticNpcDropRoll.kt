package engine.combat.drops

import api.drops.DropTable
import api.drops.DropTableItem
import api.drops.DropTableItemList
import game.content.randomEvents.GuardianJewelDropTable
import io.luna.game.model.Entity
import io.luna.game.model.mob.Mob

/**
 * A static NPC sub-table whose shared jewel outcome competes with its direct item outcomes.
 * Shared weights expand within the same roll so Ring of Wealth affects only jewel selection.
 * The native death context is forwarded unchanged to preserve the killer's equipment and location.
 * Numeric item IDs retain their configured note identity.
 *
 * @param drops The direct and shared outcomes belonging to one logical sub-table.
 * @author lare96
 */
class StaticNpcDropRoll(drops: List<StaticNpcDrop>) : DropTable() {

    /**
     * Direct outcomes, including guaranteed items and explicit empty outcomes.
     */
    private val direct: DropTableItemList

    /**
     * The probability that this roll selects the shared jewel sub-table.
     */
    private val jewelChance: Double

    init {
        require(drops.all { it.shared == null || it.shared == "JEWEL" }) {
            "Unsupported shared NPC drop table."
        }
        direct = drops.filter { it.shared == null }.map { drop ->
            val bounds = drop.quantity.split('-').map { it.toInt() }
            val amount = bounds.first()..bounds.last()
            DropTableItem(drop.id, amount, drop.rarity)
        }
        jewelChance = drops.filter { it.shared == "JEWEL" }.sumOf { it.rarity }
    }

    override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList = direct +
        GuardianJewelDropTable.computeTable(mob, source).map {
            DropTableItem(it.id, it.amount, it.chance * jewelChance)
        }

    override fun computePossibleItems(): DropTableItemList = direct +
        GuardianJewelDropTable.computePossibleItems().map {
            DropTableItem(it.id, it.amount, it.chance * jewelChance)
        }
}
