package game.bot.scripts

import api.predef.*
import api.bot.boss.BossFood
import api.bot.boss.BossTarget
import io.luna.game.model.mob.bot.Bot

/**
 * Owned endurance supplies for a deep-Wilderness Chaos Elemental outing. The existing combat loadout retains the
 * account's saved combat-build restrictions; neither admission nor Madness recovery creates equipment or supplies.
 *
 * @author lare96
 */
internal object ChaosElementalTrip {
    const val MINIMUM_HITPOINTS = 70

    /**
     * Keeps a high-offence low-Hitpoints account out of a boss that can inflict 28 damage in one Discord hit.
     */
    fun ready(bot: Bot, group: Boolean = false): Boolean =
        bot.skill(SKILL_HITPOINTS).staticLevel >= (if (group) 60 else MINIMUM_HITPOINTS) &&
        BossFood.ownedReady(bot, BossTarget.CHAOS_ELEMENTAL, group)
}
