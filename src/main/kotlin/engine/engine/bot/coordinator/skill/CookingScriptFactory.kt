package engine.bot.coordinator.skill

import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotScript
import api.bot.script.ownsProductionSupplies
import api.bot.zone.SubZone
import api.bot.zone.ProductionZones
import api.predef.SKILL_COOKING
import api.predef.cooking
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.skills.CookFoodBotScript
import game.skill.cooking.cookFood.Food
import game.bot.scripts.skills.MakeDoughBotScript
import game.bot.scripts.skills.MillFlourBotScript
import game.bot.scripts.skills.CutFoodBotScript
import game.bot.scripts.skills.AssembleFoodBotScript
import game.obj.resource.fillable.WaterResource
import game.skill.cooking.prepareFood.IncompleteFood
import io.luna.game.model.item.Item
import api.predef.rand

/**
 * A [SkillingScriptFactory] that creates cooking bot scripts.
 *
 * Cooking bots train by selecting the best cookable food for their current Cooking level, then running
 * a [CookFoodBotScript] for a generated duration. Owned plain pizzas and toppings can instead be assembled
 * into meat, anchovy, or pineapple pizzas, which award Cooking experience. Whole cakes can also be prepared
 * with owned chocolate bars or chocolate dust at level 50 for 30 Cooking experience each.
 *
 * Registered ranges across ordinary towns share the same zone-selection policy. Less adept bots retain
 * the old Al Kharid and Varrock options, while other bots can still choose Rogues' Den. The shared zoned
 * lifecycle uses personality and distance, then tries the actual facility after arrival.
 *
 * Non-training Cooking can prepare dough from owned flour and water, including a water-filling prerequisite.
 * It can also mill flour at a windmill, picking wheat or buying pots first when none are owned.
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
 * Raw cakes require all four owned ingredients and use the native preparation level and experience.
 * Grapes and jugs of water can be mixed into wine at level 35 for training or non-training selection.
 * Wine experience is awarded later by the existing fermentation task. Non-training preparation can first
 * fill owned empty jugs when grapes are available; training requires already-filled jugs.
 * Ordinary food cooking remains the fallback activity.
 * Summer pies are baked first when raw pies are owned at level 95. While the bot has fewer than 24 whole
 * pies' worth of servings, complete owned summer-pie inputs can also select the next preparation stage.
 * These zero-XP prerequisites require every remaining fruit so a training session can reach the baking step.
 *
 * @author lare96
 */
object CookingScriptFactory : SkillingScriptFactory(SKILL_COOKING) {

    /**
     * Retains the mill's existing non-training role using one captured batch of real grain and pots only.
     */
    internal fun getOwnedMillingScript(bot: Bot): MillFlourBotScript? =
        MillFlourBotScript.ownedSession(bot, getDuration(bot))

    override fun getTrainingScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        if (level >= Food.SUMMER_PIE.lvl && bot.cooking.staticLevel >= Food.SUMMER_PIE.lvl &&
            bot.itemTracker.count(Food.SUMMER_PIE.raw) > 0) {
            return cookingScript(bot, Food.SUMMER_PIE, zones)
        }
        getSummerPiePreparation(bot, level)?.let { return it }
        getAssemblyScript(bot, level, training = true)?.let { return it }

        return cookingScript(bot,
            getBestActivity(bot, level, { it.lvl }, Food.entries.filter { bot.itemTracker.count(it.raw) > 0 }), zones)
    }

    /**
     * Creates the ordinary range-cooking session using the shared personality and facility choices.
     */
    private fun cookingScript(bot: Bot, food: Food?, zones: MutableList<SubZone>): CookFoodBotScript {
        if (bot.personality.isDumb && !bot.personality.isDextrous) {
            zones += SubZone.AL_KHARID_BANK
            zones += SubZone.VARROCK_EAST_BANK
        } else {
            zones += SubZone.ROGUES_DEN
        }
        zones += ProductionZones.RANGES

        return CookFoodBotScript(bot, food, getDuration(bot), zones)
    }

    override fun getProfitScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        getSummerPiePreparation(bot, level)?.let { return it }
        if (rand(0.25)) getNonTrainingPreparation(bot, level)?.let { return it }
        return getTrainingScript(bot, level, zones)
    }

    /**
     * Selects the furthest owned summer-pie preparation stage with all later ingredients already available.
     * Half pies count as one serving and whole pies as two; the preference stops once 48 servings are owned.
     * Baking owned raw pies remains ordinary Cooking training even after that personal reserve is filled.
     */
    internal fun getSummerPiePreparation(bot: Bot, level: Int): AssembleFoodBotScript? {
        if (level < Food.SUMMER_PIE.lvl || bot.cooking.staticLevel < Food.SUMMER_PIE.lvl) return null
        val servings = bot.itemTracker.count(Food.SUMMER_PIE.cooked).toLong() * 2 + bot.itemTracker.count(7220)
        if (servings >= 48 || bot.itemTracker.count(Food.SUMMER_PIE.raw) > 0) return null
        val stages = listOf(
            IncompleteFood.RAW_SUMMER_PIE to listOf(1955),
            IncompleteFood.PART_SUMMER_PIE_2 to listOf(5982, 1955),
            IncompleteFood.PART_SUMMER_PIE_1 to listOf(5504, 5982, 1955),
            IncompleteFood.PIE_SHELL to listOf(1953, 5504, 5982, 1955)
        )
        val stage = stages.firstOrNull { (recipe, remaining) ->
            bot.ownsProductionSupplies((listOf(recipe.baseIngredient) + remaining).map { Item(it) })
        }?.first ?: return null
        return AssembleFoodBotScript(bot, stage, getDuration(bot))
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
        val milling = MillFlourBotScript(bot, MillFlourBotScript.pickMill(bot), getDuration(bot))
        return listOfNotNull(getPreparationScript(bot, level), cutting, getAssemblyScript(bot, level), milling).randomOrNull()
    }

    /**
     * Selects an owned, validated food-assembly recipe using the existing level and personality rules.
     * Training requires positive recipe experience; zero-XP preparation and water refills are non-training only.
     */
    internal fun getAssemblyScript(bot: Bot, level: Int, training: Boolean = false): AssembleFoodBotScript? =
        getBestActivity(bot, level, { it.requiredLevel },
            AssembleFoodBotScript.RECIPES.filter { !training || it.exp > 0.0 }.flatMap { food ->
                val alternatives = if (food == IncompleteFood.UNCOOKED_CAKE) {
                    listOf(food.otherIngredients.first())
                } else {
                    food.otherIngredients
                }
                alternatives.map { AssembleFoodBotScript(bot, food, getDuration(bot), secondary = it) }
            }.filter { it.isEligible() || (!training && it.canPrepareWater()) })
}
