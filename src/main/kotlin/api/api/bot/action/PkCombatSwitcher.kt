package api.bot.action

import api.predef.*
import api.predef.ext.*
import engine.bot.gear.BotGearPurpose
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.MobReference
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PrayerIcon
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatAction
import java.util.function.BooleanSupplier

/**
 * Owns timed, carried-only equipment switches for one active PK fight.
 * Selection reuses CombatLoadout; equipment uses normal click handlers and spells use native autocast checks.
 * No supplies are created or withdrawn. The combat script remains the only owner of attack initiation.
 *
 * @property acceptsTarget Owning-session policy rechecked after every suspension, including patrol boundaries.
 * @property nanoTime Monotonic clock used for decision deadlines rather than wall-clock time.
 * @author lare96
 */
internal class PkCombatSwitcher(private val bot: Bot, private val handler: BotActionHandler,
                              private val acceptsTarget: (Player) -> Boolean = { true },
                              private val nanoTime: () -> Long = System::nanoTime,
                              private val acceptsPurpose: (BotGearPurpose) -> Boolean = { true },
                              private val ownsFight: () -> Boolean = { true }) {
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
    private val clan = PkClanPolicy(bot)
    private val selection = PkLoadoutSelection(bot)
    private val failed = mutableSetOf<BotGearPurpose>()
    private var active: Pair<BotGearPurpose, CombatLoadout>? = null
    private var nextDecision: Long? = null
    private var pendingCounter: StyleCounter? = null
    private val magicTactics = BotCombatMagic(bot, handler)
    private val ancientPrayer = PkAncientPrayer(bot)
    private var prayerObservation: PrayerObservation? = null
    private var prayerCounterOpponent: MobReference? = null

    /**
     * A counter awaiting a second scheduled check against the same opponent for the same kind of evidence.
     *
     * @author lare96
     */
    private data class StyleCounter(val opponent: MobReference, val purpose: BotGearPurpose, val prayerDriven: Boolean)

    /**
     * An overhead observed on uninterrupted ticks against one opponent.
     *
     * @author lare96
     */
    private data class PrayerObservation(val opponent: MobReference, val protecting: Boolean,
                                         val since: Long, var last: Long)

    /**
     * Uses tactical Magic only during the currently selected, permanently permitted Magic discipline.
     */
    suspend fun prepareSpell(target: Player): Boolean = active?.let { (purpose, selected) ->
        purpose != BotGearPurpose.MAGIC || selected.spell?.let { magicTactics.prepare(target, it) } == true
    } ?: false

    /**
     * Clears only switch-owned timing and spell state when the owning combat script ends.
     */
    fun clear() {
        active = null
        nextDecision = null
        clearStyleConfirmation()
        prayerCounterOpponent = null
        failed.clear()
        clearMagic()
    }

    /**
     * Drops an unfinished observation when the fight is interrupted.
     */
    fun clearStyleConfirmation() {
        pendingCounter = null
        prayerObservation = null
    }

    /**
     * Waits one reaction window before the first switch and reconsiders counters at that same cadence.
     * A usable loadout stays active until an armour or prayer counter wins two consecutive scheduled checks.
     * Supply loss interrupts attacks immediately; a replacement is chosen at the next decision deadline.
     * Native interaction waits can extend completion beyond the decision cadence.
     */
    suspend fun prepare(target: Player): Result {
        if (bot.preferences.isSkiller || bot.preferences.pk == null || !validTarget(target)) {
            clearStyleConfirmation()
            return Result.UNAVAILABLE
        }
        val opponent = MobReference.capture(target)
        if (pendingCounter?.opponent != opponent) pendingCounter = null
        val ancient = bot.spellbook == Spellbook.ANCIENT && BotCombatActionHandler.canUseAncients(bot)
        if (prayerCounterOpponent != opponent || !ancient) prayerCounterOpponent = null
        val now = nanoTime()
        val current = active?.takeIf { acceptsPurpose(it.first) && ready(it.first, it.second) }
        if (current == null) {
            clearStyleConfirmation()
            prayerCounterOpponent = null
            bot.combat.target = null
            clearMagic()
        }
        if (current != null && opponent != null && ancient) observePrayer(target, opponent)
        else prayerObservation = null
        if (nextDecision == null) nextDecision = now + combat.pkSwitchDelay().inWholeNanoseconds
        if (now < nextDecision!!) return if (current != null) Result.READY else Result.WAITING
        nextDecision = now + combat.pkSwitchDelay().inWholeNanoseconds

        val choices = listOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED, BotGearPurpose.MAGIC)
            .filter { it !in failed && acceptsPurpose(it) }.mapNotNull { purpose ->
                selection.select(purpose, carriedOnly = true)?.also { outfit ->
                    clan.cape?.let { outfit.gear[Equipment.CAPE] = it }
                }?.takeIf { it.gear[Equipment.WEAPON] != null }
                    ?.let { purpose to it }
            }.toMap()
        val previousCounter = pendingCounter
        pendingCounter = null
        val decision = combat.selectPkStyleDecision(target, choices.keys) ?: return Result.UNAVAILABLE
        val prayerCounterActive = prayerCounterOpponent != null
        val protectionSustained = prayerObservation?.let { it.protecting && it.last > it.since } == true
        val prayerChoice = if (ancient && current != null && current.first in choices && BotGearPurpose.MAGIC in choices &&
            (prayerCounterActive || decision.purpose == BotGearPurpose.MAGIC &&
                current.first == BotGearPurpose.MAGIC && protectionSustained)) {
            ancientPrayer.counter(target, current, choices)
        } else null
        val purpose = prayerChoice ?: decision.purpose
        val prayerDriven = prayerChoice != null || prayerCounterActive
        if ((decision.armourDriven || prayerDriven) && current != null && current.first != purpose &&
            current.first in choices &&
            (previousCounter?.purpose != purpose || previousCounter.prayerDriven != prayerDriven)) {
            if (opponent != null) pendingCounter = StyleCounter(opponent, purpose, prayerDriven)
            return Result.READY
        }
        val selected = choices.getValue(purpose)
        if (current?.first == purpose && current.second.spell == selected.spell &&
            current.second.gear[Equipment.WEAPON] == selected.gear[Equipment.WEAPON] &&
            current.second.gear[Equipment.AMMUNITION] == selected.gear[Equipment.AMMUNITION] &&
            ready(purpose, current.second)) return Result.READY
        if (!switch(target, purpose, selected)) {
            failed += purpose
            active = null
            clearMagic()
            return if (failed.size == 3) Result.UNAVAILABLE else Result.WAITING
        }
        active = purpose to selected
        prayerCounterOpponent = opponent.takeIf { prayerDriven && purpose != BotGearPurpose.MAGIC }
        return Result.READY
    }

    /**
     * Requires adjacent world-tick observations. A changed overhead or a missed tick invalidates a pending
     * prayer decision, including a return to Magic after a temporary drop in protection.
     */
    private fun observePrayer(target: Player, opponent: MobReference) {
        val tick = bot.world.currentTick
        val protecting = target.prayerIcon == PrayerIcon.PROTECT_FROM_MAGIC
        val previous = prayerObservation
        if (previous != null && previous.opponent == opponent && previous.protecting == protecting &&
            tick in previous.last..previous.last + 1) {
            previous.last = tick
        } else {
            if (pendingCounter?.prayerDriven == true) pendingCounter = null
            prayerObservation = PrayerObservation(opponent, protecting, tick, tick)
        }
    }

    /**
     * Keeps the existing attack action and its approach rules until the new outfit is ready.
     * Pending clicks expire with the switch, including when a script is paused or its target changes.
     */
    private suspend fun switch(target: Player, purpose: BotGearPurpose, selected: CombatLoadout): Boolean {
        val combat = bot.combat
        val originalTarget = combat.target?.takeIf { it === target }
        if (combat.target !== originalTarget) combat.target = null
        val originalAction = bot.actions.first(CombatAction::class.java)
        val policy = originalTarget?.let { combat.getDefaultAttack(it).interactionPolicy }
        val switch = combat.beginEquipmentSwitch(target, policy)
        val canContinue = BooleanSupplier {
            combat.equipmentSwitch === switch && ownsFight() && combat.target === originalTarget && validTarget(target)
        }
        var committed = false
        try {
            clearMagic()
            committed = apply(purpose, selected, canContinue)
            return committed
        } finally {
            if (!committed && combat.equipmentSwitch === switch && combat.target === target &&
                bot.actions.first(CombatAction::class.java) === originalAction) {
                combat.target = null
            }
            combat.finishEquipmentSwitch(switch)
        }
    }

    /**
     * Rechecks target legality after suspensions and actual weapon, ammunition, levels and rune stock before use.
     */
    private fun validTarget(target: Player): Boolean = clan.wearingCape() && acceptsTarget(target) && combat.canAttackPlayer(target)

    /**
     * Checks the actual selected weapon and consumables, without trusting tracked bank ownership.
     */
    private fun ready(purpose: BotGearPurpose, selected: CombatLoadout): Boolean {
        val weapon = selected.gear[Equipment.WEAPON] ?: return false
        if (bot.equipment.weapon?.id != weapon || !equipDef(weapon).meetsAllRequirements(bot)) return false
        if (purpose == BotGearPurpose.MAGIC) {
            val spell = selected.spell ?: return false
            return BotCombatActionHandler.canUseSpellbook(bot, spell.spellbook) &&
                bot.spellbook == spell.spellbook && bot.magic.level >= spell.level &&
                (bot.combat.magic.selectedSpell == CombatSpellDefinition.NONE ||
                    spell.button == -1 && bot.combat.magic.selectedSpell == spell) &&
                selected.runes.all { (id, amount) -> bot.inventory.computeAmountForId(id) >= amount } &&
                (if (spell.button == -1) !bot.combat.magic.isAutocasting else
                    bot.combat.magic.isAutocasting && bot.combat.magic.autocastSpell == spell)
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
    private suspend fun apply(purpose: BotGearPurpose, selected: CombatLoadout, canContinue: BooleanSupplier): Boolean {
        if (!canContinue.asBoolean) return false
        val order = listOf(Equipment.WEAPON, Equipment.AMMUNITION) +
            selected.gear.indices.filter { it != Equipment.WEAPON && it != Equipment.AMMUNITION }
        for (slot in order) {
            val id = selected.gear[slot]
            if (id != null && bot.equipment[slot]?.id != id && !handler.equipment.equip(id, canContinue)) return false
            if (!canContinue.asBoolean) return false
            if (purpose == BotGearPurpose.MAGIC && id == null && slot != Equipment.WEAPON) {
                val worn = bot.equipment[slot]
                if (worn != null && equipDef(worn.id).getBonus(Equipment.EquipmentBonus.MAGIC_ATTACK.index) < 0 &&
                    !handler.equipment.unequip(slot, canContinue)) return false
                if (!canContinue.asBoolean) return false
            }
        }
        if (purpose == BotGearPurpose.MAGIC) {
            val spell = selected.spell ?: return false
            if (spell.button == -1) {
                if (!combat.selectAndUseSpell(spell.spell)) return false
            } else if (!combat.setAutocastSpell(spell)) return false
        } else {
            val style = pkPhysicalStyle(bot, purpose, bot.combat.weapon.typeDef) ?: return false
            bot.combat.weapon.changeStyle(style.button)
        }
        return canContinue.asBoolean && ready(purpose, selected)
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
