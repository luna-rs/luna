package game.bot.scripts.skills

import api.bot.Suspendable.naturalDelay
import api.bot.script.BotScriptData
import api.bot.script.ZonedBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearSelector
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import game.skill.magic.Staff
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Equipment.AMMUNITION
import io.luna.game.model.item.Equipment.EquipmentBonus.MAGIC_ATTACK
import io.luna.game.model.item.Equipment.SHIELD
import io.luna.game.model.item.Equipment.WEAPON
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.Weapon
import kotlin.time.Duration

class SplashBotScript(
    bot: Bot,
    duration: Duration
) : ZonedBotScript(bot, duration, SPLASH_ZONES) {

    private data class RuneSetup(
        val spell: CombatSpellDefinition,
        val staffId: Int
    )

    private var spell: CombatSpellDefinition? = null
    private var usingStaff: Int? = null
    private var splashEquipment: Array<Int?>? = null
    private var runes: List<Item> = emptyList()

    companion object {
        const val MIN_CAST_RUNES_REQUIRED = 2_000
        const val MAX_SPLASH_MAGIC_BONUS = -64

        val SPLASH_ZONES = mutableListOf(SubZone.LUMBRIDGE_COURT_YARD)
        val ALWAYS_ALLOWED = setOf("Rat")

        private val SPLASH_SPELLS = listOf(
            CombatSpell.WIND_STRIKE,
            CombatSpell.WATER_STRIKE,
            CombatSpell.EARTH_STRIKE,
            CombatSpell.FIRE_STRIKE,
            CombatSpell.WIND_BOLT,
            CombatSpell.WATER_BOLT,
            CombatSpell.EARTH_BOLT,
            CombatSpell.FIRE_BOLT,
            CombatSpell.WIND_BLAST,
            CombatSpell.WATER_BLAST,
            CombatSpell.EARTH_BLAST,
            CombatSpell.FIRE_BLAST,
            CombatSpell.WIND_WAVE,
            CombatSpell.WATER_WAVE,
            CombatSpell.EARTH_WAVE,
            CombatSpell.FIRE_WAVE
        )

        /**
         * Desired ownership target for every rune used by the normal splashing spell progression.
         *
         * The target is large enough to cast the most rune-expensive spell that uses that rune
         * [MIN_CAST_RUNES_REQUIRED] times.
         */
        private val runeTargets by lazy {
            val targets = HashMap<Rune, Int>()

            for (combatSpell in SPLASH_SPELLS) {
                for (requirement in combatSpell.def.required) {
                    if (requirement is RuneRequirement) {
                        val amount = requirement.amount * MIN_CAST_RUNES_REQUIRED
                        targets[requirement.rune] = maxOf(targets[requirement.rune] ?: 0, amount)
                    }
                }
            }

            targets
        }
    }

    override suspend fun onInit(resumed: Boolean): Boolean {
        if (resumed && spell != null && usingStaff != null && splashEquipment != null) {
            return true
        }

        val staves = ownedStaves()
        if (staves.isEmpty()) {
            bot.log("No usable staff available for splashing.")
            return false
        }

        val runeSetups = resolveRuneSetups(staves)
        if (runeSetups.isEmpty()) {
            addWantedRunes()
            bot.log("No splash spell has enough runes for $MIN_CAST_RUNES_REQUIRED casts.")
            return false
        }

        /*
         * Rune setups are ordered from highest spell to lowest. For equal spells, staves that save
         * more runes are preferred.
         *
         * We still check every staff because its magic attack bonus affects whether -64 can be reached.
         */
        for (setup in runeSetups) {
            val equipment = resolveSplashEquipment(setup.staffId) ?: continue

            spell = setup.spell
            usingStaff = setup.staffId
            splashEquipment = equipment
            runes = resolveRuneWithdrawList()
            return true
        }

        bot.log(
            "No owned equipment combination can reach $MAX_SPLASH_MAGIC_BONUS magic attack or lower."
        )
        return false
    }

    override suspend fun equipment(): BotGearLocator? {
        val equipment = splashEquipment ?: return null
        return BotGearLocator(bot, equipment.copyOf())
    }

    override suspend fun onEquipmentFailed(equipmentLocator: BotGearLocator) {
        bot.log("Could not equip the required splashing setup.")
        stop()
    }

    override suspend fun onBankOpen(initial: Boolean) {
        if (!initial) {
            return
        }

        val magicBonus = bot.equipment.getBonus(MAGIC_ATTACK)
        if (magicBonus > MAX_SPLASH_MAGIC_BONUS) {
            bot.log("Splash gear is invalid. Magic attack bonus is $magicBonus.")
            stop()
            return
        }

        if (!handler.banking.withdrawAll(runes)) {
            bot.log("Could not withdraw the required splashing runes.")
            stop()
            return
        }

        /*
         * Food is only an emergency supply. Running out of food should not prevent splashing from
         * starting, and withdrawAnyFood() will add wanted food when none is owned.
         */
        if (!handler.banking.withdrawAnyFood(rand(2, 7))) {
            bot.log("No food available for splashing. Continuing without emergency food.")
        }

        val availableSpell = resolveRuntimeSpell()
        if (availableSpell == null || !handler.combat.setAutocastSpell(availableSpell)) {
            bot.log("Could not configure a valid autocast spell after banking.")
            stop()
            return
        }

        spell = availableSpell
    }

    override suspend fun executeInZone(): Boolean {
        val magicBonus = bot.equipment.getBonus(MAGIC_ATTACK)
        if (magicBonus > MAX_SPLASH_MAGIC_BONUS) {
            bot.log("Magic attack bonus is too high to guarantee splashes. bonus=$magicBonus")
            stop()
            return true
        }

        /*
         * Only require enough runes for one cast here. The 2,000-cast requirement is exclusively
         * a startup requirement.
         *
         * This naturally causes Fire Blast -> Earth Blast -> Air Blast -> Fire Bolt -> etc. as
         * different rune supplies are exhausted.
         */
        val availableSpell = resolveRuntimeSpell()
        if (availableSpell == null) {
            bot.log("No usable splashing runes remain. Stopping script.")
            stop()
            return true
        }

        if (availableSpell != spell ||
            bot.combat.magic.autocastSpell != availableSpell ||
            !bot.combat.magic.isAutocasting
        ) {
            if (!handler.combat.setAutocastSpell(availableSpell)) {
                bot.log("Failed to switch autocast spell to ${availableSpell.spell}.")
                stop()
                return true
            }

            spell = availableSpell
        }

        /*
         * Autocasting handles subsequent attacks once combat has started. We only need to acquire
         * another target when combat ends.
         */
        val combatTarget = bot.combat.target
        if (bot.combat.inCombat() && combatTarget is Npc && combatTarget.isAlive) {
            return true
        }

        val area = activeZone?.area ?: return false

        val target = world.locator.findNpcs(area.centerPosition, area.tileRadius) {
            isValidTarget(it)
        }.asSequence()
            .filter { bot.isWithinDistance(it, 10) }
            .filter { world.collisionManager.raycast(bot.position, it.position) }
            .minByOrNull { bot.position.computeLongestDistance(it.position) }
            ?: return true

        bot.combat.attack(target)
        bot.naturalDelay()
        return true
    }

    override suspend fun finish() {
        val magic = bot.combat.magic
        magic.isAutocasting = false
        magic.autocastSpell = CombatSpellDefinition.NONE
        magic.selectedSpell = CombatSpellDefinition.NONE
        magic.refreshAutocast()
    }

    override fun snapshot(): BotScriptData? {
        return null
    }

    /**
     * Finds every spell/staff combination that has enough total owned runes to begin splashing.
     *
     * The returned list is ordered by spell level first and rune savings second.
     */
    private fun resolveRuneSetups(staves: List<Int>): List<RuneSetup> {
        val setups = ArrayList<RuneSetup>()

        for (combatSpell in SPLASH_SPELLS) {
            val definition = combatSpell.def

            if (definition.spellbook != bot.spellbook || bot.magic.level < definition.level) {
                continue
            }

            for (staffId in staves) {
                if (hasRunes(
                        definition,
                        staffId,
                        MIN_CAST_RUNES_REQUIRED,
                        inventoryOnly = false
                    )
                ) {
                    setups += RuneSetup(definition, staffId)
                }
            }
        }

        return setups.sortedWith(
            compareByDescending<RuneSetup> { it.spell.level }
                .thenByDescending { runeSavings(it.spell, it.staffId) }
        )
    }

    /**
     * Resolves the highest-level splash spell that can currently be cast from inventory.
     */
    private fun resolveRuntimeSpell(): CombatSpellDefinition? {
        val staffId = bot.equipment.weapon?.id ?: return null

        return SPLASH_SPELLS.asSequence()
            .map { it.def }
            .filter { it.spellbook == bot.spellbook }
            .filter { bot.magic.level >= it.level }
            .filter { hasRunes(it, staffId, casts = 1, inventoryOnly = true) }
            .maxByOrNull { it.level }
    }

    /**
     * Checks whether [spell] can be cast [casts] times using [staffId].
     *
     * Startup checks total bot ownership. Runtime checks only the inventory because banked runes
     * cannot be used while fighting.
     */
    private fun hasRunes(
        spell: CombatSpellDefinition,
        staffId: Int,
        casts: Int,
        inventoryOnly: Boolean
    ): Boolean {
        val represented = Staff.ID_TO_STAFF[staffId]?.represents.orEmpty()

        for (requirement in spell.required) {
            if (requirement !is RuneRequirement) {
                return false
            }

            if (requirement.rune in represented) {
                continue
            }

            val requiredAmount = requirement.amount * casts
            val availableAmount = if (inventoryOnly) {
                bot.inventory.computeAmountForId(requirement.rune.id)
            } else {
                bot.itemTracker.count(requirement.rune.id)
            }

            if (availableAmount < requiredAmount) {
                return false
            }
        }

        return true
    }

    /**
     * Returns how many runes per cast this staff replaces for [spell].
     *
     * This makes a fire staff preferable to an air staff for Fire Blast when both setups are valid,
     * because the fire staff replaces more runes per cast.
     */
    private fun runeSavings(spell: CombatSpellDefinition, staffId: Int): Int {
        val represented = Staff.ID_TO_STAFF[staffId]?.represents.orEmpty()

        return spell.required
            .filterIsInstance<RuneRequirement>()
            .filter { it.rune in represented }
            .sumOf { it.amount }
    }

    /**
     * Finds every owned, equippable weapon that Luna considers a staff.
     */
    private fun ownedStaves(): List<Int> {
        return BotGearSelector.ALL_GEAR.value[WEAPON]
            .asSequence()
            .map { it.id }
            .distinct()
            .filter { it in bot.itemTracker }
            .filter { equipDef(it).meetsAllRequirements(bot) }
            .filter { WeaponDefinition.ALL[it].orElse(null)?.type == Weapon.STAFF }
            .toList()
    }

    /**
     * Builds the most negative magic-attack equipment layout available for [staffId].
     *
     * Each slot independently chooses the owned item with the lowest magic attack bonus. Since
     * equipment slots contribute additively, this produces the lowest possible bonus from the
     * bot's currently owned and equippable gear.
     */
    private fun resolveSplashEquipment(staffId: Int): Array<Int?>? {
        val equipment = arrayOfNulls<Int>(14)
        val staffDefinition = equipDef(staffId)

        equipment[WEAPON] = staffId

        for (index in equipment.indices) {
            if (index == WEAPON ||
                index == AMMUNITION ||
                index == SHIELD && staffDefinition.isTwoHanded
            ) {
                continue
            }

            val selected = BotGearSelector.ALL_GEAR.value[index]
                .asSequence()
                .filter { it.id in bot.itemTracker }
                .filter {
                    val definition = equipDef(it.id)
                    definition.index == index && definition.meetsAllRequirements(bot)
                }
                .minByOrNull {
                    equipDef(it.id).getBonus(MAGIC_ATTACK.index)
                }
                ?: continue

            val magicBonus = equipDef(selected.id).getBonus(MAGIC_ATTACK.index)
            if (magicBonus < 0) {
                equipment[index] = selected.id
            }
        }

        val magicBonus = equipment
            .filterNotNull()
            .sumOf { equipDef(it).getBonus(MAGIC_ATTACK.index) }

        return if (magicBonus <= MAX_SPLASH_MAGIC_BONUS) {
            equipment
        } else {
            null
        }
    }

    /**
     * Builds the rune inventory used for this splashing session.
     *
     * Every owned rune used by the splash progression is brought, up to its desired 2,000-cast
     * target. This allows the script to downgrade naturally without returning to a bank.
     */
    private fun resolveRuneWithdrawList(): List<Item> {
        return runeTargets.mapNotNull { (rune, target) ->
            val owned = bot.itemTracker.count(rune.id)

            if (owned > 0) {
                Item(rune.id, minOf(owned, target))
            } else {
                null
            }
        }
    }

    /**
     * Adds the complete splashing rune supply to the temporary wanted-item list.
     */
    private fun addWantedRunes() {
        for ((rune, target) in runeTargets) {
            bot.preferences.requireItem(rune.id, target)
        }
    }

    private fun isValidTarget(npc: Npc): Boolean {
        if (!npc.isAlive ||
            !npc.combat.isAttackable ||
            !bot.combat.checkMultiCombat(npc)
        ) {
            return false
        }

        return npc.def().name in ALWAYS_ALLOWED ||
                npc.combatLevel < bot.combatLevel / 2
    }
}