package api.bot.action

import api.predef.*

import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.magic.RuneRequirement
import game.skill.magic.EquipmentRequirement
import game.skill.magic.Staff
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.Weapon

/**
 * An owned combat outfit and its consumables, shared by NPC preparation and carried PK switches.
 * Elemental and ancient damage spells retain their native rune and staff requirements. Explicit Magic Dart
 * selection also retains its native Slayer staff requirement; other special spells require separate preparation.
 *
 * @property gear Equipment slots to pass to the existing gear locator.
 * @property spell Optional combat autocast spell or explicitly requested Magic Dart.
 * @author lare96
 */
internal data class CombatLoadout(val gear: Array<Int?>, val spell: CombatSpellDefinition? = null) {
    /**
     * Rune cost per cast after applying the selected staff's unlimited runes.
     */
    val runes: Map<Int, Int> get() = runeCosts(spell, gear[Equipment.WEAPON])

    companion object {
        /**
         * Weapon types that consume their equipped stack instead of separate ammunition.
         */
        val thrown = setOf(Weapon.KNIFE, Weapon.DART, Weapon.JAVELIN, Weapon.THROWN_AXE)
        private val bows = setOf(Weapon.SHORTBOW, Weapon.LONGBOW, Weapon.CROSSBOW)

        /**
         * Returns base-rune costs for supported spells, omitting runes represented by the actual staff.
         */
        fun runeCosts(spell: CombatSpellDefinition?, weapon: Int?): Map<Int, Int> {
            val represented = Staff.ID_TO_STAFF[weapon]?.represents.orEmpty()
            return spell?.required.orEmpty().filterIsInstance<RuneRequirement>()
                .filter { it.rune !in represented }.groupBy { it.rune.id }
                .mapValues { (_, requirements) -> requirements.sumOf { it.amount } }
        }

        /**
         * Finds the strongest compatible, owned and equippable ammunition for a weapon.
         */
        fun ammunition(bot: Bot, weapon: Int, carriedOnly: Boolean = false, required: Int? = null): Int? = AmmoDefinition.ALL.values
            .filter { weapon in it.weapons }.sortedByDescending { it.strength }
            .flatMap { it.ammo.toList() }.firstOrNull {
                (required == null || it == required) &&
                    (if (carriedOnly) carried(bot, it) > 0 else bot.itemTracker.contains(it)) &&
                    api.predef.equipDef(it).meetsAllRequirements(bot)
            }

        /**
         * Counts physical equipment and inventory stock, excluding all banked supplies.
         */
        fun carried(bot: Bot, id: Int): Int = bot.inventory.computeAmountForId(id) + bot.equipment.computeAmountForId(id)

        /**
         * Selects usable owned gear without creating supplies or silently changing combat purpose.
         * Carried selection bypasses the locator's bank-oriented fill and free-ammunition fallback.
         * PK planning may exclude expensive preferred weapons or lower the damage-spell tier when a kit exceeds risk.
         * Task constraints restrict selection before scoring, so required shields never replace a two-handed weapon
         * after it was chosen. Magic Dart is considered only when explicitly requested and with its native staff.
         * An explicit spellbook plans a switch at home without changing the bot's live spellbook during selection.
         */
        fun find(bot: Bot, purpose: BotGearPurpose, requested: CombatSpell? = null,
                 carriedOnly: Boolean = false, excludingWeapons: Set<Int> = emptySet(),
                 maximumSpellHit: Int = Int.MAX_VALUE, requiredEquipment: Map<Int, Int> = emptyMap(),
                 requiredAmmo: Int? = null, allowedWeapons: Set<Int> = emptySet(),
                 spellbook: Spellbook = bot.spellbook, minimumCasts: Int = 1): CombatLoadout? {
            if (minimumCasts < 1) return null
            if (purpose !in setOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED, BotGearPurpose.MAGIC)) return null
            if (bot.preferences.isSkiller) return null
            if (purpose == BotGearPurpose.MAGIC && !BotCombatActionHandler.mayGainExperience(bot, SKILL_MAGIC)) return null
            if (purpose == BotGearPurpose.MAGIC && !BotCombatActionHandler.canUseSpellbook(bot, spellbook)) return null
            if (requiredEquipment.any { (slot, id) ->
                    (if (carriedOnly) carried(bot, id) == 0 else !bot.itemTracker.contains(id)) ||
                        equipDef(id).index != slot || !equipDef(id).meetsAllRequirements(bot)
                }) return null
            fun spellFor(weapon: Int): CombatSpellDefinition? = CombatSpellDefinition.getSpells().values
                .filter { definition ->
                    definition.spellbook == spellbook && definition.level <= bot.magic.level && definition.maxHit <= maximumSpellHit &&
                        ((definition.spell.name.substringAfterLast('_') in setOf("STRIKE", "BOLT", "BLAST", "WAVE") &&
                            definition.spell.name.substringBefore('_') in setOf("WIND", "WATER", "EARTH", "FIRE")) ||
                            (definition.spellbook == Spellbook.ANCIENT && definition.maxHit > 0 &&
                                weapon in Staff.AUTOCAST_ANCIENTS) ||
                            (requested == CombatSpell.MAGIC_DART && definition.spell == requested)) &&
                        (requested == null || definition.spell == requested) &&
                        definition.required.all { it is RuneRequirement ||
                            (it is EquipmentRequirement && it.id == weapon &&
                                equipDef(it.id).meetsAllRequirements(bot)) } &&
                        runeCosts(definition, weapon).all { (id, amount) ->
                            (if (carriedOnly) bot.inventory.computeAmountForId(id) else bot.itemTracker.count(id)).toLong() >=
                                amount.toLong() * minimumCasts
                        }
                }.maxByOrNull { it.maxHit }
            val selected = BotGearSelector.find(bot, setOf(purpose)) reject@{ item ->
                if (carriedOnly && carried(bot, item.id) == 0) true
                else if (requiredEquipment[item.index]?.let { it != item.id } == true) true
                else if (item.index != Equipment.WEAPON) false else {
                    if (item.id in excludingWeapons) return@reject true
                    if (allowedWeapons.isNotEmpty() && item.id !in allowedWeapons) return@reject true
                    if (Equipment.SHIELD in requiredEquipment && equipDef(item.id).isTwoHanded) return@reject true
                    val definition = WeaponDefinition.ALL.get(item.id).orElse(null) ?: return@reject true
                    if (purpose != BotGearPurpose.MAGIC &&
                        BotCombatActionHandler.allowedStyles(bot, definition.typeDef.styles).isEmpty()) return@reject true
                    val type = definition.type
                    when (purpose) {
                        BotGearPurpose.RANGED -> if (type in thrown) requiredAmmo != null || item.id !in AmmoDefinition.NO_AMMO_WEAPONS
                            else type !in bows || ammunition(bot, item.id, carriedOnly, requiredAmmo) == null
                        BotGearPurpose.MAGIC -> type != Weapon.STAFF || spellFor(item.id) == null
                        else -> type in bows || type in thrown
                    }
                }
            }
            requiredEquipment.forEach { (slot, id) -> selected.equipment[slot] = id }
            val weapon = selected.equipment[Equipment.WEAPON]
            if ((allowedWeapons.isNotEmpty() || Equipment.SHIELD in requiredEquipment) && weapon == null) return null
            if (purpose != BotGearPurpose.MELEE && weapon == null) return null
            if (purpose == BotGearPurpose.MELEE && weapon == null &&
                BotCombatActionHandler.allowedStyles(bot, WeaponDefinition.getUnarmed().typeDef.styles).isEmpty()) return null
            if (purpose == BotGearPurpose.RANGED && weapon != null &&
                WeaponDefinition.ALL.get(weapon).orElse(null)?.type in bows) {
                selected.equipment[Equipment.AMMUNITION] = ammunition(bot, weapon, carriedOnly, requiredAmmo) ?: return null
            }
            val spell = if (purpose == BotGearPurpose.MAGIC) spellFor(weapon!!) ?: return null else null
            // Compatible ammo is already selected, so buildLocator cannot enter its legacy free-ammo fallback.
            if (carriedOnly && weapon != null && equipDef(weapon).isTwoHanded) selected.equipment[Equipment.SHIELD] = null
            return CombatLoadout(if (carriedOnly) selected.equipment else selected.buildLocator().equipment, spell)
        }
    }
}
