package api.combat.death.dsl

import api.drops.DropTableHandler.getDropTable
import api.drops.MergedDropTable
import api.predef.*
import api.predef.ext.*
import engine.bot.speech.BotReactions
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.item.DeathGroundItem
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot

/**
 * A receiver class used during the **main death stage** of a [DeathHookReceiver].
 *
 * This receiver defines actions that must occur when the mob actually dies, such as dropping items and
 * unregistration.
 *
 * @property receiver The [DeathHookReceiver] providing the current death context.
 * @see DeathHookReceiver
 * @author lare96
 */
class DeathReceiver<T : Mob>(val receiver: DeathHookReceiver<T>) {

    /**
     * Removes all tradeable items from a player's inventory and equipment, optionally sorting them by value.
     *
     * This is used to collect and prepare items for the death-drop sequence, ensuring that non-tradeable or
     * protected items remain untouched.
     *
     * @receiver A [DeathReceiver] operating on a [Player] entity.
     * @param sort Whether to sort the removed items by their in-game value (default: `true`).
     * @return An [ArrayList] containing all removed and collected items.
     */
    fun DeathReceiver<Player>.removeAll(sort: Boolean = true): ArrayList<Item> {

        /**
         * Internal helper that removes all tradeable items from a given container.
         *
         * @param container The [ItemContainer] to remove items from.
         * @param items The mutable list collecting removed items.
         */
        fun removeItems(container: ItemContainer, items: ArrayList<Item>) {
            container.forIndexedItems { index, item ->
                if (item != null && item.itemDef.isTradeable) {
                    items.add(item)
                    container.set(index, null)
                }
            }
        }

        val removed = ArrayList<Item>()
        removeItems(receiver.victim.inventory, removed)
        removeItems(receiver.victim.equipment, removed)

        if (sort) {
            removed.sortWith(Item.VALUE_COMPARATOR)
        }
        return removed
    }

    /**
     * Retrieves the merged drop table associated with the NPC victim of this death event.
     *
     * @receiver A [DeathReceiver] operating on an [Npc] entity.
     * @return A [MergedDropTable] if one exists for the NPC, or `null` if none is found.
     */
    fun DeathReceiver<Npc>.getDropTable(): MergedDropTable? {
        return getDropTable(receiver.victim)
    }

    /**
     * Rolls the NPC’s drop table and spawns the resulting items into the world.
     *
     * This function simulates the full drop sequence following an NPC’s death:
     * - Retrieves and rolls the NPC’s drop table.
     * - Determines the appropriate visibility view (local if a player killed it, or global otherwise).
     * - Spawns all resulting [GroundItem] entities in the world at the NPC’s position.
     *
     * @receiver A [DeathReceiver] operating on an [Npc] entity.
     */
    fun DeathReceiver<Npc>.drop() {
        val source = receiver.source
        val victim = receiver.victim
        val table = getDropTable(victim)
        if (table != null) {
            val itemList = table.roll(victim, source)
            val view = if (source is Player)
                ChunkUpdatableView.localView(source)
            else
                ChunkUpdatableView.globalView()

            var reacted = false
            for (item in itemList) {
                if (source is Bot && item.itemDef.value > 10_000 && !reacted) { // TODO@0.5.0 Use economy value.
                    // React to receiving a rare drop.
                    BotReactions.reactToRareDrop(source, item)
                    reacted = true
                }
                world.addItem(DeathGroundItem(ctx, item.id, item.amount, victim.position, view, victim))
            }
        }
    }
}
