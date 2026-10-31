package engine.bot.gear

import api.predef.*
import com.google.common.cache.CacheBuilder
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Equipment.AMMUNITION
import io.luna.game.model.item.Equipment.WEAPON
import io.luna.game.model.mob.bot.Bot
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Stable equipment tastes shared by shopping and the equipment selector.
 *
 * Ordinary metal progression remains desirable to every bot. Black and white equipment, decorated armour,
 * costumes and unusual combat sets can additionally become personal favourites. Family membership is derived
 * from the account name rather than the current login, so a bot keeps its tastes without a save migration.
 * Sociability increases the performance a bot will sacrifice for appearance; intelligence keeps that sacrifice small.
 *
 * @author lare96
 */
object BotEquipmentPreferences {

    private val combatPurposes = setOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC, BotGearPurpose.PKING)
    private val ordinaryMetals = setOf("BRONZE", "IRON", "STEEL", "MITHRIL", "ADAMANT", "RUNE", "DRAGON")
    private val ordinarySets = BotGearSet.entries.filter { set ->
        ordinaryMetals.any { set.name == "${it}_FULL_LEGS" || set.name == "${it}_FULL_SKIRT" }
    }
    private val ordinaryItems = (ordinarySets.flatMap { it.equipment } +
        WeaponBotGear.entries.filter { it.name.removeSuffix("_WEAPONS") in ordinaryMetals }.flatMap { it.ids } +
        ShieldBotGear.entries.filter { it.name in ordinaryMetals }.flatMap { it.ids } +
        GlovesBotGear.entries.filter { it.name in ordinaryMetals }.flatMap { it.ids }).toSet()
    private val groups: List<BotGearType> = HeadBotGear.entries + AmuletBotGear.entries + BootsBotGear.entries +
        CapeBotGear.entries + GlovesBotGear.entries + RingBotGear.entries + ShieldBotGear.entries + WeaponBotGear.entries
    private val functionalWeapons = WeaponBotGear.entries.filter {
        it !in setOf(WeaponBotGear.FUN_WEAPONS, WeaponBotGear.DECORATIVE_MELEE_WEAPONS)
    }.flatMapTo(hashSetOf()) { it.ids }
    private val blackAndWhite = (BotGearSet.BLACK_FULL_LEGS.equipment + BotGearSet.BLACK_FULL_SKIRT.equipment +
        BotGearSet.WHITE_FULL_LEGS.equipment + BotGearSet.WHITE_FULL_SKIRT.equipment +
        WeaponBotGear.BLACK_WEAPONS.ids + WeaponBotGear.WHITE_WEAPONS.ids +
        ShieldBotGear.BLACK.ids + ShieldBotGear.WHITE.ids + GlovesBotGear.BLACK.ids).toSet()
    private val cosmeticItems = run {
        val costumeSets = BotGearSet.entries.filter { BotGearPurpose.SHOW_OFF in it.purposes }
            .flatMap { it.equipment }
        val costumeGroups = groups.filter { group ->
            BotGearPurpose.SHOW_OFF in group.purposes() && group.purposes().none { it in combatPurposes }
        }.flatMap { it.ids() }
        ((costumeSets + costumeGroups + CapeBotGear.REGULAR.ids + CapeBotGear.FREMENNIK_CLOAK.ids).toSet() -
            ordinaryItems - functionalWeapons - RingBotGear.entries.flatMap { it.ids() }.toSet() -
            setOf(552, 4250, 4021, 6208, 6707) - AmuletBotGear.GAMES.ids) + blackAndWhite
    }
    private val families: Map<String, Set<Int>> = run {
        val result = sortedMapOf<String, MutableSet<Int>>()
        for (set in BotGearSet.entries) {
            if (set in ordinarySets || set.name.contains("HYBRID") || set.name.contains("TRIBRID") ||
                set.name.startsWith("F2P_") || set.name.contains("SWITCHER")) continue
            val ids = set.equipment.filter { it !in ordinaryItems && (it !in functionalWeapons || it in blackAndWhite ||
                set.name in setOf("DHAROKS", "VERACS", "TORAGS", "GUTHANS", "AHRIMS", "KARILS")) }
            if (ids.isNotEmpty()) result.getOrPut(familyName(set)) { linkedSetOf() }.addAll(ids)
        }
        for (group in groups) {
            val groupName = (group as Enum<*>).name
            val ids = group.ids().filter { it in cosmeticItems }
            val key = when (groupName) {
                "BLACK", "BLACK_WEAPONS", "BLACK_BOOTS" -> "BLACK"
                "WHITE", "WHITE_WEAPONS" -> "WHITE"
                "GOD_KITESHIELDS" -> "GOD_RUNE"
                else -> "FASHION_$groupName"
            }
            if (ids.isNotEmpty()) result.getOrPut(key) { linkedSetOf() }.addAll(ids)
            val functional = group.ids().filter { it !in ordinaryItems && it !in cosmeticItems }
            if (functional.isNotEmpty()) result.getOrPut("EQUIPMENT_$groupName") { linkedSetOf() }.addAll(functional)
        }
        result.mapValues { it.value.toSet() }
    }
    private val declaredPurposes: Map<Int, Set<BotGearPurpose>> = run {
        val result = hashMapOf<Int, MutableSet<BotGearPurpose>>()
        for (set in BotGearSet.entries) {
            // A mixed outfit's purposes do not describe each individual switch weapon or armour piece.
            if (mixedLayout(set)) continue
            for (id in set.equipment) result.getOrPut(id) { hashSetOf() }.addAll(setPurposes(set))
        }
        for (group in groups) {
            for (id in group.ids()) result.getOrPut(id) { hashSetOf() }.addAll(group.purposes())
        }
        for (id in blackAndWhite) result.getOrPut(id) { hashSetOf() }.add(BotGearPurpose.SHOW_OFF)
        result
    }
    private val favouriteItems = CacheBuilder.newBuilder().maximumSize(20_000).build<String, Set<Int>>()

    /**
     * Returns semantic fashion items, including black and white equipment but excluding ordinary functional staffs.
     */
    fun cosmeticIds(): Set<Int> = cosmeticItems

    /**
     * Identifies fashion items independently of the broad historical wanted-item tags.
     */
    fun isCosmetic(id: Int): Boolean = unnoted(id) in cosmeticItems

    /**
     * Keeps ordinary progression universally liked while recognising personal favourites and general cosmetic appeal.
     */
    fun likes(bot: Bot, id: Int): Boolean {
        val normalized = unnoted(id)
        return normalized in ordinaryItems || normalized in cosmeticItems || prefers(bot, normalized)
    }

    /**
     * Checks the bot's stable family tastes, including every member of a selected family.
     */
    fun prefers(bot: Bot, id: Int): Boolean {
        val normalized = unnoted(id)
        val name = bot.username.orEmpty().lowercase(Locale.ROOT).replace('_', ' ').trim()
        return normalized in favouriteItems.get(name) {
            favouriteFamilies(name).flatMapTo(hashSetOf()) { families.getValue(it) }
        }
    }

    /**
     * Chooses a small stable collection of complete gear families, with both fashion and combat tastes represented.
     */
    internal fun favouriteFamilies(username: String): Set<String> {
        val name = username.lowercase(Locale.ROOT).replace('_', ' ').trim()
        val ordered = families.keys.sortedBy { stableWeight(name, it) }
        val selected = linkedSetOf<String>()
        ordered.firstOrNull { key -> families.getValue(key).any { it in cosmeticItems } }?.let(selected::add)
        ordered.firstOrNull { key -> families.getValue(key).any { it !in cosmeticItems } }?.let(selected::add)
        selected.addAll(ordered.take(2 + Math.floorMod(name.hashCode(), 3)))
        return selected
    }

    /**
     * Combines usable progression with one personal fashion goal. Ordinary shopping can request progression alone.
     * Shopping applies its own cash reserve and quantity limits; this method never creates ownership targets.
     */
    fun purchaseCandidates(bot: Bot): List<Int> = (progressionCandidates(bot) + fashionCandidates(bot)).distinct()

    /**
     * Selects missing upgrades for one combat style, with at most one chosen item in each equipment slot.
     * Pieces must meet their own real requirements; a robe upgrade never requires an unrelated staff's Attack level.
     */
    fun progressionCandidates(bot: Bot): List<Int> {
        if (bot.preferences.isSkiller) return emptyList()
        val style = planningPurpose(bot)
        return progressionOutfit(bot).filter { candidate ->
            if (owns(bot, candidate.id)) return@filter false
            val owned = bot.itemTracker.elementSet().mapNotNull { BotGearSelector.ALL_GEAR_IDS.value[it] }
                .filter { it.index == candidate.index && supports(it, setOf(style)) &&
                    it.def.value.meetsAllRequirements(bot) }
            val existing = select(bot, owned, setOf(style))
            val tasteCanMatter = prefers(bot, candidate.id) &&
                favouriteLevelGap(bot.personality.intelligence, bot.personality.social) > 0
            // Equal ordinary variants are interchangeable stock, rather than another reason to visit the market.
            if (existing != null && !tasteCanMatter && candidate.calculateScore() <= existing.calculateScore()) {
                return@filter false
            }
            select(bot, owned + candidate, setOf(style))?.id == candidate.id
        }.map { it.id }
    }

    /**
     * Chooses one favourite fashion family to complete, rather than shopping for every colour of every favourite.
     * Owning a family member already satisfies that slot, including an owned noted copy.
     */
    fun fashionCandidates(bot: Bot): List<Int> {
        val name = bot.username.orEmpty()
        val tastes = favouriteFamilies(name).sortedBy { stableWeight(name, it) }
        for (family in tastes) {
            val pieces = families.getValue(family).filter { id -> id in cosmeticItems &&
                EquipmentDefinition.ALL.get(id).map { it.meetsAllRequirements(bot) }.orElse(false) }
                .mapNotNull { BotGearSelector.ALL_GEAR_IDS.value[it] }
            val missing = pieces.groupBy { it.index }.values.mapNotNull { choices ->
                if (choices.any { owns(bot, it.id) }) null
                else select(bot, choices, setOf(BotGearPurpose.SHOW_OFF))?.id
            }
            if (missing.isNotEmpty()) return missing
        }
        return emptyList()
    }

    /**
     * Resolves suitable ammunition for the planned ranged weapon. Callers choose an activity-sized quantity.
     */
    fun suitableAmmo(bot: Bot): Int? {
        if (planningPurpose(bot) != BotGearPurpose.RANGED || bot.preferences.isSkiller) return null
        val weapon = progressionOutfit(bot).firstOrNull { it.index == WEAPON }?.id ?: return null
        return AmmoDefinition.ALL.values.filter { weapon in it.weapons }
            .sortedByDescending { it.strength }.firstNotNullOfOrNull { definition ->
                definition.ammo.sorted().firstOrNull { id ->
                    EquipmentDefinition.ALL.get(id).map { it.meetsAllRequirements(bot) }.orElse(false)
                }
            }
    }

    private fun progressionOutfit(bot: Bot): List<BotGearItem> {
        val style = planningPurpose(bot)
        val ladder = when (style) {
            BotGearPurpose.MAGIC -> listOf(BotGearSet.WIZARD_BLUE, BotGearSet.MYSTIC_LIGHT,
                BotGearSet.MYSTIC_DARK, BotGearSet.AHRIMS)
            BotGearPurpose.RANGED -> listOf(BotGearSet.LEATHER, BotGearSet.STUDDED_LEATHER,
                BotGearSet.GREEN_DHIDE, BotGearSet.BLUE_DHIDE, BotGearSet.RED_DHIDE,
                BotGearSet.BLACK_DHIDE, BotGearSet.KARILS)
            else -> ordinarySets + BotGearSet.BLACK_FULL_LEGS + BotGearSet.BLACK_FULL_SKIRT
        }
        val weaponGroups = when (style) {
            BotGearPurpose.MAGIC -> WeaponBotGear.entries.filter { it.name in setOf("STAFF", "MAGIC_STAFF",
                "ELEMENTAL_STAFF", "BATTLESTAFF", "ELEMENTAL_BATTLESTAFF", "MYSTIC_STAFF", "ANCIENT_STAFF") }
            BotGearPurpose.RANGED -> WeaponBotGear.entries.filter { it.name in setOf("BASIC_BOWS", "OAK_BOWS",
                "WILLOW_BOWS", "MAPLE_BOWS", "YEW_BOWS", "MAGIC_BOWS", "KARILS_CROSSBOW") }
            else -> WeaponBotGear.entries.filter { it.name.removeSuffix("_WEAPONS") in ordinaryMetals ||
                it == WeaponBotGear.BLACK_WEAPONS }
        }
        val favouriteSets = BotGearSet.entries.filter { !mixedLayout(it) && style in setPurposes(it) &&
            it.equipment.any { id -> prefers(bot, id) } }
        val baselineIds = (ladder.flatMap { it.equipment } + weaponGroups.flatMap { it.ids }).toSet()
        val candidates = (baselineIds + favouriteSets.flatMap { it.equipment }).mapNotNull {
            BotGearSelector.ALL_GEAR_IDS.value[it]
        }.filter { item ->
            item.index != AMMUNITION && supports(item, setOf(style)) && item.def.value.meetsAllRequirements(bot) &&
                (owns(bot, item.id) || ItemDefinition.ALL.get(item.id).map { it.isTradeable }.orElse(false)) &&
                (style != BotGearPurpose.RANGED || item.index != WEAPON ||
                    item.id in AmmoDefinition.NO_AMMO_WEAPONS || AmmoDefinition.ALL.values.any { ammo ->
                        item.id in ammo.weapons && ammo.ammo.any { id ->
                            EquipmentDefinition.ALL.get(id).map { it.meetsAllRequirements(bot) }.orElse(false)
                        }
                    })
        }
        val result = arrayListOf<BotGearItem>()
        for (slot in candidates.groupBy { it.index }.values) {
            // A favourite may replace the normal ladder, but must not invent a missing high-value accessory need.
            val baseline = slot.filter { it.id in baselineIds }
            if (baseline.isNotEmpty()) {
                val ordinary = select(bot, baseline, setOf(style))
                select(bot, slot, setOf(style), ordinary?.id)?.let(result::add)
            }
        }
        return result
    }

    /**
     * Uses original gear declarations rather than the generic melee fallback when a known item has a specific role.
     */
    internal fun supports(item: BotGearItem, purposes: Set<BotGearPurpose>): Boolean {
        val declared = declaredPurposes[item.id] ?: item.purposes
        val required = purposes - BotGearPurpose.PKING
        if (required.isNotEmpty()) return declared.containsAll(required)
        return BotGearPurpose.PKING in declared || declared.any { it in combatPurposes }
    }

    /**
     * Resolves a slot using usefulness first, then a bounded preference for favourite families.
     */
    internal fun select(bot: Bot, candidates: Collection<BotGearItem>, purposes: Set<BotGearPurpose>,
                        current: Int? = null): BotGearItem? {
        if (candidates.isEmpty()) return null
        val best = candidates.maxBy { it.calculateScore() }
        val cosmeticLayout = BotGearPurpose.SHOW_OFF in purposes && purposes.none { it in combatPurposes }
        val tolerance = favouriteLevelGap(bot.personality.intelligence, bot.personality.social)
        val favourites = candidates.filter { prefers(bot, it.id) && (cosmeticLayout || tolerance > 0 &&
            it.def.value.highestRequirement >= best.def.value.highestRequirement - tolerance) }
        return favourites.ifEmpty { candidates }.maxWithOrNull(compareBy<BotGearItem> { it.calculateScore() }
            .thenBy { if (it.id == current) 1 else 0 }
            .thenBy { stableWeight(bot.username.orEmpty(), it.id.toString()) })
    }

    /**
     * Prevents a favourite low-tier item from taking the place of a substantially stronger usable upgrade.
     */
    internal fun favouriteLevelGap(intelligence: Double, social: Double): Int =
        (social.coerceIn(0.0, 1.0) * (10 + 25 * (1 - intelligence.coerceIn(0.0, 1.0)))).roundToInt()

    /**
     * Chooses the account's current strongest combat style without using unrelated default ownership preferences.
     */
    fun planningPurpose(bot: Bot): BotGearPurpose {
        val melee = maxOf(bot.skill(SKILL_ATTACK).staticLevel, bot.skill(SKILL_STRENGTH).staticLevel)
        val ranged = bot.skill(SKILL_RANGED).staticLevel
        val magic = bot.skill(SKILL_MAGIC).staticLevel
        return when {
            magic > melee && magic >= ranged -> BotGearPurpose.MAGIC
            ranged > melee -> BotGearPurpose.RANGED
            else -> BotGearPurpose.MELEE
        }
    }

    private fun owns(bot: Bot, id: Int): Boolean {
        if (bot.itemTracker.contains(id)) return true
        val note = ItemDefinition.ALL.get(id).map { it.notedId.orElse(-1) }.orElse(-1)
        return note >= 0 && bot.itemTracker.contains(note)
    }

    private fun familyName(set: BotGearSet): String = when {
        set.name.startsWith("RUNE_SARADOMIN") || set.name.startsWith("RUNE_GUTHIX") ||
            set.name.startsWith("RUNE_ZAMORAK") -> "GOD_RUNE"
        set.name.startsWith("BLACK_FULL") -> "BLACK"
        set.name.startsWith("WHITE_FULL") -> "WHITE"
        set.name.startsWith("PIRATE_") -> "PIRATE"
        else -> set.name.removeSuffix("_LEGS").removeSuffix("_SKIRT")
    }

    private fun setPurposes(set: BotGearSet): Set<BotGearPurpose> {
        val metalFashion = set.name.startsWith("BLACK_") && !set.name.contains("DHIDE") && !set.name.contains("HYBRID") ||
            set.name.startsWith("WHITE_") || set.name.startsWith("ADAMANT_") || set.name.startsWith("RUNE_") ||
            set.name.startsWith("GILDED_RUNE")
        return if (metalFashion) set.purposes + BotGearPurpose.MELEE + BotGearPurpose.PKING else set.purposes
    }

    private fun mixedLayout(set: BotGearSet): Boolean = set.name.contains("HYBRID") ||
        set.name.contains("TRIBRID") || set.name.startsWith("F2P_") || set.name.contains("SWITCHER")

    private fun unnoted(id: Int): Int = ItemDefinition.ALL.get(id).map { it.unnotedId.orElse(id) }.orElse(id)

    private fun stableWeight(username: String, key: String): Long {
        var value = -3750763034362895579L
        for (character in "$username:$key") value = (value xor character.code.toLong()) * 1099511628211L
        return value xor (value ushr 32)
    }
}
