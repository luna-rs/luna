package game.bot.scripts.skills

import api.bot.script.InventoryBotScript
import api.bot.action.BotCombatActionHandler
import api.predef.SKILL_FLETCHING
import engine.bot.coordinator.skill.FletchingScriptFactory
import engine.bot.coordinator.skill.SkillingCoordinator.Companion.profitMode
import game.skill.fletching.attachArrow.Arrow
import game.skill.fletching.stringBow.Bow
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Continues verified Fletching products into their next native recipe using the remaining session budget.
 * Every handoff requires an owned intermediate; no stage returns to log, gem or other primary gathering.
 *
 * @author lare96
 */
internal object FletchingProduction {
    fun owned(bot: Bot, id: Int): Long = bot.inventory.computeAmountForId(id).toLong() +
        bot.bank.computeAmountForId(id)

    fun afterCutting(bot: Bot, bow: Bow, duration: Duration): InventoryBotScript? {
        if (!canContinue(bot, duration)) return null
        return if (bow == Bow.ARROW_SHAFT) {
            MakeArrowBotScript(bot, Arrow.HEADLESS_ARROW, duration).takeIf { it.isEligible() }
        } else {
            StringBowBotScript(bot, bow, duration).takeIf { it.isEligible() }
        }
    }

    fun afterArrows(bot: Bot, arrow: Arrow, duration: Duration): InventoryBotScript? {
        if (!canContinue(bot, duration) || arrow != Arrow.HEADLESS_ARROW) return null
        return FletchingScriptFactory.getFinishedArrowScript(bot, duration)
    }

    fun afterAmmunition(bot: Bot, recipe: FletchAmmunitionBotScript.Companion.Recipe,
                        duration: Duration): InventoryBotScript? {
        if (!canContinue(bot, duration) || !recipe.tool) return null
        val next = FletchAmmunitionBotScript.RECIPES.values.firstOrNull {
            it.key.startsWith("BOLT:") && it.target == recipe.output
        } ?: return null
        return FletchAmmunitionBotScript(bot, next, duration).takeIf { it.isEligible() }
    }

    private fun canContinue(bot: Bot, duration: Duration): Boolean =
        (bot.scriptStack.current() as? InventoryBotScript)?.productionPlan == null && duration > Duration.ZERO &&
        (bot.profitMode || BotCombatActionHandler.wantsToTrain(bot, SKILL_FLETCHING))
}
