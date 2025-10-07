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
import java.util.OptionalInt

/**
 * A [BotActionHandler] implementation for inventory related actions.
 */
class BotInventoryActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * A builder for use-item interactions.
     */
    inner class UseItemAction(private val usedId: Int, private val usedIndex: Int) {

        /**
         * Use an item on a generic interactable entity.
         */
        private suspend fun useOnEntity(target: Entity, action: (Int) -> Unit): Boolean {
            val index = if(usedIndex == -1) bot.inventory.computeIndexForId(usedId) else OptionalInt.of(usedIndex)
            if (index.isEmpty) {
                // We don't have the item.
                bot.log("I don't have ${itemName(usedId)}.")
                return false
            }
            val id = bot.inventory[index.asInt]?.id
            if (usedId != id) {
                bot.log("Can't find ${itemName(usedId)} on index ${index.asInt}.")
                return false
            }
            if (handler.movement.walkUntilReached(target).await()) {
                val cond = SuspendableCondition{ bot.isInteractingWith(target) }
                bot.log("Using ${itemName(usedId)} on $target.")
                action(index.asInt)
                return cond.submit(30).await()
            }
            return false

        }

        /**
         * An action that forces a [Bot] to use an item on another item in their inventory. Sends the
         * [ItemOnItemMessageReader] packet.
         */
        fun onItem(targetId: Int): Boolean {
            val usedIndex = bot.inventory.computeIndexForId(usedId)
            val targetIndex = bot.inventory.computeIndexForId(targetId)
            if (usedIndex.isEmpty || targetIndex.isEmpty) {
                bot.log("I don't have ${itemName(usedId)} or ${itemName(targetId)}.")
                return false
            }
            bot.log("Using ${itemName(usedId)} on ${itemName(targetId)}.")
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
    fun useItem(id: Int, index: Int = -1): UseItemAction = UseItemAction(id, index)

    /**
     * An action that drops an item from the inventory of a [Bot]. Unsuspends when the item is dropped and/or
     * destroyed.
     */
    suspend fun dropItem(id: Int): SuspendableFuture {
        bot.log("Dropping ${itemName(id)}.")
        val index = bot.inventory.computeIndexForId(id)
        if (index.isEmpty) {
            // We don't have the item.
            bot.log("I don't have ${itemName(id)}.")
            return SuspendableFuture().signal(false)
        }
        val cond = SuspendableCondition({
                                            bot.inventory[index.asInt] == null || bot.interfaces.isOpen(
                                                DestroyItemDialogueInterface::class)
                                        })
        bot.output.sendInventoryItemClick(5, index.asInt, id)
        if (itemDef(id).isTradeable) {
            return cond.submit()
        } else {
            cond.submit().await()
            bot.log("Destroying ${itemName(id)}.")
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
            bot.log("Can't find ${itemName(id)}.")
            return false
        }
        val clickId = bot.inventory[clickIndex]?.id
        if (clickId != id) {
            bot.log("Can't find ${itemName(id)} on $clickIndex.")
            return false
        }
        bot.log("Clicking item ${itemName(clickId)}, option $option.")
        bot.output.sendInventoryItemClick(option, clickIndex, clickId)
        return true
    }
}