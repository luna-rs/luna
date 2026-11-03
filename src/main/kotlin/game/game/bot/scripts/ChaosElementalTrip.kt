package game.bot.scripts

import api.predef.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.player.item.consume.food.Food
import io.luna.game.model.mob.bot.Bot

/**
 * Owned endurance supplies for a deep-Wilderness Chaos Elemental outing. The existing combat loadout retains the
 * account's saved combat-build restrictions; neither admission nor Madness recovery creates equipment or supplies.
 *
 * @author lare96
 */
internal object ChaosElementalTrip {
    const val FOOD_COUNT = 20
    const val MINIMUM_HEAL = 16
    const val MINIMUM_HITPOINTS = 70

    /**
     * Keeps a high-offence low-Hitpoints account out of a boss that can inflict 28 damage in one Discord hit.
     */
    fun ready(bot: Bot): Boolean = bot.skill(SKILL_HITPOINTS).staticLevel >= MINIMUM_HITPOINTS &&
        Food.ID_TO_FOOD.filterValues { it.heal >= MINIMUM_HEAL }
            .keys.sumOf { bot.itemTracker.count(it).toLong() } >= FOOD_COUNT
}
