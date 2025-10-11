package api.bot.zone

import api.attr.Attr
import api.bot.Suspendable.waitFor
import api.bot.action.BotActionHandler
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.OptionDialogueInterface
import world.player.item.jewelleryTeleport.TeleportJewellery
import kotlin.time.Duration.Companion.seconds

/**
 * A [TravelStrategy] implementation that forces a [Bot] to teleport with jewellery to its destination.
 *
 * @author lare96
 */
class JewelleryTravelStrategy(private val jewellery: TeleportJewellery,
                              private val option: Int) : TravelStrategy {
    companion object {

        /**
         * An attribute containing our items to look for before travelling.
         */
        private val Bot.jewelleryItems by Attr.list<Item>()
    }

    override fun canTravel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean {
        bot.jewelleryItems.clear()
        for (id in jewellery.items) {
            bot.jewelleryItems.add(Item(id))
        }
        if (!jewellery.crumbles && jewellery.items.size > 1) {
            bot.jewelleryItems.removeLast()
        }
        return handler.hasAny(bot.jewelleryItems)
    }

    override suspend fun travel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean {
        val count = bot.jewelleryItems.size
        if (count > 0) {
            val item = handler.retrieveAny(bot.jewelleryItems)
            if (item != null) {
                handler.inventory.clickItem(4, item.id)
                if (!waitFor(5.seconds) { bot.interfaces.isOpen(OptionDialogueInterface::class) }) {
                    bot.log("Teleport dialogue did not open for ${itemName(item)}.")
                    return false
                }
                val prev = bot.position
                if (handler.widgets.clickDialogueOption(option)) {
                    if (!waitFor(5.seconds) { prev != bot.position }) {
                        bot.log("Teleport via ${itemName(item)} did not happen.");
                        return false
                    }
                    return WalkingTravelStrategy.travel(bot, handler, dest)
                }
            } else {
                bot.log("No jewellery found.")
            }
        }
        return false
    }
}