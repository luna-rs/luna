package api.bot.action

import api.combat.death.PlayerDeathItems
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import game.skill.magic.RuneRequirement
import game.skill.magic.Staff
import io.luna.Luna
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.Weapon
import java.math.BigInteger

/**
 * Selects PK weapons and the strongest native damage spell before checking owned consumables.
 * Shop candidates must be accessible and stocked; each weapon leaves room in the risk allowance for supplies.
 * Ordinary NPC training retains its existing owned-loadout policy.
 *
 * @author lare96
 */
internal class PkLoadoutSelection(private val bot: Bot) {
    /**
     * Disciplines permanently assigned to this account, independent of its current supply shortages.
     */
    fun purposes(): List<BotGearPurpose> {
        val styles = bot.preferences.pk?.styles() ?: return emptyList()
        if (bot.preferences.isSkiller) return emptyList()
        return listOf(BotGearPurpose.MELEE to Skill.ATTACK, BotGearPurpose.RANGED to Skill.RANGED,
            BotGearPurpose.MAGIC to Skill.MAGIC).filter { styles.uses(it.second) }.map { it.first }
    }

    /**
     * Counts physical stock, including bank notes, without trusting a cached ownership total.
     */
    fun owned(id: Int): Int = (bot.inventory.filterNotNull() + bot.equipment.filterNotNull() + bot.bank.filterNotNull())
        .filter { it.unnotedItemDef.id() == id }.sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /**
     * A desired item can already be owned or currently stocked by an accessible GP shop.
     */
    fun available(id: Int): Boolean = owned(id) > 0 || bot.world.shops.values().any {
        it.currency == Currency.COINS && id in it.itemIds && it.botAccess.apply(bot) &&
            it.items.computeAmountForId(id) > 0 && it.computeBuyValue(id, 1).isPresent
    }

    /**
     * Supports native elemental and ancient damage spells, excluding target-specific spells and unlock items.
     */
    fun strongestSpell(): CombatSpellDefinition? = CombatSpellDefinition.getSpells().values.filter {
        val name = it.spell.name
        val supported = name.substringBefore('_') in setOf("WIND", "WATER", "EARTH", "FIRE", "SMOKE", "SHADOW", "BLOOD", "ICE")
        it.spellbook == bot.spellbook && it.level <= bot.skill(Skill.MAGIC).level && it.maxHit > 0 && supported &&
            it.required.all { requirement -> requirement is RuneRequirement } &&
            BotCombatActionHandler.mayGainExperience(bot, Skill.MAGIC)
    }.maxWithOrNull(compareBy<CombatSpellDefinition> { it.maxHit }.thenBy { it.level })

    /**
     * Resolves compatible, equippable weapons and ammunition. Desired selection may buy missing items;
     * owned and carried selection never invent stock or downgrade the strongest spell to fit leftover runes.
     */
    fun select(purpose: BotGearPurpose, desired: Boolean = false, carriedOnly: Boolean = false): CombatLoadout? {
        if (purpose !in purposes()) return null
        val spell = if (purpose == BotGearPurpose.MAGIC) strongestSpell() ?: return null else null
        // One synchronous stock snapshot avoids rescanning every bank slot for every weapon candidate.
        val carried = bot.inventory.filterNotNull() + bot.equipment.filterNotNull()
        val stock = (carried + if (carriedOnly) emptyList() else bot.bank.filterNotNull())
            .groupBy { if (carriedOnly) it.id else it.unnotedItemDef.id() }
            .mapValues { (_, items) -> items.sumOf { it.amount.toLong() } }
        val shops = if (desired) bot.world.shops.values().filter {
            it.currency == Currency.COINS && it.botAccess.apply(bot)
        } else emptyList()
        val economy = bot.world.economy
        val prices = HashMap<Int, Long>()
        fun price(id: Int) = prices.getOrPut(id) { economy.getPrice(id).coerceAtLeast(0) }
        val risk = if (carriedOnly) null else PkRiskBudget.assess(bot,
            PlayerDeathItems.losesItems(bot, Luna.settings().game().betaMode()))
        val weaponAllowance = risk?.maximumLoss?.divide(BigInteger.valueOf((purposes().size * 2).toLong()))
        fun accessible(id: Int): Boolean = when {
            (stock[id] ?: 0) > 0 -> true
            desired -> shops.any { id in it.itemIds && it.items.computeAmountForId(id) > 0 && it.computeBuyValue(id, 1).isPresent }
            else -> false
        }
        fun affordable(id: Int): Boolean = weaponAllowance == null ||
            BigInteger.valueOf(price(id)) <= weaponAllowance
        fun ammunition(weapon: Int): Int? = AmmoDefinition.ALL.values.filter { weapon in it.weapons }
            .sortedByDescending { it.strength }.flatMap { it.ammo.sorted() }.firstOrNull {
                accessible(it) && EquipmentDefinition.ALL.get(it).map { definition -> definition.meetsAllRequirements(bot) }.orElse(false)
            }
        val candidates = WeaponDefinition.ALL.filter { definition ->
            val id = definition.id()
            val equipment = EquipmentDefinition.ALL.get(id).orElse(null) ?: return@filter false
            if (!accessible(id) || !affordable(id) || !equipment.meetsAllRequirements(bot)) return@filter false
            when (purpose) {
                BotGearPurpose.MAGIC -> definition.type == Weapon.STAFF &&
                    (bot.spellbook != Spellbook.ANCIENT || id in Staff.AUTOCAST_ANCIENTS)
                BotGearPurpose.RANGED -> definition.type in BOWS &&
                    (id in AmmoDefinition.NO_AMMO_WEAPONS || ammunition(id) != null) &&
                    BotCombatActionHandler.allowedStyles(bot, definition.typeDef.styles).isNotEmpty()
                else -> definition.type !in BOWS && definition.type !in CombatLoadout.thrown &&
                    definition.type !in setOf(Weapon.STAFF, Weapon.PICKAXE, Weapon.SICKLE, Weapon.UNARMED) &&
                    equipment.getBonus(EquipmentBonus.STRENGTH.index) > 0 &&
                    BotCombatActionHandler.allowedStyles(bot, definition.typeDef.styles).isNotEmpty()
            }
        }
        fun bonus(id: Int, bonus: EquipmentBonus) = EquipmentDefinition.ALL.retrieve(id).getBonus(bonus.index)
        val weapon = candidates.maxWithOrNull(compareBy<WeaponDefinition> {
            when (purpose) {
                BotGearPurpose.MELEE -> bonus(it.id(), EquipmentBonus.STRENGTH)
                BotGearPurpose.RANGED -> bonus(it.id(), EquipmentBonus.RANGED_ATTACK)
                else -> bonus(it.id(), EquipmentBonus.MAGIC_ATTACK)
            }
        }.thenBy { if (it.type == Weapon.SHORTBOW) 1 else 0 }
            .thenBy { -price(it.id()) })?.id() ?: return null
        val gear = BotGearSelector.find(bot, setOf(purpose)) {
            carriedOnly && CombatLoadout.carried(bot, it.id) == 0
        }.equipment
        gear[Equipment.WEAPON] = weapon
        gear[Equipment.AMMUNITION] = if (purpose == BotGearPurpose.RANGED && weapon !in AmmoDefinition.NO_AMMO_WEAPONS)
            ammunition(weapon) else null
        if (EquipmentDefinition.ALL.retrieve(weapon).isTwoHanded) gear[Equipment.SHIELD] = null
        val selected = CombatLoadout(gear, spell)
        if (!desired && selected.runes.any { (id, amount) ->
                (if (carriedOnly) bot.inventory.computeAmountForId(id) else owned(id)) < amount }) return null
        return selected
    }

    companion object {
        const val MIN_CASTS = 50
        const val MIN_AMMUNITION = 100
        private val BOWS = setOf(Weapon.SHORTBOW, Weapon.LONGBOW, Weapon.CROSSBOW)
    }
}
