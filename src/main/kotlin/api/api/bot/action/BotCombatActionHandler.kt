package api.bot.action

import engine.controllers.Controllers.inWilderness
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.bot.scripts.PkBotScript.Companion.LOW_LEVEL_ANCHOR_POINTS
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.future.await

class BotCombatActionHandler(private val bot: Bot, private val handler: BotActionHandler) {
    // todo docs
    // todo support for both 'fake' items and actually requiring bots to have runes, etc. ???
    fun findAndEatFood() {} // finds and eats any food in your inventory (allow to select for faster)
    fun swapAndUseSpecial() {} // swap to any special weapon in the inventory, spam special attack until you have no special left, swap back to original weapon (^^^)
    suspend fun fleeWilderness(): Boolean {
        handler.widgets.clickRunning(true)
        if (bot.inWilderness()) {
            bot.isWandering = false
            handler.widgets.clickRunning(true)
            if (bot.wildernessLevel < 20) {
                bot.output.sendCommand("home")
                return true // todo verify bot is actually home before returning
            } else if (bot.walking.isEmpty) {
                bot.navigator.navigate(LOW_LEVEL_ANCHOR_POINTS.random(), true).await()
                bot.output.sendCommand("home")
                return true // todo verify bot is actually home before returning
            }
            // todo override movement stack, move to anchor point.. etc.
            return false
        }
        return true
    }
    suspend fun fleeCombat() {
        handler.widgets.clickRunning(true)
        // TODO think of best place to move the bot. maybe create anchor points in the wild for this?
    }

    fun selectAndUseSpell() {} // selects a spell for use in the next attack
}