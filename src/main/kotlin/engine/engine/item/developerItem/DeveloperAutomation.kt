package engine.item.developerItem

import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import engine.bot.coordinator.CombatCoordinator
import engine.bot.coordinator.skill.SkillingCoordinator
import engine.bot.coordinator.skill.SkillingCoordinator.Companion.profitMode
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot

/**
 * Explicit sessions backed by the same coordinators and factories used by autonomous bots.
 *
 * @author lare96
 */
internal object DeveloperAutomation {
    val combatSkills = setOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS, Skill.RANGED)
    val skillIds: List<Int> get() = SkillingCoordinator.factories.keys.sorted()

    fun skill(bot: Bot, id: Int): BotScript {
        val factory = requireNotNull(SkillingCoordinator.factories[id]) { "Use Combat for this skill." }
        bot.profitMode = false
        return factory.getTrainingScript(bot, bot.skill(id).staticLevel, ArrayList())
    }

    fun combat(bot: Bot): BotScript = object : DynamicBotScript(bot) {
        override suspend fun run(): Boolean {
            val before = bot.scriptStack.size()
            CombatCoordinator(true).accept(bot)
            if (bot.scriptStack.size() == before) {
                bot.sendMessage("No eligible combat camp/loadout. Check your levels, equipment, food and supplies.")
            }
            return true
        }
    }
}
