package game.bot.scripts.combat

import api.bot.action.PkRiskBudget
import api.combat.death.PlayerDeathItems
import api.predef.*
import engine.controllers.Controllers.inWilderness
import engine.controllers.WildernessLocatableController.levelAt
import game.bot.scripts.combat.CombatBotScript.InitialState
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType

/**
 * How a bot answers a player who attacks it in the Wilderness, the way a player would.
 *
 * The first answer weighs the same things a player does in the second after being hit: the matchup, what the bot
 * would drop if it died against what it owns, whether it came out to fight, its health and food, and whether it can
 * get away at all. Bots that can't fight (skillers, or no usable style) always run. Once running, a bot the attacker
 * keeps hitting ([CAUGHT_HITS] attacks while it runs) has not got away; one that can fight then turns on its attacker,
 * unless it is almost out of the Wilderness or already gave up on the fight. A PK patrol's hunting rules never stop
 * a bot defending itself ([defenceSession]).
 */
object WildernessDefence {

    /**
     * How many attacks a bot takes while running before it accepts it can't outrun its attacker.
     */
    const val CAUGHT_HITS = 2

    /**
     * The Wilderness level at or below which a caught bot keeps running anyway, since the ditch is a few steps away.
     */
    private const val NEAR_DITCH_LEVEL = 2

    /**
     * The least run energy a bot needs to outrun a player. Walking is no faster than the player chasing it.
     */
    private const val MIN_RUN_ENERGY = 10.0

    /**
     * The player attacking [bot] in the ordinary Wilderness that it may hit back, or `null` if none is.
     */
    @JvmStatic
    fun attackingPlayer(bot: Bot): Player? {
        if (!bot.inWilderness()) return null
        val combat = bot.actionHandler.combat
        return listOfNotNull(bot.combat.lastAttackReceived?.attacker, bot.combat.lastCombatWith)
            .filterIsInstance<Player>().firstOrNull {
                it !== bot && it.isAlive && it.state == EntityState.ACTIVE && it.inWilderness() &&
                    combat.canRetaliateAgainst(it)
            }
    }

    /**
     * The PK patrol that owns a fight against [attacker], or `null` when its hunting rules wouldn't let it chase them.
     * Defending itself is not a hunt, so a patrol's boundary never stops the bot hitting back.
     */
    @JvmStatic
    fun defenceSession(session: PkBotScript?, attacker: Player): PkBotScript? =
        session?.takeIf { it.allowsPursuit(attacker) }

    /**
     * Whether [bot] can fight back at all: skillers don't, and nor does a build with no style it is willing to use.
     */
    fun canFightBack(bot: Bot, attacker: Player): Boolean =
        bot.personality.type != PersonalityTemplateType.SKILLER && !bot.preferences.isSkiller &&
            attacker.combat.isAttackable && bot.actionHandler.combat.prepareTrainingStyle()

    /**
     * The first answer to [attacker]. Rolled, so two bots in the same spot don't always do the same thing.
     *
     * - The matchup and personality start it: confidence fights an even or weaker attacker, intelligence runs from a
     *   stronger one.
     * - A smart bot with most of its wealth on it would rather get away; one with nothing to lose is happy to fight.
     * - PKers came out to fight.
     * - A bot that can't outrun its attacker (frozen, or out of run energy) fights unless the attacker is a threat.
     * - A bot already nervous about its health with no food runs.
     */
    @JvmStatic
    fun respond(bot: Bot, attacker: Player): InitialState {
        if (!canFightBack(bot, attacker)) return InitialState.RUN
        val chance = fightChance(bot, attacker)
        val fights = rand(chance)
        bot.log("Attacked by ${attacker.username} in the Wilderness: " +
                    "${if (fights) "fighting back" else "getting away"} (${(chance * 100).toInt()}% to fight).")
        return if (fights) InitialState.ATTACK else InitialState.RUN
    }

    /**
     * The chance that [bot] fights [attacker], from 5% to 95%.
     */
    internal fun fightChance(bot: Bot, attacker: Player): Double {
        val personality = bot.personality
        val threat = bot.actionHandler.combat.isThreat(attacker)
        var chance = if (threat) 1.0 - maxOf(0.0, personality.intelligence - personality.confidence * 0.30)
            else personality.confidence * 0.70 + personality.intelligence * 0.30
        val share = riskShare(bot)
        chance -= personality.intelligence * share * 0.5
        if (share == 0.0) chance += 0.15
        if (bot.preferences.isPker) chance += 0.20
        if (cantOutrun(bot)) chance = maxOf(chance, if (threat) 0.5 else 0.9)
        if (bot.emotions.isNervousAboutHp && !bot.actionHandler.inventory.hasAnyFood()) chance = minOf(chance, 0.1)
        return chance.coerceIn(0.05, 0.95)
    }

    /**
     * Whether a running [bot] that [attacker] keeps hitting turns to fight. It keeps running when it can't fight, gave
     * up on the fight, or is almost out of the Wilderness. A bot facing a much stronger attacker turns only on its
     * confidence; the rest know running isn't working.
     */
    fun turnsWhenCaught(bot: Bot, attacker: Player, gaveUp: Boolean): Boolean {
        if (gaveUp || levelAt(bot.position) <= NEAR_DITCH_LEVEL || !canFightBack(bot, attacker)) return false
        if (bot.emotions.isNervousAboutHp && !bot.actionHandler.inventory.hasAnyFood()) return false
        return !bot.actionHandler.combat.isThreat(attacker) || rand(bot.personality.confidence)
    }

    /**
     * Whether [bot] has no hope of outrunning a player: it is held by a spell or has no run energy left.
     */
    private fun cantOutrun(bot: Bot): Boolean = bot.status.isHeldBySpell() || bot.runEnergy < MIN_RUN_ENERGY

    /**
     * The share of everything [bot] owns that it would drop if it died now, from 0 to 1.
     */
    private fun riskShare(bot: Bot): Double {
        val risk = PkRiskBudget.assess(bot, PlayerDeathItems.losesItems(bot, Luna.settings().game().betaMode()))
        if (risk.wealth.signum() <= 0) return 0.0
        return (risk.deathLoss.toDouble() / risk.wealth.toDouble()).coerceIn(0.0, 1.0)
    }
}
