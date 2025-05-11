package api.bot.action

import api.bot.SuspendableFuture
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.BotOutputMessageHandler

/**
 * Provides access to collection of functions which [Bot] types can use to perform specialized multi-stage actions that
 * utilize packets from [BotOutputMessageHandler]. These types of actions must be used within [CoroutineBotScript] types to
 * take advantage of being suspended by [SuspendableFuture].
 *
 * Almost every function will return [SuspendableFuture] which can be used to suspend the underlying coroutine
 * and/or send a signal to the future's channel to unsuspend it. Functions may also block coroutines with sub-tasks
 * before returning.
 */
class BotActionHandler(val bot: Bot) {

    /**
     * The banking action handler.
     */
    val banking = BotBankingActionHandler(bot, this)

     /**
     * The shop action handler.
     */
    val shop = BotShopActionHandler(bot, this)

    /**
     * The movement action handler.
     */
    val movement = BotMovementActionHandler(bot, this)

    /**
     * The equipment action handler.
     */
    val equipment = BotEquipmentActionHandler(bot, this)

    /**
     * The inventory action handler.
     */
    val inventory = BotInventoryActionHandler(bot, this)

    /**
     * The interactions action handler.
     */
    val interactions = BotInteractionActionHandler(bot, this)

    /**
     * The widgets action handler.
     */
    val widgets = BotWidgetActionHandler(bot, this)
}
