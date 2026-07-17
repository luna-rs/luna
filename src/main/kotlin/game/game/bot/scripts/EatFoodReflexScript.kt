package game.bot.scripts

import api.bot.script.ReflexBotScript
import io.luna.game.model.mob.bot.Bot

//todo docs
class EatFoodReflexScript(bot: Bot) : ReflexBotScript(bot) {
    private val inventoryActions = bot.actionHandler.inventory
    override fun shouldReact(): Boolean {
        return bot.emotions.isNervousAboutHp && inventoryActions.hasAnyFood()
    }

    override suspend fun run(): Boolean {
        inventoryActions.eatAnyFood()
        return !bot.emotions.isNervousAboutHp || !inventoryActions.hasAnyFood()
    }
}