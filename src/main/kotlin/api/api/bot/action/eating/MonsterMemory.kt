package api.bot.action.eating

import api.attr.Attr
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import kotlin.math.roundToInt

/**
 * What a bot has felt from monsters this session: the biggest hit each kind of monster has dealt it, and when it last
 * took any hit at all.
 *
 * A player's sense of how hard a monster hits comes from two places, what they already know about it and what it has
 * done to them. [expectedMaxHit] combines both, so a bot that knows little about a monster underestimates it until a
 * big hit teaches it otherwise.
 *
 * @author lare96
 */
class MonsterMemory {

    companion object {

        /**
         * The session memory of each bot. It is not saved, so a bot that logs back in starts learning again.
         */
        val Bot.monsterMemory by Attr.obj { MonsterMemory() }

        /**
         * How much of a monster's real maximum hit a bot knows about before it has been hit hard by one.
         *
         * @param intelligence The bot's intelligence, from `0.0` to `1.0`.
         * @return The knowledge [expectedMaxHit] works from.
         */
        fun knowledge(intelligence: Double): Double = 0.4 + 0.6 * intelligence
    }

    /**
     * The biggest hit each NPC id has dealt the bot.
     */
    private val biggestHits = HashMap<Int, Int>()

    /**
     * The tick the bot last took damage from anything, or `-1` if it hasn't been hit this session.
     */
    var lastHitTick = -1L
        private set

    /**
     * Records a hit the bot took on [tick]. Only hits from NPCs teach the bot about monsters.
     *
     * @param npcId The id of the NPC that dealt the hit, or `null` if something else did.
     * @param amount The damage dealt.
     * @param tick The tick the hit landed.
     */
    fun remember(npcId: Int?, amount: Int, tick: Long) {
        if (amount <= 0) {
            return
        }
        lastHitTick = tick
        if (npcId != null) {
            biggestHits.merge(npcId, amount, ::maxOf)
        }
    }

    /**
     * Returns the biggest hit [npcId] has dealt the bot this session, or `0` if it hasn't hit the bot yet.
     */
    fun biggestHit(npcId: Int): Int = biggestHits[npcId] ?: 0

    /**
     * Estimates the most [npc] can hit the bot for.
     *
     * A bot with full [knowledge] knows the monster's maximum hit before it fights it. Less knowledge scales that
     * down, the way a player who hasn't looked a monster up only has a rough idea. Either way, the biggest hit the
     * bot has actually taken from it wins.
     *
     * @param npc The monster.
     * @param knowledge How much of the monster's real maximum hit the bot knows about, from `0.0` to `1.0`.
     * @return The expected maximum hit, at least `1`.
     */
    fun expectedMaxHit(npc: Npc, knowledge: Double): Int {
        val known = (npc.combatDef().maximumHit * knowledge.coerceIn(0.0, 1.0)).roundToInt()
        return maxOf(biggestHit(npc.id), known, 1)
    }
}
