package api.bot.zone

import api.attr.Attr
import api.bot.Suspendable.waitFor
import api.bot.action.BotActionHandler
import api.predef.*
import api.predef.ext.*
import game.item.degradable.jewellery.TeleportJewellery
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlin.time.Duration.Companion.seconds

/**
 * A [TravelStrategy] that uses teleport jewellery before walking to the final destination.
 *
 * This strategy looks for any available charged variant of [jewellery], withdraws or retrieves one through the supplied
 * [BotActionHandler], opens its teleport dialogue, selects [option], then falls back to [WalkingTravelStrategy] from the
 * teleport arrival position to [dest].
 *
 * This is useful for routes where jewellery provides a cheap long-distance shortcut, but the teleport does not place
 * the bot exactly on its target tile.
 *
 * @property jewellery The teleport jewellery definition that provides the item ids and degradation behavior.
 * @property option The dialogue option index to click after opening the jewellery teleport menu.
 * @author lare96
 */
class JewelleryTravelStrategy(private val jewellery: TeleportJewellery, private val option: Int) : TravelStrategy {

    companion object {

        /**
         * The candidate jewellery items this bot can use for the current travel attempt.
         *
         * This list is rebuilt by [canTravel] before each travel check. It is stored as a bot attribute so [travel] can
         * reuse the same filtered item list without recomputing the jewellery variants.
         */
        private val Bot.jewelleryItems by Attr.list<Item>()
    }

    override fun canTravel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean {
        bot.jewelleryItems.clear()
        for (id in jewellery.items) {
            bot.jewelleryItems.add(Item(id))
        }
        if (!jewellery.crumbles && jewellery.items.size > 1) {
            // Remove the final uncharged jewellery item. We can't teleport with it.
            bot.jewelleryItems.removeLast()
        }
        if (!handler.hasAny(bot.jewelleryItems)) {
            // Bot will try to buy the jewellery item in the future if we don't have it.
            bot.jewelleryItems.firstOrNull()?.id?.apply { bot.preferences.wantedItems += this }
            return false
        }
        return true
    }

    override suspend fun travel(bot: Bot, handler: BotActionHandler, dest: Position): Boolean {
        val count = bot.jewelleryItems.size
        if (count > 0) {
            val item = handler.retrieveAny(bot.jewelleryItems)
            if (item != null) {
                handler.inventory.clickItem(4, item.id)
                if (!waitFor(5.seconds) { OptionDialogue::class in bot.overlays }) {
                    bot.log("Teleport dialogue did not open for ${item.name}.")
                    return false
                }
                val prev = bot.position
                if (handler.widgets.clickDialogueOption(option)) {
                    if (!waitFor(5.seconds) { prev != bot.position }) {
                        bot.log("Teleport via ${item.name} did not happen.")
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