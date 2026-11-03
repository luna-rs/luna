package api.bot.action

import api.bot.Suspendable.waitFor
import api.predef.*
import engine.combat.status.StatusEffectType
import engine.controllers.Controllers.inWilderness
import game.combat.spells.CrumbleUndead
import game.combat.spells.GodSpells
import game.combat.spells.IbanStaff
import game.content.mageArena.MageArena
import game.skill.magic.Magic
import game.skill.magic.SpellCost
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import kotlin.time.Duration.Companion.seconds

/**
 * Deliberate combat Magic shared by supplied NPC sessions and the Magic phase of a saved PK profile. Decisions use
 * visible target state, native costs, cast restrictions and the existing one-to-six-tick personality cadence. All
 * effects, rune removal, cooldowns and XP remain in the ordinary player spell handlers.
 *
 * @author lare96
 */
internal class BotCombatMagic(private val bot: Bot, private val handler: BotActionHandler) {
    companion object {
        private val holds = listOf(CombatSpell.ENTANGLE, CombatSpell.SNARE, CombatSpell.BIND)
        private val drains = listOf(CombatSpell.VULNERABILITY, CombatSpell.CURSE, CombatSpell.STUN,
            CombatSpell.CONFUSE, CombatSpell.ENFEEBLE, CombatSpell.WEAKEN)
        private val utilities = listOf(CombatSpell.TELEBLOCK) + holds + drains

        /**
         * Filters specialist damage spells using existing ownership-independent native unlock state. Crumble is
         * selected only after a concrete undead target is visible, rather than becoming a generic camp loadout.
         */
        fun supportedDamage(bot: Bot, spell: CombatSpell, targetSpecific: Boolean = false): Boolean = when (spell) {
            CombatSpell.MAGIC_DART -> true
            CombatSpell.IBAN_BLAST -> IbanStaff.remaining(bot) > 0
            CombatSpell.SARADOMIN_STRIKE, CombatSpell.CLAWS_OF_GUTHIX, CombatSpell.FLAMES_OF_ZAMORAK -> {
                val god = MageArena.God.entries.first { it.spell == spell }
                MageArena.stage(bot) >= MageArena.STAFF_GIVEN && MageArena.casts(bot, god) >= MageArena.SPELL_CASTS
            }
            CombatSpell.CRUMBLE_UNDEAD -> targetSpecific
            else -> spell.name.substringBefore('_') in setOf("WIND", "WATER", "EARTH", "FIRE", "SMOKE", "SHADOW", "BLOOD", "ICE")
        }

        /**
         * Optional owned utility supplies, capped at ten casts per rune and shared between spells. Native costing
         * includes current staff and combination-rune substitution. Callers retain slot and PK risk limits.
         */
        fun optionalRuneStock(bot: Bot, weapon: Int?, gear: Array<Int?>, casts: Int = 10): Map<Int, Int> {
            if (bot.spellbook != Spellbook.REGULAR || !BotCombatActionHandler.mayGainExperience(bot, SKILL_MAGIC))
                return emptyMap()
            val stock = bot.inventory.filterNotNull() + bot.bank.filterNotNull()
            val amounts = linkedMapOf<Int, Int>()
            val costs = utilities.map { it.def }.filter { it.level <= bot.magic.level }.map {
                Magic.spellCost(bot.magic.level, stock, { id -> id in gear }, weapon, it.level, it.required)
            }.toMutableList()
            if (bot.magic.level >= GodSpells.CHARGE_LEVEL && MageArena.stage(bot) >= MageArena.STAFF_GIVEN &&
                MageArena.God.entries.any { it.staff == weapon && it.cape in gear }) {
                costs += Magic.spellCost(bot.magic.level, stock, { id -> id in gear }, weapon,
                    GodSpells.CHARGE_LEVEL, GodSpells.CHARGE_RUNES)
            }
            for (cost in costs.filterIsInstance<SpellCost.Castable>()) for (item in cost.items) {
                val owned = stock.filter { it.id == item.id }.sumOf { it.amount.toLong() }
                amounts[item.id] = maxOf(amounts[item.id] ?: 0, minOf(owned, item.amount.toLong() * casts).toInt())
            }
            return amounts
        }
    }

    private var nextDecision = 0L
    private var nextUtility = 0L
    private var nextChargeAttempt = 0L
    private var offensive: CombatSpellDefinition? = null

    /**
     * Selects one native manual cast or retains ordinary damage autocasting. Utility attempts are separated by
     * at least ten ticks, and current stat drains, freeze deadlines and teleblock prevent redundant casts.
     */
    suspend fun prepare(target: Mob, selected: CombatSpellDefinition, tactical: Boolean = true): Boolean {
        if (!target.isAlive || !BotCombatActionHandler.mayGainExperience(bot, SKILL_MAGIC)) return false
        val now = bot.world.currentTick
        val god = MageArena.God.entries.firstOrNull { it.spell == selected.spell }
        if (god != null && bot.magic.level >= GodSpells.CHARGE_LEVEL &&
            bot.equipment[Equipment.CAPE]?.id == god.cape && bot.equipment[Equipment.WEAPON]?.id == god.staff &&
            StatusEffectType.CHARGED !in bot.status && now >= nextChargeAttempt &&
            Magic.spellCost(bot, GodSpells.CHARGE_LEVEL, GodSpells.CHARGE_RUNES) is SpellCost.Castable) {
            nextChargeAttempt = now + 10
            if (bot.output.clickButton(GodSpells.CHARGE_BUTTON)) {
                waitFor(3.seconds) { StatusEffectType.CHARGED in bot.status }
                return false
            }
        }
        var choice = offensive?.takeIf { castable(it, target) } ?: selected
        if (!tactical) choice = selected
        if (nextDecision == 0L) nextDecision = now + handler.combat.pkSwitchDelay().inWholeMilliseconds / 600
        if (tactical && now >= nextDecision) {
            nextDecision = now + handler.combat.pkSwitchDelay().inWholeMilliseconds / 600
            val intelligence = bot.personality.intelligence.coerceIn(0.0, 1.0)
            val dexterity = bot.personality.dexterity.coerceIn(0.0, 1.0)
            if (rand((intelligence + dexterity) / 2)) {
                choice = CombatSpellDefinition.getSpells().values.filter {
                    it.maxHit > 0 && supportedDamage(bot, it.spell, targetSpecific = true) && castable(it, target)
                }.maxByOrNull { bot.combat.magic.getMaxHit(it) } ?: selected
            }
            offensive = choice
            if (now >= nextUtility && rand((intelligence + dexterity) * 0.35)) {
                val utility = utilities.firstOrNull { useful(it, target) && castable(it.def, target) }
                if (utility != null) {
                    choice = utility.def
                    nextUtility = now + 10
                }
            }
        }
        if (!castable(choice, target)) return false
        if (choice != selected || choice.button == -1) {
            return handler.combat.selectAndUseSpell(choice.spell)
        }
        return true
    }

    private fun useful(spell: CombatSpell, target: Mob): Boolean {
        if (target is Npc && target.id in 1532..1535) return false
        return when (spell) {
        CombatSpell.TELEBLOCK -> target is Player && target.inWilderness() && !target.status.isTeleBlocked()
        in holds -> !target.status.isHeldBySpell() && !target.status.isFreezeImmune() &&
            (target is Player || target.combat.target === bot) && bot.combat.magic.canHoldTarget(spell.def, target)
        in drains -> {
            val skill = target.skill(when (spell) {
                CombatSpell.CONFUSE, CombatSpell.STUN -> SKILL_ATTACK
                CombatSpell.WEAKEN, CombatSpell.ENFEEBLE -> SKILL_STRENGTH
                else -> SKILL_DEFENCE
            })
            skill.level >= skill.staticLevel && bot.combat.magic.canCurseTarget(spell.def, target)
        }
        else -> false
        }
    }

    private fun castable(spell: CombatSpellDefinition, target: Mob): Boolean =
        spell.spellbook == bot.spellbook && BotCombatActionHandler.canUseSpellbook(bot, spell.spellbook) &&
            bot.magic.level >= spell.level &&
            (spell.spell != CombatSpell.CRUMBLE_UNDEAD || CrumbleUndead.eligibleTarget(target)) &&
            (spell.spell != CombatSpell.IBAN_BLAST || IbanStaff.remaining(bot) >= if (target is Player) 2 else 1) &&
            supportedOrUtility(spell.spell) &&
            Magic.spellCost(bot, spell.level, spell.required) is SpellCost.Castable

    private fun supportedOrUtility(spell: CombatSpell): Boolean = spell in utilities ||
        supportedDamage(bot, spell, targetSpecific = true)
}
