package game.player.item.consume.potion

import game.player.Sound
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Combines two real partial bottles through the ordinary inventory item-on-item interaction.
 * The used slot receives at most four doses; the target slot keeps any remainder or its empty vial.
 * Slot replacement preserves both original bottles without requiring spare inventory or bank space.
 * No fee, experience, level check or empty-vial splitting is part of this action.
 *
 * @author lare96
 */
object PotionDecanting {

    const val EMPTY_VIAL = 229

    /**
     * Rechecks the native inventory event rather than trusting packet ids or a previously selected slot.
     */
    fun combine(event: ItemOnItemEvent): Boolean {
        if (event.usedItemInterface != 3214 || event.targetItemInterface != 3214) return false
        return combine(event.plr, event.usedItemId, event.usedItemIndex, event.targetItemId, event.targetItemIndex)
    }

    /**
     * Replaces two distinct, unchanged, unnoted one-bottle slots in one synchronous bulk update.
     */
    fun combine(player: Player, usedId: Int, usedIndex: Int, targetId: Int, targetIndex: Int): Boolean {
        val inventory = player.inventory
        if (usedIndex !in 0 until inventory.capacity() || targetIndex !in 0 until inventory.capacity() ||
            usedIndex == targetIndex) return false
        val used = inventory[usedIndex] ?: return false
        val target = inventory[targetIndex] ?: return false
        if (used.id != usedId || target.id != targetId || !isBottle(used) || !isBottle(target)) return false
        val outputs = outputs(usedId, targetId) ?: return false
        val combined = Item(outputs.first)
        val remainder = Item(outputs.second)
        inventory.startBulkUpdate()
        try {
            inventory[usedIndex] = combined
            inventory[targetIndex] = remainder
        } finally {
            inventory.finishBulkUpdate()
        }
        player.playSound(Sound.LIQUID)
        player.sendMessage("You combine the liquid into ${PotionFamily.forId(usedId)!!.doses(combined.id)} doses.")
        return true
    }

    /**
     * Calculates the exact used-slot and target-slot outputs for two reviewed partial bottles.
     */
    internal fun outputs(usedId: Int, targetId: Int): Pair<Int, Int>? {
        val family = PotionFamily.forId(usedId) ?: return null
        if (usedId !in family.ids || targetId !in family.ids) return null
        val usedDoses = family.doses(usedId)
        val targetDoses = family.doses(targetId)
        if (usedDoses !in 1..3 || targetDoses !in 1..3) return null
        val total = usedDoses + targetDoses
        val remainder = total - minOf(total, 4)
        return family.idForDoses(minOf(total, 4)) to
            if (remainder == 0) EMPTY_VIAL else family.idForDoses(remainder)
    }

    private fun isBottle(item: Item): Boolean = item.amount == 1 && !item.isDynamic && !item.itemDef.isNoted
}
