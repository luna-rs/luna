package api.bot.action.prayer

import api.combat.npc.NpcCombatHandler
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * What players know about how each kind of monster attacks: the know-how an experienced player brings to a fight.
 *
 * Most monsters only ever use the standard melee attack, and that's plain to anyone who looks at them. Monsters with
 * their own attacks (wizards, archers, dragons and the like) become known from the attacks players take from them,
 * so the common knowledge grows as people fight them. Attacks a protection prayer blocked count too, since everyone
 * still sees what was thrown at them. It is not saved; it builds up again after a restart.
 *
 * @author lare96
 */
object MonsterStyles {

    /**
     * How many of a monster's attacks need to have been seen before its usual style is common knowledge.
     */
    const val KNOWN_AFTER = 3

    /**
     * How many attacks of each style each NPC id has made, indexed by [CombatDamageType.ordinal].
     */
    private val seen = HashMap<Int, IntArray>()

    /**
     * Records that [npc] attacked a player with [style].
     */
    fun record(npc: Npc, style: CombatDamageType) {
        seen.getOrPut(npc.id) { IntArray(CombatDamageType.entries.size) }[style.ordinal]++
    }

    /**
     * Returns the style [npc] usually attacks with, or `null` if that isn't known yet.
     */
    fun usualStyle(npc: Npc): CombatDamageType? {
        if (NpcCombatHandler.usesStandardAttack(npc)) {
            return CombatDamageType.MELEE
        }
        val counts = seen[npc.id] ?: return null
        if (counts.sum() < KNOWN_AFTER) {
            return null
        }
        return CombatDamageType.entries.maxBy { counts[it.ordinal] }
    }
}
