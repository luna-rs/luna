package api.bot.action

import api.bot.Signals.interacting
import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import io.luna.game.model.Entity
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.`in`.GroundItemClickMessageReader
import io.luna.net.msg.`in`.ItemOnItemMessageReader
import io.luna.net.msg.`in`.NpcClickMessageReader
import io.luna.net.msg.`in`.ObjectClickMessageReader
import io.luna.net.msg.`in`.PlayerClickMessageReader

/**
 * A [BotActionHandler] implementation for interaction related actions.
 */
class BotInteractionActionHandler(bot: Bot) : BotActionHandler(bot) {

    /**
     * A builder for use-item interactions.
     */
    inner class UseItemAction(private val usedId: Int) {

        /**
         * Use an item on a generic interactable entity.
         */
        private suspend fun useOnEntity(target: Entity, action: (Int) -> Unit): SuspendableFuture {
            if (!bot.position.isWithinDistance(target.position, Position.VIEWING_DISTANCE)) {
                if (!movement.walk(target.position, Position.VIEWING_DISTANCE).await()) {
                    // Walk to entity did not complete successfully.
                    return SuspendableFuture().signal(false)
                }
            }
            val usedIndex = bot.inventory.computeIndexForId(usedId)
            if (usedIndex.isEmpty) {
                // We don't have the item.
                return SuspendableFuture().signal(false)
            }
            val suspendCond = SuspendableCondition(interacting(target), 60)
            action(usedIndex.asInt)
            return suspendCond.submit()
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
            output.useItemOnItem(targetIndex.asInt, usedIndex.asInt, targetId, usedId)
            return true
        }

        /**
         * An action that forces a [Bot] to use an item in their inventory on [target].
         */
        suspend fun onNpc(target: Npc): SuspendableFuture =
            useOnEntity(target) { output.useItemOnNpc(usedId, it, target) }

        /**
         * An action that forces a [Bot] to use an item in their inventory on [target].
         */
        suspend fun onPlayer(target: Player): SuspendableFuture =
            useOnEntity(target) { output.useItemOnPlayer(usedId, it, target) }

        /**
         * An action that forces a [Bot] to use an item in their inventory on [target].
         */
        suspend fun onObject(target: GameObject) =
            useOnEntity(target) { output.useItemOnObject(usedId, it, target) }
    }

    /**
     * An action that forces the [Bot] to interact with [target]. If the bot is out of viewing distance, it will walk
     * within viewing distance before the correct interaction is sent. The returned future will unsuspend when the bot
     * is interacting with the [target] or if the task cannot complete.
     *
     * One of the following packets will be sent from this function: [PlayerClickMessageReader],
     * [ObjectClickMessageReader], [NpcClickMessageReader], or [GroundItemClickMessageReader].
     *
     * @param target The target to interact with.
     * @param option The interaction option.
     */
    suspend fun interact(target: Entity, option: Int): SuspendableFuture {
        if (!bot.position.isWithinDistance(target.position, Position.VIEWING_DISTANCE)) {
            if (!movement.walk(target.position, Position.VIEWING_DISTANCE).await()) {
                // Walk to entity did not complete successfully.
                return SuspendableFuture().signal(false)
            }
        }
        val suspendCond = SuspendableCondition(interacting(target), 60)
        when (target) {
            is Player -> output.sendPlayerInteraction(option, target)
            is Npc -> output.sendNpcInteraction(option, target)
            is GameObject -> output.sendObjectInteraction(option, target)
            is GroundItem -> output.sendGroundItemInteraction(option, target)
            else -> throw IllegalStateException("This entity cannot be interacted with.")
        }
        return suspendCond.submit()
    }

    /**
     * An action builder that forces a [Bot] to use an item in their inventory on a player, NPC, item, or object.
     */
    fun useItem(usedInventoryIndex: Int): UseItemAction = UseItemAction(usedInventoryIndex)
}