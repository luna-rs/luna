package api.bot.scripts

import api.bot.CoroutineBotScript
import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.delay
import world.player.settings.emote.Emote

/**
 * A [CoroutineBotScript] that makes a [Bot] demonstrate some of its capabilities, and then logout.
 */
class ExampleSubScript(bot: Bot) : CoroutineBotScript(bot) {

    /**
     * The item the bot will attempt to buy from the general store.
     */
    private val item = item("Lobster").id

    override suspend fun run() {
        delay(3000)
        output.chat("Hi! I am a test bot running an example script.")
        delay(3000)
        output.chat("I was written with Kotlin coroutines.")
        delay(3000)
        bot.inventory.add(Item(995, 1000)) // Just in case they need gold.
        output.chat("First, I'll buy some lobster from the shop and eat it.")
        delay(3000)
        val shopKeeper = handler.interactions.findNearestNpc("Shop keeper")
        if (shopKeeper == null) {
            output.chat("I couldn't find a shop. I'll move on.")
        } else {
            handler.interactions.interact(2, shopKeeper)
            delay(3000)
            handler.shop.buy1(item).await()
            delay(1500)
            handler.widgets.clickCloseInterface()
            delay(3000)
            handler.inventory.clickItem(1, item)
            delay(1000)
            output.chat("Yummy dude!")
            delay(1000)
        }
        delay(2000)
        output.chat("Now, I will unequip all my stuff and bank all my items.")
        handler.equipment.unequipAll()
        delay(2000)
        val nearestBank = handler.banking.findNearestBank()
        if (nearestBank == null) {
            output.chat("Hmm, strange. I can't find a bank anywhere nearby.")
            delay(2000)
            output.chat("I'll move on to the next task.")
            delay(2000)
        } else {
            handler.interactions.interact(1, nearestBank)
            delay(1000)
            handler.banking.depositAll()
            delay(1000)
        }
        output.chat("Now, I will walk to Edgeville.")
        delay(750)
        val edgevillePosition = Position(3087, 3492).translate(rand(2), rand(2))
        handler.movement.walk(edgevillePosition).await()
        delay(2000)
        output.chat("Finally! I'm here! I'll teleport back home now.")
        bot.animation(Animation(if (rand().nextBoolean()) 866 else 862))
        delay(2000)
        output.sendCommand("home")
        delay(5000)
        output.chat("Finally, I'm back home! This concludes the test script.")
        delay(2000)
        output.chat("Bye now!")
        bot.animation(Animation(Emote.WAVE.id))
        delay(2000)
        handler.widgets.clickLogout()
    }
}