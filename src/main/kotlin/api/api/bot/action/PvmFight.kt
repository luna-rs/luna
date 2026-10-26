package api.bot.action

import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot

/**
 * The monsters a bot is fighting right now, looked up once so everything that reacts to the fight sees the same
 * opponents.
 *
 * @property primary The monster the bot is fighting: its target, or else the attacker it last fought.
 * @property attackers The monsters in view that have the bot as their target. Usually includes [primary].
 * @author lare96
 */
class PvmFight(val primary: Npc, val attackers: Collection<Npc>) {

    companion object {

        /**
         * Looks up the monsters [bot] is fighting.
         *
         * @return The fight, or `null` if the bot isn't fighting a monster.
         */
        fun of(bot: Bot): PvmFight? {
            val target = (bot.combat.target as? Npc)?.takeIf { it.isAlive }
            if (target == null && !bot.combat.inCombat()) {
                return null
            }
            val attackers = bot.world.locator.findViewableNpcs(bot) { it.isAlive && it.combat.target == bot }
            val primary = target ?: (bot.combat.lastCombatWith as? Npc)?.takeIf { it in attackers }
                ?: attackers.firstOrNull()
            return primary?.let { PvmFight(it, attackers) }
        }
    }

    /**
     * Every monster in this fight, each once.
     */
    val opponents: Set<Npc> by lazy { LinkedHashSet<Npc>(attackers.size + 1).apply { add(primary); addAll(attackers) } }
}
