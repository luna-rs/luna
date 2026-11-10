package api.bot.action.trading

import api.attr.Attr
import game.skill.fishing.catchFish.Fish
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemTag
import io.luna.game.model.mob.bot.Bot
import java.util.Locale

/**
 * Gives merchant accounts stable advertised buying categories while sharing the existing trading engine.
 * General merchants remain the broad fallback. Item categories are cached by definition identity, so repeated
 * advertisement and direct-trade checks do not scan the item catalogue or rebuild category collections.
 *
 * @author lare96
 */
enum class BotMerchantSpecialty(val advertisement: String, private val category: Int,
                                val catalogue: List<Int>) {
    GENERAL("Buying anything cheap!", 0, listOf(314, 1511, 436, 526)),
    RUNES("Buying any runes!", 1, listOf(556, 555, 557, 554, 558, 559, 562, 560, 565, 563, 561, 564, 566)),
    EQUIPMENT("Buying any equipment!", 2, listOf(1287, 1329, 1123, 1073, 1161, 1357, 1271)),
    SKILLING("Buying all skilling supplies!", 4, listOf(314, 1779, 7936, 1511, 436, 2353, 5291, 317)),
    ORES("Buying all ores!", 8, listOf(436, 438, 440, 442, 444, 447, 449, 451, 453)),
    BARS("Buying all metal bars!", 16, listOf(2349, 2351, 2353, 2355, 2357, 2359, 2361, 2363)),
    SEEDS("Buying all seeds!", 32, listOf(5291, 5292, 5293, 5294, 5295, 5296, 5318, 5319)),
    LOGS("Buying all logs!", 64, listOf(1511, 1521, 1519, 1517, 1515, 1513)),
    RAW_FISH("Buying all raw fish!", 128, listOf(317, 321, 327, 335, 331, 359, 377, 371, 383, 389, 395));

    /**
     * Accepts tradeable stock within this advertised category, normalizing notes to the actual commodity.
     */
    fun accepts(id: Int): Boolean {
        val categories = categories(id)
        return categories >= 0 && (this == GENERAL || categories and category != 0)
    }

    companion object {
        private var Bot.specialty by Attr.nullableObj(BotMerchantSpecialty::class)
        private val spaces = Regex("\\s+")
        private val categories = arrayOfNulls<Classification>(ItemDefinition.SIZE)
        private val rawFish = Fish.entries.asSequence().map { it.id }
            .filter { it !in setOf(405, 407, 1061, 1059, 401) }.toSet() + setOf(389, 395)

        /**
         * The exact loaded sources used by one cached classification. Missing definitions are never cached.
         *
         * @author lare96
         */
        private data class Classification(val item: ItemDefinition, val wanted: WantedItemDefinition?,
                                          val equipment: EquipmentDefinition?, val categories: Int)

        /**
         * Captures assignment once per connected account; the canonical account name reproduces it after restart.
         */
        fun forBot(bot: Bot): BotMerchantSpecialty = bot.specialty ?: forAccount(bot.username.orEmpty()).also {
            bot.specialty = it
        }

        /**
         * Keeps forty percent of accounts general, with rune, equipment, broad supply and focused specialists.
         */
        fun forAccount(username: String): BotMerchantSpecialty {
            val canonical = username.lowercase(Locale.ROOT).replace('_', ' ').trim().replace(spaces, " ")
            if (canonical.isEmpty()) return GENERAL
            return when (Math.floorMod(canonical.hashCode(), 100)) {
                in 0..39 -> GENERAL
                in 40..54 -> RUNES
                in 55..69 -> EQUIPMENT
                in 70..84 -> SKILLING
                in 85..87 -> ORES
                in 88..90 -> BARS
                in 91..93 -> SEEDS
                in 94..96 -> LOGS
                else -> RAW_FISH
            }
        }

        /**
         * Applies the same account category to prepared catalogues, player advertisements, and direct trades.
         */
        fun accepts(bot: Bot, id: Int): Boolean = forBot(bot).accepts(id)

        /**
         * Resolves only the offered item. Source identity checks also handle definition reloads and local fixtures.
         */
        private fun categories(id: Int): Int {
            if (id !in categories.indices) return -1
            val supplied = ItemDefinition.ALL.get(id).orElse(null) ?: return -1
            val base = supplied.unnotedId.orElse(id)
            if (base <= 0 || base == 995 || base !in categories.indices) return -1
            val item = ItemDefinition.ALL.get(base).orElse(null) ?: return -1
            if (!item.isTradeable) return -1
            val wanted = WantedItemDefinition.DEFAULT.get(base).orElse(null)
            val equipment = EquipmentDefinition.ALL.get(base).orElse(null)
            val previous = categories[base]
            if (previous?.item === item && previous.wanted === wanted && previous.equipment === equipment) {
                return previous.categories
            }
            val name = item.name.lowercase(Locale.ROOT)
            val tags = wanted?.tags().orEmpty()
            var mask = 0
            if (name.endsWith(" rune")) mask = mask or RUNES.category
            if (equipment != null || WantedItemTag.WEAPON in tags || WantedItemTag.COMBAT_EQUIPMENT in tags ||
                WantedItemTag.COSMETIC_EQUIPMENT in tags) mask = mask or EQUIPMENT.category
            if (WantedItemTag.SKILLING_CONSUMABLE in tags || WantedItemTag.TOOL in tags) mask = mask or SKILLING.category
            if (name.endsWith(" ore") || name == "coal") mask = mask or ORES.category or SKILLING.category
            if (name.endsWith(" bar")) mask = mask or BARS.category or SKILLING.category
            if (name.endsWith(" seed")) mask = mask or SEEDS.category or SKILLING.category
            if (name == "logs" || name.endsWith(" logs")) mask = mask or LOGS.category or SKILLING.category
            if (base in rawFish) mask = mask or RAW_FISH.category or SKILLING.category
            categories[base] = Classification(item, wanted, equipment, mask)
            return mask
        }
    }
}
