package game.item.degradable.combat

import api.predef.*
import game.item.degradable.DegradableEquipmentHandler
import game.item.degradable.DegradableEquipmentHandler.charges
import io.luna.game.model.item.DynamicItem
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * Repairs worn Barrows equipment for coins.
 *
 * A piece wears through four numbered stages (100, 75, 50 and 25) before it breaks. The price follows the wear: a
 * totally broken weapon costs 100,000 coins, a body 90,000, legs 80,000 and a helmet 60,000, and a piece that is only
 * partly worn costs its share of that.
 */
object BarrowsRepair {

    /**
     * Bob in Lumbridge, Dunstan in Burthorpe and Tindel Marchant in Port Khazard.
     */
    val REPAIRERS = setOf(519, 1082, 1799)

    /**
     * The charges each numbered stage holds.
     */
    const val STAGE_CHARGES = 250

    /**
     * The wear of a totally broken piece.
     */
    const val BROKEN_WEAR = 4 * STAGE_CHARGES

    /**
     * The coins a totally broken piece costs to repair, by equipment slot.
     */
    private val BROKEN_PRICE = mapOf(Equipment.WEAPON to 100_000,
                                     Equipment.CHEST to 90_000,
                                     Equipment.LEGS to 80_000,
                                     Equipment.HEAD to 60_000)

    /**
     * A worn piece and what it takes to fix it.
     *
     * @param fixedId The fixed piece it becomes.
     * @param wear The charges it has lost, from 0 to [BROKEN_WEAR].
     */
    class Repair(val fixedId: Int, val wear: Int) {

        /**
         * The coins this repair costs.
         */
        val cost: Int = BROKEN_PRICE.getValue(equipDef(fixedId).index) * wear / BROKEN_WEAR
    }

    /**
     * Works out the repair for [item].
     *
     * @return The repair, or `null` if [item] isn't a worn Barrows piece.
     */
    fun repairOf(item: Item): Repair? {
        val worn = DegradableEquipmentHandler.getDegradable(item.id)
        if (worn != null) {
            val (set, stage) = worn
            val stageIndex = set.items.indexOf(stage)
            if (!set.type.isBarrows() || stageIndex < 1) {
                return null
            }
            val charges = if (item is DynamicItem) item.charges.coerceIn(0, STAGE_CHARGES) else STAGE_CHARGES
            return Repair(set.items[0].id, stageIndex * STAGE_CHARGES - charges)
        }

        // A broken piece isn't in the degradable data: it's the id the last numbered stage wears into.
        val (set, last) = DegradableEquipmentHandler.getDegradable(item.id - 1) ?: return null
        if (!set.type.isBarrows() || last.nextId != item.id) {
            return null
        }
        return Repair(set.items[0].id, BROKEN_WEAR)
    }

    /**
     * Has [npc] look at the item in inventory slot [index] and offer to repair it.
     */
    fun offer(player: Player, npc: Npc, index: Int) {
        val item = player.inventory[index] ?: return
        val repair = repairOf(item) ?: return
        if (!canPay(player, repair)) {
            player.sendMessage("I'll need ${repair.cost}gp to repair that.")
            return
        }
        val price = if (repair.cost == 0) "free" else "${repair.cost}gp"
        player.newDialogue()
            .npc(npc.id, "Quite badly damaged, but easy to repair.", "Would you like me to repair it for $price?")
            .options("Yes, please.", { repair(it, npc, index, item) },
                     "No, thank you.", { it.resetDialogues() })
            .open()
    }

    /**
     * Repairs [item], provided it's still in slot [index] and the player can still pay.
     */
    private fun repair(player: Player, npc: Npc, index: Int, item: Item) {
        player.resetDialogues()
        val inventory = player.inventory
        if (inventory[index] !== item) {
            return
        }
        val repair = repairOf(item) ?: return
        if (!canPay(player, repair)) {
            player.sendMessage("I'll need ${repair.cost}gp to repair that.")
            return
        }
        inventory.startBulkUpdate()
        try {
            if (repair.cost > 0) {
                inventory.remove(Item(995, repair.cost))
            }
            inventory[index] = Item(repair.fixedId)
        } finally {
            inventory.finishBulkUpdate()
        }
        val price = if (repair.cost == 0) "free" else "${repair.cost}gp"
        player.sendMessage("${npcDef(npc.id).name} repairs your ${itemName(repair.fixedId)} for $price.")
    }

    private fun canPay(player: Player, repair: Repair) =
        repair.cost == 0 || player.inventory.contains(Item(995, repair.cost))
}
