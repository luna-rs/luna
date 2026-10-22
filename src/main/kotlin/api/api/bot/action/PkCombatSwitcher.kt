package api.bot.action

import api.predef.*
import api.predef.ext.*
import engine.bot.gear.BotGearPurpose
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot

/**
 * Owns timed, carried-only equipment switches for one active PK fight.
 * Selection reuses CombatLoadout; equipment uses normal click handlers and spells use native autocast checks.
 * No supplies are created or withdrawn. The combat script remains the only owner of attack initiation.
 *
 * @property nanoTime Monotonic clock used for decision deadlines rather than wall-clock time.
 * @author lare96
 */
internal class PkCombatSwitcher(private val bot: Bot, private val handler: BotActionHandler,
                              private val nanoTime: () -> Long = System::nanoTime) {
    /**
     * Whether the caller can attack, should wait for its reaction window, or must abandon an unusable fight.
     *
     * @author lare96
     */
    enum class Result { READY, WAITING, UNAVAILABLE }

    /**
     * A stable policy instance and per-fight selection state; failed disciplines are not retried indefinitely.
     */
    private val combat = BotCombatActionHandler(bot, handler)
    private val failed = mutableSetOf<BotGearPurpose>()
    private var active: Pair<BotGearPurpose, CombatLoadout>? = null
    private var nextDecision: Long? = null

    /**
     * Clears only switch-owned timing and spell state when the owning combat script ends.
     */
    fun clear() {
        active = null
        nextDecision = null
        failed.clear()
        clearMagic()
    }

    /**
     * Waits one reaction window before the first switch and reconsiders counters at that same cadence.
     * Supply loss interrupts attacks immediately; a replacement is chosen at the next decision deadline.
     * Native interaction waits can extend completion beyond the decision cadence.
     */
    suspend fun prepare(target: Player): Result {
        if (bot.preferences.isSkiller || bot.preferences.pk == null || !validTarget(target)) return Result.UNAVAILABLE
        val now = nanoTime()
        val current = active?.takeIf { ready(it.first, it.second) }
        if (current == null) {
            bot.combat.target = null
            clearMagic()
        }
        if (nextDecision == null) nextDecision = now + combat.pkSwitchDelay().inWholeNanoseconds
        if (now < nextDecision!!) return if (current != null) Result.READY else Result.WAITING
        nextDecision = now + combat.pkSwitchDelay().inWholeNanoseconds

        val choices = listOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED, BotGearPurpose.MAGIC)
            .filter { it !in failed }.mapNotNull { purpose ->
                CombatLoadout.find(bot, purpose, carriedOnly = true)?.takeIf { it.gear[Equipment.WEAPON] != null }
                    ?.let { purpose to it }
            }.toMap()
        val purpose = combat.selectPkStyle(target, choices.keys) ?: return Result.UNAVAILABLE
        val selected = choices.getValue(purpose)
        if (current?.first == purpose && ready(purpose, current.second)) return Result.READY
        bot.combat.target = null
        clearMagic()
        if (!apply(target, purpose, selected)) {
            failed += purpose
            active = null
            clearMagic()
            return if (failed.size == 3) Result.UNAVAILABLE else Result.WAITING
        }
        active = purpose to selected
        return Result.READY
    }

    /**
     * Rechecks target legality after suspensions and actual weapon, ammunition, levels and rune stock before use.
     */
    private fun validTarget(target: Player): Boolean = target.isAlive && target.isViewableFrom(bot) &&
        bot.combat.checkMultiCombat(target) && bot.controllers.checkCombat(target)

    /**
     * Checks the actual selected weapon and consumables, without trusting tracked bank ownership.
     */
    private fun ready(purpose: BotGearPurpose, selected: CombatLoadout): Boolean {
        val weapon = selected.gear[Equipment.WEAPON] ?: return false
        if (bot.equipment.weapon?.id != weapon || !equipDef(weapon).meetsAllRequirements(bot)) return false
        if (purpose == BotGearPurpose.MAGIC) {
            val spell = selected.spell ?: return false
            return bot.spellbook == spell.spellbook && bot.magic.level >= spell.level &&
                bot.combat.magic.selectedSpell == CombatSpellDefinition.NONE &&
                selected.runes.all { (id, amount) -> bot.inventory.computeAmountForId(id) >= amount } &&
                bot.combat.magic.isAutocasting && bot.combat.magic.autocastSpell == spell
        }
        if (bot.combat.magic.isAutocasting || bot.combat.magic.selectedSpell != CombatSpellDefinition.NONE) return false
        if (purpose == BotGearPurpose.RANGED) {
            val ammo = selected.gear[Equipment.AMMUNITION] ?: weapon
            if (bot.equipment.computeAmountForId(ammo) == 0) return false
        }
        return bot.combat.weapon.styleDef in BotCombatActionHandler.allowedStyles(bot, bot.combat.weapon.typeDef.styles)
    }

    /**
     * Stops automatic attacks while normal equip clicks apply a carried outfit. Two-handed layouts omit shields.
     * Magic removes carried-outfit gaps with negative spell accuracy rather than leaving plate armour equipped.
     */
    private suspend fun apply(target: Player, purpose: BotGearPurpose, selected: CombatLoadout): Boolean {
        if (!validTarget(target)) return false
        val order = listOf(Equipment.WEAPON, Equipment.AMMUNITION) +
            selected.gear.indices.filter { it != Equipment.WEAPON && it != Equipment.AMMUNITION }
        for (slot in order) {
            val id = selected.gear[slot]
            if (id != null && bot.equipment[slot]?.id != id && !handler.equipment.equip(id)) return false
            if (!validTarget(target)) return false
            if (purpose == BotGearPurpose.MAGIC && id == null && slot != Equipment.WEAPON) {
                val worn = bot.equipment[slot]
                if (worn != null && equipDef(worn.id).getBonus(Equipment.EquipmentBonus.MAGIC_ATTACK.index) < 0 &&
                    !handler.equipment.unequip(slot)) return false
                if (!validTarget(target)) return false
            }
        }
        if (purpose == BotGearPurpose.MAGIC) {
            if (!combat.setAutocastSpell(selected.spell ?: return false)) return false
        } else {
            val styles = BotCombatActionHandler.allowedStyles(bot, bot.combat.weapon.typeDef.styles).filter {
                if (purpose == BotGearPurpose.RANGED) Skill.RANGED in it.exp else Skill.RANGED !in it.exp
            }
            val style = styles.firstOrNull() ?: return false
            bot.combat.weapon.changeStyle(style.button)
        }
        return validTarget(target) && ready(purpose, selected)
    }

    /**
     * Prevents a later melee/ranged phase or another script from inheriting a previous autocast selection.
     */
    private fun clearMagic() {
        bot.combat.magic.selectedSpell = CombatSpellDefinition.NONE
        bot.combat.magic.autocastSpell = CombatSpellDefinition.NONE
        bot.combat.magic.isAutocasting = false
        bot.combat.magic.refreshAutocast()
    }
}
