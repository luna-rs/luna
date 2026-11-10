package api.bot.action.trading

import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Equipment
import java.util.Locale

/**
 * Resolves explicit metal equipment requests to ordinary, unnoted equipment commodities.
 *
 * The catalogue is built once on the first recognized request, then shared across human messages. Matching uses
 * both equipment slots and complete standard item names, so tools, ammunition, trimmed variants and unrelated
 * items containing a metal name never join a broad request. No bot ownership or world-player scan is performed.
 *
 * @author lare96
 */
internal class PlayerTradeEquipmentRequests(private val items: () -> Sequence<Entry> = {
    EquipmentDefinition.ALL.asSequence().mapNotNull { equipment ->
        ItemDefinition.ALL.get(equipment.id()).orElse(null)?.let {
            Entry(it.id(), it.name, equipment.index, it.isTradeable, it.isNoted)
        }
    }
}) {

    /**
     * Immutable cache metadata required for category membership, independent of a bot's inventory.
     *
     * @author lare96
     */
    internal data class Entry(val id: Int, val name: String, val slot: Int,
                              val tradeable: Boolean = true, val noted: Boolean = false)

    /**
     * Ready-made category maps shared by repeated messages without rebuilding a combined gear basket.
     *
     * @author lare96
     */
    private data class Categories(val armour: Map<Int, String>, val weapons: Map<Int, String>,
                                  val equipment: Map<Int, String>)

    /**
     * Shared category vocabulary and the complete ordinary item names accepted by each request.
     *
     * @author lare96
     */
    companion object {
        val live = PlayerTradeEquipmentRequests()

        private val REQUEST = Regex("^(bronze|iron|steel|black|white|mithril|mith|adamant|adamantite|addy|rune|dragon) " +
                "(equipment|gear|armour|armor|weapons?|armour upgrades|armor upgrades)[.!]*$")
        private val SPACES = Regex("\\s+")
        private val ARMOUR = setOf("med helm", "full helm", "chainbody", "platebody", "platelegs",
            "plateskirt", "sq shield", "kiteshield", "boots", "gloves")
        private val WEAPONS = setOf("dagger", "sword", "longsword", "scimitar", "mace", "battleaxe",
            "warhammer", "2h sword", "spear", "halberd", "claws")
        private val ARMOUR_SLOTS = setOf(Equipment.HEAD, Equipment.CHEST, Equipment.LEGS, Equipment.SHIELD,
            Equipment.BOOTS, Equipment.HANDS)
        private val METALS = setOf("bronze", "iron", "steel", "black", "white", "mithril", "adamant", "rune", "dragon")
    }

    private var catalogue: Map<String, Categories>? = null

    /**
     * Reads one explicit category without loading equipment metadata for ordinary or unknown item phrases.
     * Missing definitions are not cached, allowing initialization to finish before the first live request.
     */
    fun resolve(phrase: String): Map<Int, String>? {
        val match = REQUEST.matchEntire(phrase.lowercase(Locale.ROOT).trim().replace(SPACES, " ")) ?: return null
        val metal = when (val typed = match.groupValues[1]) {
            "mith" -> "mithril"
            "addy", "adamantite" -> "adamant"
            else -> typed
        }
        val category = match.groupValues[2]
        val weaponsOnly = category.startsWith("weapon")
        val armourOnly = category.startsWith("arm")
        val index = catalogue ?: buildCatalogue().also { if (it.isNotEmpty()) catalogue = it }
        val members = index[metal] ?: return emptyMap()
        return when {
            weaponsOnly -> members.weapons
            armourOnly -> members.armour
            else -> members.equipment
        }
    }

    /**
     * Indexes the immutable equipment catalogue once; standard-name membership bounds every category.
     */
    private fun buildCatalogue(): Map<String, Categories> {
        val result = HashMap<Pair<String, Boolean>, MutableMap<Int, String>>()
        for (entry in items()) {
            if (!entry.tradeable || entry.noted || entry.id < 0) continue
            val name = entry.name.lowercase(Locale.ROOT)
            val metal = name.substringBefore(' ')
            if (metal !in METALS) continue
            val suffix = name.substringAfter(' ', "")
            val weapon = entry.slot == Equipment.WEAPON && suffix in WEAPONS
            if (!weapon && !(entry.slot in ARMOUR_SLOTS && suffix in ARMOUR)) continue
            result.getOrPut(metal to weapon) { sortedMapOf() }[entry.id] = entry.name
        }
        return result.keys.map { it.first }.distinct().associateWith { metal ->
            val armour = result[metal to false].orEmpty()
            val weapons = result[metal to true].orEmpty()
            Categories(armour, weapons, armour + weapons)
        }
    }
}
