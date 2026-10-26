package game.bot.scripts

import api.bot.action.eating.PvmEating
import api.bot.script.ReflexBotScript
import game.content.duelArena.DuelArena
import game.content.pestControl.PestControl
import io.luna.game.model.mob.bot.Bot

/**
 * Eats whenever the bot is nervous about its hitpoints and has food, whatever it is doing.
 *
 * A script that eats for itself through [PvmEating] is watching the bot's hitpoints, so this reflex leaves the food to
 * it. Interrupting would pause that script and hand its fight to the combat reflex. Once the script stops checking,
 * this reflex looks after the bot again. Duel Arena and Pest Control sessions own their eating decisions.
 *
 * @author lare96
 */
class EatFoodReflexScript(bot: Bot) : ReflexBotScript(bot) {
    private val inventoryActions = bot.actionHandler.inventory
    override fun shouldReact(): Boolean {
        if (DuelArena.session(bot) != null ||
            bot.scriptStack.current() is PestControlBotScript && PestControl.round.playing(bot)) return false
        return !PvmEating.isAttending(bot) && bot.emotions.isNervousAboutHp && inventoryActions.hasAnyFood()
    }

    override suspend fun run(): Boolean {
        inventoryActions.eatAnyFood()
        return !bot.emotions.isNervousAboutHp || !inventoryActions.hasAnyFood()
    }
}
