package api.bot.action

import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Entity
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.DestroyItemDialogueInterface
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.`in`.ItemOnItemMessageReader

/**
 * A [BotActionHandler] implementation for inventory related actions.
 */
class BotInventoryActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * A builder for use-item interactions.
     */
    inner class UseItemAction(private val usedId: Int) {

        /**
         * Use an item on a generic interactable entity.
         */
        private suspend fun useOnEntity(target: Entity, action: (Int) -> Unit): Boolean {
            val usedIndex = bot.inventory.computeIndexForId(usedId)
            if (usedIndex.isEmpty) {
                // We don't have the item.
                return false
            }

            bot.walking.walkUntilReached(target)
            val walkingSuspendCond = SuspendableCondition({ bot.isViewableFrom(target) || bot.walking.isEmpty })
            if (!bot.isViewableFrom(target)) {
                if(!walkingSuspendCond.submit().await()) {
                    return false
                }
            }
            val interactSuspendCond = SuspendableCondition({ bot.isInteractingWith(target) }, 30)
            action(usedIndex.asInt)
            return interactSuspendCond.submit().await()
        }

        /**
         * An action that forces a [Bot] to use an item on another item in their inventory. Sends the
         * [ItemOnItemMessageReader] packet.
         */
        fun onItem(targetId: Int): Boolean {
            val usedIndex = bot.inventory.computeIndexForId(usedId)
            val targetIndex = bot.inventory.computeIndexForId(targetId)
            if (usedIndex.isEmpty || targetIndex.isEmpty) {
                return false
            }
            bot.output.useItemOnItem(targetIndex.asInt, usedIndex.asInt, targetId, usedId)
            return true
        }

        /**
         * An action that forces a [Bot] to use an item in their inventory on [target].
         */
        suspend fun onNpc(target: Npc) =
            useOnEntity(target) { bot.output.useItemOnNpc(usedId, it, target) }

        /**
         * An action that forces a [Bot] to use an item in their inventory on [target].
         */
        suspend fun onPlayer(target: Player) =
            useOnEntity(target) { bot.output.useItemOnPlayer(usedId, it, target) }

        /**
         * An action that forces a [Bot] to use an item in their inventory on [target].
         */
        suspend fun onObject(target: GameObject) =
            useOnEntity(target) { bot.output.useItemOnObject(usedId, it, target) }
    }

    /**
     * An action builder that forces a [Bot] to use an item in their inventory on a player, NPC, item, or object.
     */
    fun useItem(usedInventoryIndex: Int): UseItemAction = UseItemAction(usedInventoryIndex)

    /**
     * An action that drops an item from the inventory of a [Bot]. Unsuspends when the item is dropped and/or
     * destroyed.
     */
    suspend fun dropItem(id: Int): SuspendableFuture {
        val index = bot.inventory.computeIndexForId(id)
        if (index.isEmpty) {
            // We don't have the item.
            return SuspendableFuture().signal(false)
        }
        val suspendCond = SuspendableCondition({
                                                   bot.inventory[index.asInt] == null || bot.interfaces.isOpen(
                                                       DestroyItemDialogueInterface::class)
                                               })
        bot.output.sendDropItem(index.asInt, id)
        if (itemDef(id).isTradeable) {
            return suspendCond.submit()
        } else {
            suspendCond.submit().await()
            return handler.widgets.clickDestroyItem()
        }

    }

    /**
     * An action that drops all items from the inventory of a [Bot]. Returns `true` if all items were dropped/destroyed.
     */
    suspend fun dropAllItems(): Boolean {
        for (item in bot.inventory) {
            if (item != null) {
                dropItem(item.id).await()
            }
        }
        return bot.inventory.size() == 0
    }

    /**
     * An action that forces the [Bot] to click an item in their inventory. Returns `true` if the item was
     * clicked.
     */
    fun clickItem(option: Int, id: Int, index: Int = -1): Boolean {
        val clickIndex = if (index == -1) bot.inventory.computeIndexForId(id).orElse(-1) else index
        if (clickIndex == -1) {
            logger.debug("No index found for id [$id].")
            return false
        }
        val clickId = bot.inventory[clickIndex]?.id
        if (clickId != id) {
            logger.debug("Item id [$clickId] on index [$clickIndex] does not match id [$id].")
            return false
        }
        bot.output.sendInventoryItemClick(option, clickIndex, clickId)
        return true
    }
}