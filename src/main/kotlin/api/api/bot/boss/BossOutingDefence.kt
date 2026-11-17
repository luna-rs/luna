package api.bot.boss

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotCombatActionHandler
import api.bot.script.BotScript.ActionSafety
import engine.controllers.WildernessController
import engine.controllers.WildernessLocatableController.levelAt
import game.bot.scripts.combat.CombatBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.mob.MobReference
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.state.CombatContext.COMBAT_TIMER_TICKS

/**
 * Remembers one locally witnessed attack against a travelling Wilderness teammate. Native identities reject slot
 * reuse, and the current membership prevents an old cue from authorizing a replacement outing.
 *
 * @author lare96
 */
object BossOutingDefence {

    private var Bot.outingThreat by Attr.nullableObj(Threat::class)

    /**
     * One launched attack, held by its observer without retaining either player's live session.
     *
     * @author lare96
     */
    private class Threat(val membership: Any, val attacker: MobReference, val victim: MobReference, val tick: Long)

    /**
     * Records visible launches against an actual bot member. Human followers are never protected as teammates;
     * an outsider can be either a human or a bot, regardless of permanent clan allegiance.
     */
    @JvmStatic
    fun observeAttack(attacker: Player, victim: Player) {
        val member = victim as? Bot ?: return
        if (!BossOutings.isWildernessMember(member) || BossOutings.allies(attacker, victim) ||
            !inWilderness(attacker) || !inWilderness(victim)) return
        val attackerRef = attacker.reference ?: return
        val victimRef = victim.reference ?: return
        for (player in victim.world.locator.findViewablePlayers(victim)) {
            val observer = player as? Bot ?: continue
            if (observer === victim || !BossOutings.isWildernessMember(observer) ||
                !BossOutings.allies(observer, victim) || !inWilderness(observer) ||
                !attacker.isViewableFrom(observer) || !victim.isViewableFrom(observer)) continue
            val membership = BossOutings.member(observer) ?: continue
            observer.outingThreat = Threat(membership, attackerRef, victimRef, observer.world.currentTick)
        }
    }

    /**
     * Returns only a current visible aggressor that native Wilderness and single-combat rules permit attacking.
     * The safe reflex boundary decides when to pause movement and launch the defensive combat child.
     */
    @JvmStatic
    fun pendingTarget(bot: Bot): Player? {
        val threat = currentThreat(bot) ?: return null
        val attacker = threat.attacker.resolve() as? Player ?: return null
        return attacker.takeIf { bot.actionHandler.combat.canAttackWildernessPlayer(it) }
    }

    /**
     * Revalidates the exact defensive target after equipment actions and before every later attack.
     */
    @JvmStatic
    fun permits(bot: Bot, attacker: Player): Boolean = pendingTarget(bot) === attacker

    /**
     * Handles a teammate's witnessed attack before unrelated witnessed-fight escape. Personal combat, low-health
     * survival, strong actions and an already active escape keep their normal priority.
     */
    @JvmStatic
    fun respond(bot: Bot): Boolean {
        val target = pendingTarget(bot) ?: return false
        val parent = bot.scriptStack.current() ?: return false
        if (bot.combat.inCombat() || parent is CombatBotScript || !parent.allowsCombatResponse ||
            bot.emotions.isNervousAboutHp || !ActionSafety.COMBAT.allows(bot) ||
            bot.actionHandler.combat.isFleeingWilderness || BotCombatActionHandler.hasWitnessedEscapeOwner(bot)) {
            return false
        }
        bot.scriptStack.pushHead(CombatBotScript(bot, target,
            targetAllowed = { permits(bot, target) }, onCombatFinished = { forget(bot) },
            allowPlayerRetargeting = false))
        return true
    }

    /**
     * Releases an outing's pending cue when its owner departs, arrives, dies or logs out.
     */
    @JvmStatic
    fun forget(bot: Bot) {
        bot.outingThreat = null
    }

    private fun currentThreat(bot: Bot): Threat? {
        val threat = bot.outingThreat ?: return null
        val attacker = threat.attacker.resolve() as? Player
        val victim = threat.victim.resolve() as? Bot
        if (threat.membership !== BossOutings.member(bot) || !BossOutings.isWildernessMember(bot) ||
            !inWilderness(bot) || attacker == null || victim == null || !inWilderness(attacker) ||
            !inWilderness(victim) || !BossOutings.allies(bot, victim) || BossOutings.allies(bot, attacker) ||
            !attacker.isViewableFrom(bot) || !victim.isViewableFrom(bot) ||
            bot.world.currentTick - threat.tick !in 0 until COMBAT_TIMER_TICKS.toLong() ||
            (attacker.combat.target !== victim && attacker.combat.target !== bot &&
                !(victim.combat.inCombat() && victim.combat.lastCombatWith === attacker))) {
            forget(bot)
            return null
        }
        return threat
    }

    private fun inWilderness(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive &&
        !player.client.isPendingLogout && levelAt(player.position) > 0 &&
        player.controllers.primary is WildernessController
}
