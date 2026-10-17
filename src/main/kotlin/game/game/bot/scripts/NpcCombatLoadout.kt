package game.bot.scripts

import api.predef.*

import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.magic.RuneRequirement
import game.skill.magic.Staff
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.Weapon

/**
 * An owned combat outfit and its consumables, selected before an NPC session starts.
 * Ordinary elemental spells use base runes and staff substitution; special spells require separate preparation.
 *
 * @property gear Equipment slots to pass to the existing gear locator.
 * @property spell Optional standard elemental autocast spell.
 * @author lare96
 */
internal data class NpcCombatLoadout(val gear: Array<Int?>, val spell: CombatSpellDefinition? = null) {
    /** Rune cost per cast after applying the selected staff's unlimited runes. */
    val runes: Map<Int, Int> get() = runeCosts(spell, gear[Equipment.WEAPON])

    companion object {
        /** Weapon types that consume their equipped stack instead of separate ammunition. */
        val thrown = setOf(Weapon.KNIFE, Weapon.DART, Weapon.JAVELIN, Weapon.THROWN_AXE)
        private val bows = setOf(Weapon.SHORTBOW, Weapon.LONGBOW, Weapon.CROSSBOW)

        /** Returns base-rune costs for supported spells, omitting runes represented by the actual staff. */
        fun runeCosts(spell: CombatSpellDefinition?, weapon: Int?): Map<Int, Int> {
            val represented = Staff.ID_TO_STAFF[weapon]?.represents.orEmpty()
            return spell?.required.orEmpty().filterIsInstance<RuneRequirement>()
                .filter { it.rune !in represented }.groupBy { it.rune.id }
                .mapValues { (_, requirements) -> requirements.sumOf { it.amount } }
        }

        /** Finds the strongest compatible, owned and equippable ammunition for a weapon. */
        fun ammunition(bot: Bot, weapon: Int): Int? = AmmoDefinition.ALL.values
            .filter { weapon in it.weapons }.sortedByDescending { it.strength }
            .flatMap { it.ammo.toList() }.firstOrNull {
                bot.itemTracker.contains(it) && api.predef.equipDef(it).meetsAllRequirements(bot)
            }

        /** Selects usable owned gear without creating supplies or silently changing combat purpose. */
        fun find(bot: Bot, purpose: BotGearPurpose, requested: CombatSpell? = null): NpcCombatLoadout? {
            if (purpose !in setOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED, BotGearPurpose.MAGIC)) return null
            fun spellFor(weapon: Int): CombatSpellDefinition? = CombatSpellDefinition.getSpells().values
                .filter { definition ->
                    definition.spellbook == bot.spellbook && definition.level <= bot.magic.level &&
                        definition.spell.name.substringAfterLast('_') in setOf("STRIKE", "BOLT", "BLAST", "WAVE") &&
                        definition.spell.name.substringBefore('_') in setOf("WIND", "WATER", "EARTH", "FIRE") &&
                        (requested == null || definition.spell == requested) &&
                        definition.required.all { it is RuneRequirement } &&
                        runeCosts(definition, weapon).all { (id, amount) -> bot.itemTracker.count(id) >= amount }
                }.maxByOrNull { it.maxHit }
            val selected = BotGearSelector.find(bot, setOf(purpose)) { item ->
                if (item.index != Equipment.WEAPON) false else {
                    val type = WeaponDefinition.ALL.get(item.id).orElse(null)?.type
                    when (purpose) {
                        BotGearPurpose.RANGED -> if (type in thrown) item.id !in AmmoDefinition.NO_AMMO_WEAPONS
                            else type !in bows || ammunition(bot, item.id) == null
                        BotGearPurpose.MAGIC -> type != Weapon.STAFF || spellFor(item.id) == null
                        else -> type in bows || type in thrown
                    }
                }
            }
            val weapon = selected.equipment[Equipment.WEAPON]
            if (purpose != BotGearPurpose.MELEE && weapon == null) return null
            if (purpose == BotGearPurpose.RANGED && weapon != null &&
                WeaponDefinition.ALL.get(weapon).orElse(null)?.type in bows) {
                selected.equipment[Equipment.AMMUNITION] = ammunition(bot, weapon) ?: return null
            }
            val spell = if (purpose == BotGearPurpose.MAGIC) spellFor(weapon!!) ?: return null else null
            // Compatible ammo is already selected, so buildLocator cannot enter its legacy free-ammo fallback.
            return NpcCombatLoadout(selected.buildLocator().equipment, spell)
        }
    }
}
