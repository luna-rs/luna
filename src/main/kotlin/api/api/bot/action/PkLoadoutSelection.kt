package api.bot.action

import api.combat.death.PlayerDeathItems
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import engine.bot.gear.BotEquipmentPreferences
import game.skill.magic.EquipmentRequirement
import game.skill.magic.RuneRequirement
import game.skill.magic.Staff
import game.combat.spells.IbanStaff
import io.luna.Luna
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.Weapon
import java.math.BigInteger

/**
 * Selects usable PK weapons and the strongest native damage spell supported by the available supplies.
 * Shop candidates must be accessible and stocked; complete departure plans retain the account's risk policy.
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
     * Resolves compatible, equippable weapons with one usable shot or fifty spell casts for departure.
     * Carried combat checks require only one remaining cast so an active outing can use its prepared supplies.
     * Owned and carried selection choose the strongest supplied spell rather than waiting for a missing rune tier.
     * Desired selection may buy missing essentials, preferring an owned functioning weapon over upgrades.
     * Shared wanted-item purchase planning can quote legitimate gear before funding exists; departure never uses this mode.
     */
    fun select(purpose: BotGearPurpose, desired: Boolean = false, carriedOnly: Boolean = false,
               spellbook: Spellbook = bot.spellbook, preferOwned: Boolean = false,
               preferCheapest: Boolean = false,
               minimumCasts: Int = if (carriedOnly) 1 else MIN_CASTS,
               purchasePlanning: Boolean = false): CombatLoadout? {
        if (minimumCasts < 1 || purpose !in purposes() || purchasePlanning && (!desired || carriedOnly)) return null
        // One synchronous stock snapshot avoids rescanning every bank slot for every weapon candidate.
        val carried = bot.inventory.filterNotNull() + bot.equipment.filterNotNull()
        val stock = (carried + if (carriedOnly) emptyList() else bot.bank.filterNotNull())
            .groupBy { if (carriedOnly) it.id else it.unnotedItemDef.id() }
            .mapValues { (_, items) -> items.sumOf { it.amount.toLong() } }
        val shops = if (desired && !purchasePlanning) bot.world.shops.values().filter {
            it.currency == Currency.COINS && it.botAccess.apply(bot)
        } else emptyList()
        val economy = bot.world.economy
        val prices = HashMap<Int, Long>()
        fun price(id: Int) = prices.getOrPut(id) { economy.getPrice(id).coerceAtLeast(0) }
        val risk = if (carriedOnly || purchasePlanning) null else PkRiskBudget.assess(bot,
            PlayerDeathItems.losesItems(bot, Luna.settings().game().betaMode()))
        val weaponAllowance = risk?.maximumLoss
        fun purchasable(id: Int): Boolean {
            val definition = ItemDefinition.ALL.get(id).orElse(null) ?: return false
            return definition.isTradeable && definition.unnotedId.isEmpty &&
                !definition.name.isNullOrBlank() && !definition.name.equals("null", ignoreCase = true)
        }
        fun accessible(id: Int): Boolean = when {
            (stock[id] ?: 0) > 0 -> true
            purchasePlanning -> purchasable(id)
            desired -> shops.any { id in it.itemIds && it.items.computeAmountForId(id) > 0 && it.computeBuyValue(id, 1).isPresent }
            else -> false
        }
        fun affordable(id: Int): Boolean = weaponAllowance == null || !ItemDefinition.ALL.retrieve(id).isTradeable ||
            BigInteger.valueOf(price(id)) <= weaponAllowance
        val spells = if (purpose == BotGearPurpose.MAGIC &&
            BotCombatActionHandler.canUseSpellbook(bot, spellbook) &&
            BotCombatActionHandler.mayGainExperience(bot, Skill.MAGIC)) {
            CombatSpellDefinition.getSpells().values.filter {
                it.spellbook == spellbook && it.level <= bot.skill(Skill.MAGIC).level && it.maxHit > 0 &&
                    BotCombatMagic.supportedDamage(bot, it.spell) &&
                    (it.spell != io.luna.game.model.mob.combat.CombatSpell.IBAN_BLAST ||
                        IbanStaff.remaining(bot) >= minimumCasts * 2)
            }.sortedWith(compareByDescending<CombatSpellDefinition> { it.maxHit }.thenByDescending { it.level })
        } else emptyList()
        val weaponSpells = HashMap<Int, CombatSpellDefinition?>()
        fun spellFor(weapon: Int): CombatSpellDefinition? = weaponSpells.getOrPut(weapon) {
            val compatible = spells.filter { spell -> spell.required.all {
                it is RuneRequirement || it is EquipmentRequirement && it.id == weapon
            } && (!purchasePlanning || CombatLoadout.runeCosts(spell, weapon).all { (id, amount) ->
                (stock[id] ?: 0) >= amount.toLong() * minimumCasts || purchasable(id)
            }) }
            val supplied = compatible.filter { spell -> CombatLoadout.runeCosts(spell, weapon).all { (id, amount) ->
                (stock[id] ?: 0) >= amount.toLong() * minimumCasts
            } }
            val choices = if (!desired || preferOwned && supplied.isNotEmpty()) supplied else compatible
            if (preferCheapest) choices.minWithOrNull(compareBy<CombatSpellDefinition> { spell ->
                CombatLoadout.runeCosts(spell, weapon).entries.fold(BigInteger.ZERO) { total, (id, amount) ->
                    total + BigInteger.valueOf(price(id)) * BigInteger.valueOf(amount.toLong() * minimumCasts)
                }
            }.thenByDescending { it.maxHit }.thenByDescending { it.level }) else choices.firstOrNull()
        }
        fun ammunition(weapon: Int): Int? = AmmoDefinition.ALL.values.filter { weapon in it.weapons }
            .sortedByDescending { it.strength }.flatMap { it.ammo.sorted() }
            .let { ids -> if (preferCheapest) ids.sortedBy(::price) else ids }
            .sortedByDescending { if (preferOwned && (stock[it] ?: 0) >= MIN_AMMUNITION) 1 else 0 }.firstOrNull {
                accessible(it) && (desired || (stock[it] ?: 0) >= if (carriedOnly) 1 else MIN_AMMUNITION) &&
                    EquipmentDefinition.ALL.get(it).map { definition -> definition.meetsAllRequirements(bot) }.orElse(false)
            }
        val candidates = WeaponDefinition.ALL.filter { definition ->
            val id = definition.id()
            val equipment = EquipmentDefinition.ALL.get(id).orElse(null) ?: return@filter false
            if (!accessible(id) || !affordable(id) || BotEquipmentPreferences.isCosmetic(id) ||
                !equipment.meetsAllRequirements(bot)) return@filter false
            when (purpose) {
                BotGearPurpose.MAGIC -> definition.type == Weapon.STAFF &&
                    (spellbook != Spellbook.ANCIENT || id in Staff.AUTOCAST_ANCIENTS) && spellFor(id) != null
                BotGearPurpose.RANGED -> definition.type in BOWS + CombatLoadout.thrown &&
                    (id in AmmoDefinition.NO_AMMO_WEAPONS || ammunition(id) != null) &&
                    BotCombatActionHandler.allowedStyles(bot, definition.typeDef.styles).isNotEmpty()
                else -> definition.type !in BOWS && definition.type !in CombatLoadout.thrown &&
                    definition.type != Weapon.UNARMED &&
                    BotCombatActionHandler.allowedStyles(bot, definition.typeDef.styles).any { style ->
                        style.exp.none { it == Skill.RANGED || it == Skill.MAGIC }
                    }
            }
        }
        fun bonus(id: Int, bonus: EquipmentBonus) = EquipmentDefinition.ALL.retrieve(id).getBonus(bonus.index)
        fun essentialCost(weapon: Int): BigInteger {
            val supplies = linkedMapOf(weapon to 1)
            if (purpose == BotGearPurpose.RANGED && weapon !in AmmoDefinition.NO_AMMO_WEAPONS) {
                ammunition(weapon)?.let { supplies[it] = 1 }
            }
            if (purpose == BotGearPurpose.MAGIC) CombatLoadout.runeCosts(spellFor(weapon), weapon).forEach { (id, amount) ->
                supplies[id] = amount * minimumCasts
            }
            return supplies.entries.fold(BigInteger.ZERO) { total, (id, amount) ->
                if (!ItemDefinition.ALL.retrieve(id).isTradeable) total else
                    total + BigInteger.valueOf(price(id)) * BigInteger.valueOf(amount.toLong())
            }
        }
        val weapon = candidates.maxWithOrNull(compareBy<WeaponDefinition> {
            if (preferOwned && (stock[it.id()] ?: 0) > 0) 1 else 0
        }.thenBy {
            if (preferCheapest) essentialCost(it.id()).negate() else BigInteger.ZERO
        }.thenBy {
            if (purpose == BotGearPurpose.MAGIC) spellFor(it.id())?.maxHit ?: 0 else 0
        }.thenBy {
            when (purpose) {
                BotGearPurpose.MELEE -> bonus(it.id(), EquipmentBonus.STRENGTH)
                BotGearPurpose.RANGED -> bonus(it.id(), EquipmentBonus.RANGED_ATTACK)
                else -> bonus(it.id(), EquipmentBonus.MAGIC_ATTACK)
            }
        }.thenBy { if (it.type == Weapon.SHORTBOW) 1 else 0 }
            .thenBy { -price(it.id()) })?.id() ?: return null
        val gear = BotGearSelector.find(bot, setOf(purpose, BotGearPurpose.PKING)) {
            BotEquipmentPreferences.isCosmetic(it.id) || carriedOnly && CombatLoadout.carried(bot, it.id) == 0
        }.equipment
        gear[Equipment.WEAPON] = weapon
        gear[Equipment.AMMUNITION] = if (purpose == BotGearPurpose.RANGED && weapon !in AmmoDefinition.NO_AMMO_WEAPONS)
            ammunition(weapon) else null
        if (EquipmentDefinition.ALL.retrieve(weapon).isTwoHanded) gear[Equipment.SHIELD] = null
        val selected = CombatLoadout(gear, if (purpose == BotGearPurpose.MAGIC) spellFor(weapon) else null)
        if (!desired && selected.runes.any { (id, amount) ->
                (if (carriedOnly) bot.inventory.computeAmountForId(id) else owned(id)).toLong() <
                    amount.toLong() * minimumCasts }) return null
        return selected
    }

    companion object {
        const val MIN_CASTS = 50
        const val MIN_AMMUNITION = 1
        private val BOWS = setOf(Weapon.SHORTBOW, Weapon.LONGBOW, Weapon.CROSSBOW)
    }
}
