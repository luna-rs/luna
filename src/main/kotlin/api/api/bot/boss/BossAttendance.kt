package api.bot.boss

import api.bot.isRealBot
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import game.bot.scripts.NpcCombatScript
import game.player.item.consume.food.Food

/**
 * Independent encounter attendance after the travel party has dissolved. Humans never supply the bot quorum.
 *
 * @author lare96
 */
internal object BossAttendance {
    /**
     * Counts living supplied bots actually running this boss encounter anywhere in its local footprint.
     */
    fun companions(bot: Bot, target: BossTarget): Int = bot.world.locator.findPlayers(target.zone.inside, 128) {
        it !== bot && it.isRealBot() && it.state == EntityState.ACTIVE && it.isAlive &&
            target.zone.area.contains(it.position) && (it as Bot).scriptStack.scripts.any { script ->
                script is NpcCombatScript && script.bossTarget == target &&
                    script.countsForBossCompany()
            }
    }.size

    /**
     * Each bot chooses its own headcount from its current confidence, independently of its former host.
     */
    fun enough(bot: Bot, target: BossTarget): Boolean =
        companions(bot, target) >= BossReadiness.requiredCompanions(bot.personality.confidence)
}
