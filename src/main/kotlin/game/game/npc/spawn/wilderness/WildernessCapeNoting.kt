package game.npc.spawn.wilderness

import api.predef.itemDef
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * Exchanges carried items for their existing banknote forms at Wilderness cape merchants.
 * The exchange is free and retains the items in the player's inventory, including their Wilderness risk.
 *
 * @author lare96
 */
object WildernessCapeNoting {

    /**
     * The ten original team-cape sellers, William through Sam.
     */
    val NPC_IDS = 1778..1787

    /**
     * Resolves an ordinary, non-stackable item's reciprocal banknote definition.
     * Dynamic items retain their individual state and cannot be exchanged.
     */
    fun noteId(item: Item): Int? {
        val definition = item.itemDef
        if (item.isDynamic || definition.isNoted || definition.isStackable) return null
        val note = definition.notedId.orElse(-1)
        if (note < 0) return null
        val notedDefinition = itemDef(note)
        return note.takeIf { notedDefinition.isNoted && notedDefinition.isStackable &&
            notedDefinition.unnotedId.orElse(-1) == item.id }
    }

    /**
     * Converts every ordinary copy of the selected item after checking the live slot, merchant and note capacity.
     * No suspension occurs between validation and the single bulk inventory update.
     */
    fun note(player: Player, merchant: Npc, index: Int, id: Int): Boolean {
        if (merchant.id !in NPC_IDS || merchant.world !== player.world ||
            merchant.state != EntityState.ACTIVE || !merchant.isAlive ||
            player.world.npcs[merchant.index] !== merchant || player.state != EntityState.ACTIVE ||
            !player.isAlive || player.isLocked ||
            !player.world.collisionManager.reached(player, merchant, InteractionPolicy.STANDARD_SIZE)) return false
        val inventory = player.inventory
        if (index !in 0 until inventory.capacity()) return false
        val used = inventory[index]?.takeIf { it.id == id } ?: return false
        val noteId = noteId(used)
        if (noteId == null) {
            player.sendMessage("I can only note ordinary, unnoted items that have a banknote form.")
            return false
        }
        val slots = (0 until inventory.capacity()).filter {
            inventory[it]?.let { item -> item.id == id && !item.isDynamic && item.amount == 1 } == true
        }
        if (slots.isEmpty()) return false
        val heldNotes = inventory.filterNotNull().filter { it.id == noteId }.sumOf { it.amount.toLong() }
        if (heldNotes + slots.size > Int.MAX_VALUE) {
            player.sendMessage("You cannot carry any more of those banknotes.")
            return false
        }
        inventory.startBulkUpdate()
        try {
            for (slot in slots) inventory[slot] = null
            check(inventory.add(slots.first(), Item(noteId, slots.size)))
        } finally {
            inventory.finishBulkUpdate()
        }
        player.sendMessage("The cape merchant notes your items free of charge.")
        return true
    }
}
