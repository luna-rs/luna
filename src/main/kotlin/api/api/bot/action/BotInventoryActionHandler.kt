package api.bot.action

import api.bot.SuspendableCondition
import api.predef.*
import api.predef.ext.*
import game.player.item.consume.food.Food
import io.luna.game.model.Entity
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.DestroyItemDialogue
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.`in`.ItemOnItemMessageReader
import kotlinx.coroutines.future.await

/**
 * Handles bot-driven inventory actions.
 *
 * This action handler is responsible for inventory item clicks, item dropping, item destruction, and
 * "use item on target" interactions. It sends the same packet-style actions that a real client interaction would
 * normally produce.
 *
 * Higher-level bot scripts should use this handler when they need to:
 * - Click an inventory item option.
 * - Drop or destroy an inventory item.
 * - Drop every item, or every item matching a specific id.
 * - Use one inventory item on another item.
 * - Use an inventory item on an NPC, player, object, or other supported entity target.
 *
 * @param bot The bot that will perform the inventory actions.
 * @param handler The parent bot action handler that owns this inventory action handler.
 * @author lare96
 */
class BotInventoryActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * Builds and performs "use item" actions for a specific inventory item.
     *
     * This builder stores the item id that should be used, along with an optional inventory index. If the index is not
     * supplied, the bot will search its inventory for the first matching item id.
     *
     * @property usedId The id of the inventory item being used.
     * @property usedIndex The preferred inventory slot of the item being used, or `-1` to search by id.
     */
    inner class UseItemAction(private val usedId: Int, private val usedIndex: Int) {

        /**
         * Uses the selected inventory item on an interactable world entity.
         *
         * This method first resolves the inventory slot of the item being used. If [usedIndex] is `-1`, the bot
         * searches its inventory for [usedId]. Otherwise, the supplied index is trusted and then validated against
         * the expected item id.
         *
         * After the item slot is resolved, the bot attempts to navigate close enough to [target]. If navigation
         * succeeds, [action] is invoked with the resolved inventory index to send the appropriate item-on-entity packet.
         *
         * The action is considered successful if the bot either reaches a ground item target within one tile, or
         * begins interacting with the supplied target.
         *
         * @param target The entity that the inventory item should be used on.
         * @param action The packet-sending action that accepts the resolved inventory index.
         * @return `true` if the use action appeared to start successfully; otherwise `false`.
         */
        private suspend fun useOnEntity(target: Entity, action: (Int) -> Unit): Boolean {
            val index = if (usedIndex == -1) bot.inventory.computeIndexForId(usedId) else usedIndex
            if (index == -1) {
                bot.log("I don't have ${itemName(usedId)}.")
                return false
            }

            val id = bot.inventory[index]?.id
            if (usedId != id) {
                bot.log("Can't find ${itemName(usedId)} on index ${index}.")
                return false
            }

            if (bot.navigator.navigate(target, true).await() == NavigationResult.REACHED) {
                val cond = SuspendableCondition {
                    (target is GroundItem && bot.isWithinDistance(target, 1)) ||
                            bot.isInteractingWith(target)
                }

                bot.log("Using ${itemName(usedId)} on $target.")
                action(index)
                return cond.submit(30).await()
            }

            return false
        }

        /**
         * Uses the selected inventory item on another inventory item.
         *
         * This sends the item-on-item packet handled by [ItemOnItemMessageReader]. Both items must exist in the bot's
         * inventory before the packet is sent.
         *
         * @param targetId The id of the inventory item that [usedId] should be used on.
         * @return `true` if both items were found and the item-on-item packet was sent.
         */
        fun onItem(targetId: Int): Boolean {
            val usedIndex = bot.inventory.computeIndexForId(usedId)
            val targetIndex = bot.inventory.computeIndexForId(targetId)
            if (usedIndex == -1 || targetIndex == -1) {
                bot.log("I don't have ${itemName(usedId)} or ${itemName(targetId)}.")
                return false
            }

            bot.log("Using ${itemName(usedId)} on ${itemName(targetId)}.")
            bot.output.useItemOnItem(targetIndex, usedIndex, targetId, usedId)
            return true
        }

        /**
         * Uses the selected inventory item on an NPC.
         *
         * The bot will first navigate into interaction range, then send the item-on-NPC packet.
         *
         * @param target The NPC that the item should be used on.
         * @return `true` if the item-on-NPC action appeared to start successfully.
         */
        suspend fun onNpc(target: Npc) =
            useOnEntity(target) { bot.output.useItemOnNpc(usedId, it, target) }

        /**
         * Uses the selected inventory item on a player.
         *
         * The bot will first navigate into interaction range, then send the item-on-player packet.
         *
         * @param target The player that the item should be used on.
         * @return `true` if the item-on-player action appeared to start successfully.
         */
        suspend fun onPlayer(target: Player) =
            useOnEntity(target) { bot.output.useItemOnPlayer(usedId, it, target) }

        /**
         * Uses the selected inventory item on a game object.
         *
         * The bot will first navigate into interaction range, then send the item-on-object packet.
         *
         * @param target The game object that the item should be used on.
         * @return `true` if the item-on-object action appeared to start successfully.
         */
        suspend fun onObject(target: GameObject) =
            useOnEntity(target) { bot.output.useItemOnObject(usedId, it, target) }
    }

    /**
     * Creates an action builder for using an inventory item on another target.
     *
     * If [index] is supplied, the builder will attempt to use the item from that exact inventory slot.
     *
     * If [index] is `-1`, the inventory is searched for the first matching [id] whenever the action is performed.
     *
     * @param id The id of the item that should be used.
     * @param index The preferred inventory index of the item, or `-1` to search by item id.
     * @return A [UseItemAction] that can complete the item-on-target action.
     */
    fun useItem(id: Int, index: Int = -1): UseItemAction = UseItemAction(id, index)

    /**
     * Drops or destroys one inventory item.
     *
     * This method clicks the fifth inventory option for the item, which is expected to be the normal drop option. If
     * the item is tradeable, the item should leave the inventory directly. If the item is not tradeable and opens a
     * [DestroyItemDialogue], the bot clicks the destroy confirmation button instead.
     *
     * The action is considered successful once the item leaves the inventory or the destroy dialogue is accepted.
     *
     * @param id The id of the item to drop or destroy.
     * @return `true` if the item was dropped or destroyed; otherwise `false`.
     */
    suspend fun dropItem(id: Int): Boolean {
        bot.log("Dropping ${itemName(id)}.")

        val index = bot.inventory.computeIndexForId(id)
        if (index == -1) {
            bot.log("I don't have ${itemName(id)}.")
            return false
        }

        val openDestroyCond =
            SuspendableCondition { bot.inventory[index] == null || DestroyItemDialogue::class in bot.overlays }

        bot.output.sendInventoryItemClick(5, index, id)

        if (openDestroyCond.submit(5).await()) {
            if (itemDef(id).isTradeable) {
                return true
            }

            bot.log("Destroying ${itemName(id)}.")

            val clickDestroyCond = SuspendableCondition { DestroyItemDialogue::class !in bot.overlays }
            bot.output.clickButton(14175)

            if (!clickDestroyCond.submit().await()) {
                bot.log("Could not click accept on DestroyItemDialogue.")
                return false
            }

            return true
        }

        bot.log("Could not drop/destroy ${bot.inventory[index]}.")
        return false
    }

    /**
     * Drops or destroys all matching inventory items.
     *
     * If [id] is supplied, only items with that id are dropped or destroyed. If [id] is `null`, every item in the
     * bot's inventory is dropped or destroyed.
     *
     * This method stops immediately if any individual drop action fails.
     *
     * @param id The optional item id to drop, or `null` to drop every item.
     * @return `true` if every requested item was removed from the inventory.
     */
    suspend fun dropAll(id: Int? = null): Boolean {
        for (item in bot.inventory) {
            if (item != null && (item.id == id || id == null) && !dropItem(item.id)) {
                return false
            }
        }

        return if (id == null) bot.inventory.size() == 0 else !bot.inventory.contains(id)
    }

    /**
     * Clicks an inventory item option.
     *
     * If [index] is supplied, the method validates that the expected item id exists at that slot.
     *
     * If [index] is `-1`, the bot searches for the first matching item id before sending the click.
     *
     * This only confirms that the packet was sent. It does not wait for any post-click effect such as eating food,
     * opening an interface, equipping gear, drinking a potion, or starting an action.
     *
     * @param option The inventory option index to click.
     * @param id The expected item id.
     * @param index The preferred inventory index, or `-1` to search by item id.
     * @return `true` if the item was found and the inventory click packet was sent.
     */
    fun clickItem(option: Int, id: Int, index: Int = -1): Boolean {
        val clickIndex = if (index == -1) bot.inventory.computeIndexForId(id) else index
        if (clickIndex == -1) {
            bot.log("Can't find ${itemName(id)}.")
            return false
        }

        val clickId = bot.inventory[clickIndex]?.id
        if (clickId != id) {
            bot.log("Can't find ${itemName(id)} at index $clickIndex.")
            return false
        }

        bot.log("Clicking item ${itemName(clickId)}, option $option.")
        bot.output.sendInventoryItemClick(option, clickIndex, clickId)
        return true
    }

    /**
     * Attempts to eat any food currently in the bot's inventory.
     *
     * This scans the inventory from first slot to last slot and eats the first food item whose heal amount is at least
     * [minimumHeal]. The item is used through an inventory item-click packet, so this method only confirms that a
     * valid food item was found and clicked.
     *
     * @param minimumHeal The minimum heal amount the food must provide.
     * @return `true` if a matching food item was found and clicked, or `false` if no matching food was available.
     */
    fun eatAnyFood(minimumHeal: Int = 0): Boolean {
        bot.inventory.withIndex().forEach { (index, item) ->
            if (item != null) {
                val food = Food.ID_TO_FOOD[item.id]
                if (food != null && food.heal >= minimumHeal) {
                    bot.output.sendInventoryItemClick(1, index, item.id)
                    return true
                }
            }
        }
        return false
    }
}