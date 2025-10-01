package api.bot.action

import api.bot.SuspendableCondition
import io.luna.game.model.Entity
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.`in`.GroundItemClickMessageReader
import io.luna.net.msg.`in`.NpcClickMessageReader
import io.luna.net.msg.`in`.ObjectClickMessageReader
import io.luna.net.msg.`in`.PlayerClickMessageReader

/**
 * A [BotActionHandler] implementation for interaction related actions.
 */
class BotInteractionActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * An action that forces the [Bot] to interact with [target]. If the bot is out of viewing distance, it will walk
     * within viewing distance before the correct interaction is sent. This function will unsuspend when the bot
     * is interacting with the [target] or if the task cannot complete.
     *
     * One of the following packets will be sent from this function: [PlayerClickMessageReader],
     * [ObjectClickMessageReader], [NpcClickMessageReader], or [GroundItemClickMessageReader].
     *
     * @param option The interaction option.
     * @param target The target to interact with.
     * @return `false` if the interaction was unsuccessful.
     */
    suspend fun interact(option: Int, target: Entity): Boolean {
        if (handler.movement.walkUntilReached(target).await()) {
            bot.resetInteractingWith()
            bot.resetInteractionTask()
            val cond = SuspendableCondition({ bot.isInteractingWith(target) }, 30)
            when (target) {
                is Player -> bot.output.sendPlayerInteraction(option, target)
                is Npc -> bot.output.sendNpcInteraction(option, target)
                is GameObject -> bot.output.sendObjectInteraction(option, target)
                is GroundItem -> bot.output.sendGroundItemInteraction(option, target)
                else -> throw IllegalStateException("This entity cannot be interacted with.")
            }
            return cond.submit().await()
        }
        return false
    }
}