package api.bot.action

import game.skill.magic.RuneRequirement
import game.skill.magic.Staff
import io.luna.game.model.def.CombatSpellDefinition
import java.util.Random
import kotlin.math.ln

/**
 * Gives each bot a stable, damage-weighted preference across all available combat spells.
 * Every damaging spell has a chance, including lower tiers; stronger spells receive more weight.
 * Callers enforce spellbook, level, equipment and supplies before using this ordering.
 *
 * @author lare96
 */
internal object NpcCombatSpellSelection {
    /**
     * Uses weighted sampling without replacement so filtering unavailable spells preserves the preference order.
     */
    fun comparator(identity: Long): Comparator<CombatSpellDefinition> {
        val random = Random(identity * -7046029254386353131L)
        val scores = CombatSpellDefinition.getSpells().values.associateWith {
            ln(random.nextDouble().coerceAtLeast(Double.MIN_VALUE)) / it.maxHit.coerceAtLeast(1)
        }
        return compareBy { scores.getValue(it) }
    }

    /**
     * Counts the runes replaced by a proposed staff for one cast.
     */
    fun runeSavings(spell: CombatSpellDefinition, staff: Int): Int =
        spell.required.filterIsInstance<RuneRequirement>()
            .filter { it.rune in Staff.ID_TO_STAFF[staff]?.represents.orEmpty() }.sumOf { it.amount }

    /**
     * Breaks equivalent staff ties without relying on registry order.
     */
    fun staffPreference(identity: Long, staff: Int): Double =
        Random((identity * -7046029254386353131L) xor (staff.toLong() * 0x9E3779B9L)).nextDouble()
}
