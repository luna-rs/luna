package api.bot.script

import api.predef.weapon
import game.skill.magic.CombinationRune
import game.skill.magic.RuneRequirement
import game.skill.magic.SpellRequirement
import game.skill.magic.Staff
import io.luna.Luna
import io.luna.game.model.item.Item
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.bot.Bot

/**
 * Whether the normal Magic validator waives spell costs for this bot.
 * Activity scripts still validate their own input items, spellbook, and levels.
 *
 * @author lare96
 */
fun Bot.bypassesSpellCosts(): Boolean = Luna.settings().game().betaMode() || rights >= PlayerRights.ADMINISTRATOR

/**
 * Plans one cast's rune stacks from owned stock, without changing inventory or equipment.
 * Equipped staffs remove their represented costs. Sufficient base runes are preferred; otherwise one
 * sufficient combination stack substitutes each element. A combination rune covering two required
 * elements is counted once at the larger cost. Non-rune requirements remain the activity's responsibility.
 *
 * This conservative startup/banking plan does not combine fragmented partial rune supplies. The normal
 * player spell action uses Magic.checkRequirements to determine the actual carried cost at execution.
 * Missing supplies remain in the plan as base runes so the shared wanted-item handling can request them.
 *
 * @param requirements Existing spell requirements, supplying only their rune portions to this plan.
 * @param bankedOnly Whether only bank stock may satisfy the planned withdrawal.
 * @return Distinct rune stacks needed for one cast, or no costs when gameplay waives them.
 * @author lare96
 */
fun Bot.productionRuneCosts(requirements: List<SpellRequirement>, bankedOnly: Boolean = false): List<Item> {
    if (bypassesSpellCosts()) return emptyList()
    fun owned(id: Int): Long = bank.computeAmountForId(id).toLong() +
        if (bankedOnly) 0 else inventory.computeAmountForId(id)
    val staff = equipment.weapon?.id?.let { Staff.ID_TO_STAFF[it] }
    val costs = linkedMapOf<Int, Int>()
    val runes = requirements.filterIsInstance<RuneRequirement>().groupBy { it.rune }
    for ((rune, requirementsForRune) in runes) {
        if (staff != null && rune in staff.represents) continue
        val amount = requirementsForRune.sumOf { it.amount }
        val id = if (owned(rune.id) >= amount) rune.id else
            CombinationRune.entries.firstOrNull { rune in it.represents && owned(it.id) >= amount }?.id ?: rune.id
        costs[id] = maxOf(costs[id] ?: 0, amount)
    }
    return costs.map { (id, amount) -> Item(id, amount) }
}
