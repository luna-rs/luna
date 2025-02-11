package api.bot.scripts

import api.bot.CoroutineBotScript
import api.bot.SuspendableCondition
import api.predef.*
import api.predef.ext.*
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.Region
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.delay
import kotlin.streams.toList

/**
 * A [CoroutineBotScript] that makes a [Bot] return to the ::home area. If they are nearby, they will walk. Otherwise,
 * the ::home command will be used.
 */
class GoHomeScript(bot: Bot) : CoroutineBotScript(bot) {

    override suspend fun run() {
        // Generate random items.
        val randomItems = { bot: Bot ->
            val allItems = ItemDefinition.ALL.stream().toList()
            val items = mutableListOf<Item>()
            repeat(bot.inventory.computeRemainingSize()) {
                items += Item(allItems.random().id)
            }
            items
        }
        bot.inventory.addAll(randomItems(bot)) // Give bot random items.

        // Normally you'd check if the bot is in a minigame, the wilderness, etc. depending on what the bot is doing.
        // But it's just an example.
        val home = Luna.settings().game().startingPosition()
        if (bot.position.isWithinDistance(home, Region.SIZE)) {
            // We're close to home, walk.
            handler.movement.walk(home, Position.VIEWING_DISTANCE).await()
        } else {
            // We're away from home, use command.
            val suspendableCond = SuspendableCondition({ bot.position.isViewable(home) })
            output.sendCommand("home")
            suspendableCond.submit().await()
        }

        output.chat("Finally, I've arrived at home!")
        delay(2000)

        val bankObject = handler.banking.findNearestBank()!! // Should never be null, we're at ::home.
        handler.interactions.interact(1, bankObject).await() // Wait until bank is open.

        output.chat("Let me deposit my items!")
        if (handler.banking.depositAll()) { // Deposit all items.
            handler.widgets.clickCloseInterface().await() // Close banking interface.

            // Make bot dance while dropping items.
            bot.inventory.addAll(randomItems(bot))
            delay(2000)
            output.chat("Wait... more items? Here you go!")
            world.schedule(3) {
                if(bot.inventory.size() == 0 || bot.state != EntityState.ACTIVE)
                    it.cancel()
                bot.animation(Animation(if (rand().nextBoolean()) 866 else 862))
            }
            handler.interactions.dropAllItems() // Drop all items.
        }
        delay(2000)
        output.chat("I guess I'll logout now! Bye!")
        delay(2000)
        handler.widgets.clickLogout() // Logout button.
    }
}