package game.bot.scripts.skills

import api.bot.action.BotCombatActionHandler
import api.bot.action.CombatLoadout
import api.bot.action.MagicOnlyCombat
import api.bot.action.MagicSupplyPreparation
import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.naturalDelay
import api.bot.script.ZonedBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearSelector
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Equipment.AMMUNITION
import io.luna.game.model.item.Equipment.EquipmentBonus.MAGIC_ATTACK
import io.luna.game.model.item.Equipment.SHIELD
import io.luna.game.model.item.Equipment.WEAPON
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.CombatStance
import io.luna.game.model.mob.combat.Weapon
import io.luna.game.model.mob.combat.attack.CombatAttack
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Trains with native failed casts and an equipment-validated Magic attack bonus of at most -64.
 * Missing bronze equipment and small rune batches use normal wanted-item acquisition. Low-level accounts
 * manually cast without a staff when its positive bonus would prevent guaranteed splashes.
 * Exhausted supplies trigger banking and bounded replenishment rather than physical attacks.
 *
 * @author lare96
 */
class SplashBotScript(bot: Bot, duration: Duration,
                      zones: MutableList<SubZone> = SPLASH_ZONES.toMutableList()) :
    ZonedBotScript(bot, duration, zones), MagicOnlyCombat {

    override val actionSafety = ActionSafety.COMBAT
    override val magicOnly = true
    override val allowsCombatResponse = false
    private var spell: CombatSpellDefinition? = null
    private var usingStaff: Int? = null
    private var splashEquipment: Array<Int?>? = null
    private val supplies = MagicSupplyPreparation(bot)
    private var searchingZone: SubZone? = null
    private var searchStartedAt: Long? = null
    private var started = false

    companion object {
        const val RUNE_BATCH_CASTS = 100
        const val MAX_SPLASH_MAGIC_BONUS = -64
        val SPLASH_ZONES = listOf(SubZone.LUMBRIDGE_COURT_YARD, SubZone.VARROCK_SEWERS, SubZone.DRAYNOR_SEWERS)
        val ALWAYS_ALLOWED = setOf("Rat")
        private val STARTER_ARMOUR = setOf(1155, 1117, 1075, 1189)
        private var Bot.splashSessions by Attr.long().persist("splash_sessions")
        private var Bot.splashCasts by Attr.long().persist("splash_casts")
        private val SPLASH_SPELLS = listOf(
            CombatSpell.WIND_STRIKE, CombatSpell.WATER_STRIKE, CombatSpell.EARTH_STRIKE, CombatSpell.FIRE_STRIKE,
            CombatSpell.WIND_BOLT, CombatSpell.WATER_BOLT, CombatSpell.EARTH_BOLT, CombatSpell.FIRE_BOLT,
            CombatSpell.WIND_BLAST, CombatSpell.WATER_BLAST, CombatSpell.EARTH_BLAST, CombatSpell.FIRE_BLAST,
            CombatSpell.WIND_WAVE, CombatSpell.WATER_WAVE, CombatSpell.EARTH_WAVE, CombatSpell.FIRE_WAVE)

        /**
         * Retains the session and interrupted shopping deadline through logout or restart.
         *
         * @author lare96
         */
        class SplashData : ZonedBotScriptData() {
            var supplyDeadline = 0L
            var supplyInterruptedAt = 0L
            var started = false
            override fun load(data: JsonObject) {
                super.load(data)
                supplyDeadline = data.get("supply_deadline")?.asLong ?: 0L
                supplyInterruptedAt = data.get("supply_interrupted_at")?.asLong ?: 0L
                started = data.get("started")?.asBoolean ?: false
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("supply_deadline", supplyDeadline)
                data.addProperty("supply_interrupted_at", supplyInterruptedAt)
                data.addProperty("started", started)
            }
        }
    }

    constructor(bot: Bot, data: SplashData) : this(bot, data.duration, data.zones) {
        supplies.deadline = data.supplyDeadline
        supplies.interruptedAt = data.supplyInterruptedAt
        started = data.started
    }

    override suspend fun onInit(resumed: Boolean): Boolean {
        clearCombat()
        if (!BotCombatActionHandler.wantsToTrain(bot, SKILL_MAGIC)) return false
        if (!handler.combat.switchSpellbook(Spellbook.REGULAR)) return false
        var equipment = chooseEquipment()
        if (equipment == null) {
            if (!supplies.obtain(STARTER_ARMOUR.associateWith { 1 })) return false
            equipment = chooseEquipment() ?: return false
        }
        splashEquipment = equipment
        usingStaff = equipment[WEAPON]
        spell = ownedSpell(false) ?: CombatSpell.WIND_STRIKE.def
        if (!prepareRunes()) return false
        if (resumed) {
            if (!validEquipment() && !BotGearLocator(bot, equipment.copyOf(), clearUnselected = true).locateAndEquip()) return false
            forceBanking = true
        }
        return true
    }

    override suspend fun equipment(): BotGearLocator? = splashEquipment?.let { BotGearLocator(bot, it.copyOf(), clearUnselected = true) }
    override suspend fun onEquipmentFailed(equipmentLocator: BotGearLocator) { stop() }

    override suspend fun onBankOpen(initial: Boolean) {
        clearCombat()
        if (!validEquipment() || !prepareRunes()) {
            if (!isPaused()) stop()
            return
        }
        if (!bot.bank.isOpen && !handler.banking.travelToBankOpen()) { stop(); return }
        handler.banking.clickBankingMode(false)
        val selected = spell ?: return
        for ((id, amount) in CombatLoadout.runeCosts(selected, usingStaff)) {
            val missing = amount * RUNE_BATCH_CASTS - bot.inventory.computeAmountForId(id)
            if (missing > 0 && id in bot.bank) handler.banking.withdraw(Item(id, missing))
        }
        handler.banking.withdrawAnyFood(3)
        if (bot.combat.stance == CombatStance.DEFENSIVE) {
            val style = bot.combat.weapon.typeDef.styles.firstOrNull { it.stance != CombatStance.DEFENSIVE }
                ?: run { stop(); return }
            bot.combat.weapon.changeStyle(style.button)
        }
        if (ownedSpell(true) == null || usingStaff != null && !handler.combat.setAutocastSpell(selected)) {
            stop()
            return
        }
        if (!started) {
            bot.splashSessions = (bot.splashSessions + 1).coerceAtLeast(bot.splashSessions)
            started = true
        }
    }

    override suspend fun executeInZone(): Boolean {
        if (!BotCombatActionHandler.wantsToTrain(bot, SKILL_MAGIC) || !validEquipment()) { stop(); return true }
        val available = ownedSpell(true)
        if (available == null) {
            clearCombat()
            forceBanking = true
            return true
        }
        spell = available
        if (usingStaff != null && (!bot.combat.magic.isAutocasting || bot.combat.magic.autocastSpell != available)) {
            if (!handler.combat.setAutocastSpell(available)) { stop(); return true }
        }
        val current = bot.combat.target
        if (usingStaff != null && current is Npc && current.isAlive && bot.combat.inCombat()) return true
        val area = activeZone?.area ?: return false
        if (searchingZone != activeZone) {
            searchingZone = activeZone
            searchStartedAt = null
        }
        val target = world.locator.findNpcs(area.centerPosition, area.tileRadius) {
            it.position in area && isValidTarget(it)
        }.asSequence().filter { bot.isWithinDistance(it, 10) }
            .filter { world.collisionManager.raycast(bot.position, it.position) }
            .minByOrNull { bot.position.computeLongestDistance(it.position) }
        if (target == null) {
            val now = System.nanoTime()
            val started = searchStartedAt ?: now.also { searchStartedAt = it }
            return now - started < 180.seconds.inWholeNanoseconds
        }
        searchStartedAt = null
        if (usingStaff == null && !handler.combat.selectAndUseSpell(available.spell)) return true
        bot.combat.attack(target)
        bot.naturalDelay()
        return true
    }

    override fun onPaused() {
        searchStartedAt = null
        clearCombat()
    }
    override suspend fun finish() { clearCombat() }

    override fun permitsMagicAttack(attack: CombatAttack<*>): Boolean {
        val ready = validEquipment() && BotCombatActionHandler.isNativeMagicAttack(bot, attack)
        if (!ready) {
            clearCombat()
            forceBanking = true
        }
        return ready
    }

    override fun onMagicCast() {
        bot.splashCasts = (bot.splashCasts + 1).coerceAtLeast(bot.splashCasts)
    }

    override fun snapshot(): SplashData = SplashData().also {
        it.duration = duration
        it.zones = originalZones.toMutableList()
        it.supplyDeadline = supplies.deadline
        it.supplyInterruptedAt = supplies.interruptedAt
        it.started = started
    }

    private fun clearCombat() {
        bot.combat.target = null
        bot.combat.autoRetaliateTarget = null
        bot.combat.setFirstAttack(null)
        val magic = bot.combat.magic
        magic.isAutocasting = false
        magic.autocastSpell = CombatSpellDefinition.NONE
        magic.selectedSpell = CombatSpellDefinition.NONE
        magic.refreshAutocast()
    }

    private fun validEquipment(): Boolean = splashEquipment != null &&
        bot.equipment.weapon?.id == usingStaff && bot.equipment.getBonus(MAGIC_ATTACK) <= MAX_SPLASH_MAGIC_BONUS

    private suspend fun prepareRunes(): Boolean {
        val costs = CombatLoadout.runeCosts(spell ?: return false, usingStaff)
        return supplies.obtain(costs.mapValues { it.value * RUNE_BATCH_CASTS }, costs)
    }

    private fun ownedSpell(inventoryOnly: Boolean): CombatSpellDefinition? = SPLASH_SPELLS.asSequence()
        .map { it.def }.filter { it.spellbook == bot.spellbook && bot.magic.level >= it.level }
        .filter { selected -> CombatLoadout.runeCosts(selected, usingStaff).all { (id, amount) ->
            (if (inventoryOnly) bot.inventory.computeAmountForId(id) else bot.itemTracker.count(id)) >= amount
        } }.maxByOrNull { it.level }

    /**
     * Prefers an owned staff only when the complete outfit still guarantees failure. Unarmed manual casting
     * removes its positive bonus, so ordinary bronze armour qualifies without Ranged or Defence training.
     */
    internal fun chooseEquipment(): Array<Int?>? {
        val staves = BotGearSelector.ALL_GEAR.value[WEAPON].asSequence().map { it.id }.distinct()
            .filter { it in bot.itemTracker && equipDef(it).meetsAllRequirements(bot) }
            .filter { WeaponDefinition.ALL[it].orElse(null)?.type == Weapon.STAFF }.toList()
        for (staff in staves + listOf<Int?>(null)) {
            val equipment = arrayOfNulls<Int>(14)
            equipment[WEAPON] = staff
            for (slot in equipment.indices) {
                if (slot == WEAPON || slot == AMMUNITION || slot == SHIELD && staff?.let { equipDef(it).isTwoHanded } == true) continue
                equipment[slot] = BotGearSelector.ALL_GEAR.value[slot].asSequence().map { it.id }.distinct()
                    .filter { it in bot.itemTracker && equipDef(it).index == slot && equipDef(it).meetsAllRequirements(bot) }
                    .filter { equipDef(it).getBonus(MAGIC_ATTACK.index) < 0 }
                    .minByOrNull { equipDef(it).getBonus(MAGIC_ATTACK.index) }
            }
            if (equipment.filterNotNull().sumOf { equipDef(it).getBonus(MAGIC_ATTACK.index) } <= MAX_SPLASH_MAGIC_BONUS) return equipment
        }
        return null
    }

    private fun isValidTarget(npc: Npc): Boolean = npc.isAlive && npc.combat.isAttackable &&
        bot.combat.checkMultiCombat(npc) && (npc.def().name in ALWAYS_ALLOWED || npc.combatLevel < bot.combatLevel / 2)
}
