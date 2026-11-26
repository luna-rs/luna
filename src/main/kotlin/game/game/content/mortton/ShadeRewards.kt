package game.content.mortton

import io.luna.game.model.item.Item
import kotlin.random.Random

/**
 * Loar and Phrin cremation rewards, and the bronze and steel chests they unlock.
 *
 * @author lare96
 */
object ShadeRewards {
    const val LOAR_REMAINS = 3396
    const val PHRIN_REMAINS = 3398
    const val FINE_CLOTH = 3470
    const val COINS = 995
    const val SWAMP_PASTE = 1941
    val remains = setOf(LOAR_REMAINS, PHRIN_REMAINS)
    val bronzeKeys = 3450..3454
    val steelKeys = 3455..3459
    val keys = bronzeKeys.first..steelKeys.last
    private const val FIRST_CHEST = 4111
    val chests = FIRST_CHEST..(FIRST_CHEST + keys.count() - 1)

    fun keyForChest(chest: Int): Int = keys.first + chest - FIRST_CHEST

    fun chestForKey(key: Int): Int = FIRST_CHEST + key - keys.first

    fun cremation(remains: Int, random: Random = Random.Default): Item {
        val roll = random.nextInt(1000)
        return when (remains) {
            LOAR_REMAINS -> if (roll < 790) Item(random.nextInt(3450, 3453))
                            else Item(COINS, random.nextInt(400, 501))
            PHRIN_REMAINS -> when {
                roll < 665 -> Item(random.nextInt(3455, 3458))
                roll < 790 -> Item(random.nextInt(3453, 3455))
                else -> Item(COINS, random.nextInt(600, 701))
            }
            else -> error("Unsupported shade remains: $remains")
        }
    }

    fun chest(key: Int, ringOfWealth: Boolean, random: Random = Random.Default): List<Item> {
        require(key in keys)
        val steel = key in steelKeys
        val trim = if (steel) key - steelKeys.first + 1 else key - bronzeKeys.first + 1
        val bonus = if (ringOfWealth) 2 else 0
        val treasure = if (steel) {
            when (random.nextInt(135) - trim - bonus) {
                in Int.MIN_VALUE..0 -> Item(FINE_CLOTH)
                in 1..9 -> Item(1601) // Diamond
                in 10..29 -> Item(2361, 3) // Adamantite bars
                in 30..55 -> Item(1159) // Mithril full helm
                in 56..79 -> Item(1369) // Mithril battleaxe
                in 80..105 -> Item(561, random.nextInt(10, 30)) // Nature runes
                else -> Item(1520, random.nextInt(5, 15)) // Noted willow logs
            }
        } else {
            when (random.nextInt(102) - trim - bonus) {
                in Int.MIN_VALUE..0 -> Item(1637) // Sapphire ring
                in 1..10 -> Item(2359, 3) // Mithril bars
                in 11..21 -> Item(2357, 3) // Gold bars
                in 22..41 -> Item(562, random.nextInt(10, 30)) // Chaos runes
                in 42..64 -> Item(1325) // Steel scimitar
                in 65..82 -> Item(1729) // Amulet of defence
                else -> Item(1353) // Steel axe
            }
        }
        val paste = if (steel) random.nextInt(15, 31) else random.nextInt(5, 11)
        return listOf(treasure, Item(SWAMP_PASTE, paste))
    }
}
