package engine.bot.coordinator.skill

import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.SKILL_COOKING
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.skills.CookFoodBotScript
import game.skill.cooking.cookFood.Food
import game.bot.scripts.skills.MakeDoughBotScript
import game.bot.scripts.skills.CutFoodBotScript
import game.bot.scripts.skills.AssembleFoodBotScript
import game.obj.resource.fillable.WaterResource
import game.skill.cooking.prepareFood.IncompleteFood
import api.predef.rand

/**
 * A [SkillingScriptFactory] that creates cooking bot scripts.
 *
 * Cooking bots train by selecting the best cookable food for their current Cooking level, then running
 * a [CookFoodBotScript] for a generated duration. Owned plain pizzas and toppings can instead be assembled
 * into meat, anchovy, or pineapple pizzas, which award Cooking experience. Whole cakes can also be prepared
 * with owned chocolate bars or chocolate dust at level 50 for 30 Cooking experience each.
 *
 * Zone selection is personality-based:
 *
 * - Dumb and non-dextrous bots use less optimal banking locations:
 *   - Al Kharid Bank
 *   - Varrock East Bank
 * - All other bots use Rogues' Den, which is the more efficient cooking spot.
 *
 * Non-training Cooking can prepare dough from owned flour and water, including a water-filling prerequisite.
 * Owned pineapples can also be cut into rings when a knife is available. These recipes grant no experience.
 * Pizza and pie assembly select owned ingredients, including alternate meats and refillable water containers.
 * Owned bowls of nettle tea can be combined with milk at level 20, returning the empty bucket without awarding XP.
 * Meat- or potato-based incomplete stews can be completed at level 25. Uncooked stews can be prepared as curry at level 60
 * with spice or three curry leaves per stew. These zero-XP preparation steps are selected only for non-training.
 * Initial stews use owned bowls of water and potato or cooked meat at level 25 for 2 XP per operation.
 * Nettle-water preparation requires level 20 and grants no XP. Non-training selection can fill owned empty bowls
 * first when the other ingredient is already available; training requires a complete XP-awarding input set.
 * Owned bowls of nettle tea and empty cups can be processed at level 20 for the existing recipe's 52 XP,
 * with the empty bowl returned. This recipe is eligible for training and non-training selection.
 * Milk can be added to owned cups of nettle tea at level 20 as a non-training step, returning the empty bucket.
 * Ordinary food cooking remains the fallback activity.
 *
 * @author lare96
 */
object CookingScriptFactory : SkillingScriptFactory(SKILL_COOKING) {

    override fun getTrainingScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        getAssemblyScript(bot, level, training = true)?.let { return it }
        if (bot.personality.isDumb && !bot.personality.isDextrous) {
            zones += SubZone.AL_KHARID_BANK
            zones += SubZone.VARROCK_EAST_BANK
        } else {
            zones += SubZone.ROGUES_DEN
        }

        return CookFoodBotScript(
            bot,
            getBestActivity(bot, level, { it.lvl }, Food.entries.filter { bot.itemTracker.count(it.raw) > 0 }),
            getDuration(bot),
            zones
        )
    }

    override fun getProfitScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        if (rand(0.25)) getNonTrainingPreparation(bot, level)?.let { return it }
        return getTrainingScript(bot, level, zones)
    }

    /** Selects a level-appropriate owned dough recipe using the existing personality rules. */
    internal fun getPreparationScript(bot: Bot, level: Int): MakeDoughBotScript? {
        val options = IncompleteFood.DOUGH.values.flatMap { food ->
            WaterResource.FILLED_IDS.map { MakeDoughBotScript(bot, food, it, getDuration(bot)) }
        }.filter { it.isEligible() || it.canPrepareWater() }
        return getBestActivity(bot, level, { it.requiredLevel }, options)
    }

    /** Chooses eligible owned dough preparation, food cutting, or food assembly for non-training Cooking. */
    internal fun getNonTrainingPreparation(bot: Bot, level: Int): BotScript? {
        val cutting = getBestActivity(bot, level, { it.requiredLevel },
            CutFoodBotScript.RECIPES.keys.map { CutFoodBotScript(bot, it, getDuration(bot)) }
                .filter { it.isEligible() })
        return listOfNotNull(getPreparationScript(bot, level), cutting, getAssemblyScript(bot, level)).randomOrNull()
    }

    /**
     * Selects an owned, validated food-assembly recipe using the existing level and personality rules.
     * Training requires positive recipe experience; zero-XP preparation and water refills are non-training only.
     */
    internal fun getAssemblyScript(bot: Bot, level: Int, training: Boolean = false): AssembleFoodBotScript? =
        getBestActivity(bot, level, { it.requiredLevel },
            AssembleFoodBotScript.RECIPES.filter { !training || it.exp > 0.0 }.flatMap { food ->
                food.otherIngredients.map { AssembleFoodBotScript(bot, food, getDuration(bot), secondary = it) }
            }.filter { it.isEligible() || (!training && it.canPrepareWater()) })
}
