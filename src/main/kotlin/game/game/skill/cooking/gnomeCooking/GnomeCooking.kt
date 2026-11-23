package game.skill.cooking.gnomeCooking

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.mob.Player

/**
 * Gnome cooking as the 377 client has it: fillings go into a half baked dish or the cocktail shaker one at a time,
 * and "Inspect" lists what is inside. The player's current fillings are saved with them, one list per container.
 */
object GnomeCooking {
    const val GIANNE_DOUGH = 2171
    const val COCKTAIL_SHAKER = 2025
    const val COCKTAIL_GLASS = 2026
    const val EMPTY_BUCKET = 1925

    const val SPICY_WORM = 2160
    const val CHEESE = 1985
    const val SEASONED_LEGS = 2158
    const val TOMATO = 1982
    const val EQUA_LEAVES = 2128
    const val ONION = 1957
    const val CABBAGE = 1965
    const val DWELLBERRIES = 2126
    const val CHOCOLATE_BAR = 1973
    const val POTATO = 1942
    const val GNOME_SPICE = 2169
    const val TOADS_LEGS = 2152
    const val KING_WORM = 2162

    const val CHOCOLATE_DUST = 1975
    const val POT_OF_CREAM = 2130
    const val LEMON_CHUNKS = 2104
    const val LEMON_SLICES = 2106
    const val ORANGE_CHUNKS = 2110
    const val ORANGE_SLICES = 2112
    const val PINEAPPLE_CHUNKS = 2116
    const val LIME_CHUNKS = 2122
    const val LIME_SLICES = 2124

    const val VODKA = 2015
    const val WHISKY = 2017
    const val GIN = 2019
    const val BRANDY = 2021
    const val LEMON = 2102
    const val ORANGE = 2108
    const val PINEAPPLE = 2114
    const val LIME = 2120
    const val BUCKET_OF_MILK = 1927

    /**
     * The most fillings one dish or the shaker holds.
     */
    const val CAPACITY = 15

    /**
     * The experience for the first bake, for a finishing step of a dish, and for finishing a cocktail.
     */
    const val HALF_BAKE_EXP = 3.0
    const val DISH_EXP = 30.0
    const val COCKTAIL_EXP = 60.0

    /**
     * The fillings a half baked dish takes, in the order "Inspect" lists them, with their singular and plural
     * wording.
     */
    val FILLINGS = linkedMapOf(
        KING_WORM to ("King worm" to "King worms"),
        SPICY_WORM to ("Spicy worm" to "Spicy worms"),
        TOADS_LEGS to ("pair of Toad's legs" to "pairs of Toad's legs"),
        SEASONED_LEGS to ("pair of Seasoned toad's legs" to "pairs of Seasoned toad's legs"),
        CHEESE to ("piece of Cheese" to "pieces of Cheese"),
        TOMATO to ("Tomato" to "Tomatoes"),
        EQUA_LEAVES to ("sprig of Equa Leaves" to "sprigs of Equa Leaves"),
        ONION to ("Onion" to "Onions"),
        CABBAGE to ("Cabbage" to "Cabbages"),
        DWELLBERRIES to ("Dwellberry" to "Dwellberries"),
        POTATO to ("Potato" to "Potatoes"),
        GNOME_SPICE to ("dash of Gnome Spice" to "dashes of Gnome Spice"),
        CHOCOLATE_BAR to ("Chocolate bar" to "Chocolate bars"))

    /**
     * What goes into the cocktail shaker, in the order "Inspect" lists it.
     */
    val MIXERS = linkedMapOf(
        VODKA to ("measure of Vodka" to "measures of Vodka"),
        GIN to ("measure of Gin" to "measures of Gin"),
        BRANDY to ("measure of Brandy" to "measures of Brandy"),
        WHISKY to ("measure of Whisky" to "measures of Whisky"),
        LEMON to ("Lemon" to "Lemons"),
        ORANGE to ("Orange" to "Oranges"),
        LIME to ("Lime" to "Limes"),
        PINEAPPLE to ("Pineapple" to "Pineapples"),
        EQUA_LEAVES to ("sprig of Equa Leaves" to "sprigs of Equa Leaves"),
        BUCKET_OF_MILK to ("bucket of Milk" to "buckets of Milk"),
        DWELLBERRIES to ("Dwellberry" to "Dwellberries"))

    /**
     * What can be added to a baked dish or a poured cocktail to finish it.
     */
    val TOPPINGS = setOf(CHOCOLATE_DUST, EQUA_LEAVES, POT_OF_CREAM, GNOME_SPICE, LEMON_CHUNKS, LEMON_SLICES,
        ORANGE_CHUNKS, ORANGE_SLICES, PINEAPPLE_CHUNKS, LIME_CHUNKS, LIME_SLICES, CHOCOLATE_BAR)

    /**
     * A filling that bakes or pours into [product]. The levels are the Knowledge Base's.
     */
    class Recipe(val product: Int, val lvl: Int, val filling: Map<Int, Int>, val finished: Boolean = false)

    val RECIPES = mapOf(
        GnomeDish.BATTA to listOf(
            Recipe(2261, 25, mapOf(EQUA_LEAVES to 4)),
            Recipe(2255, 26, mapOf(SEASONED_LEGS to 1, CHEESE to 1), finished = true),
            Recipe(2251, 27, mapOf(SPICY_WORM to 1, CHEESE to 1)),
            Recipe(2279, 28, mapOf(TOMATO to 2, ONION to 1, CABBAGE to 1, DWELLBERRIES to 1, CHEESE to 1)),
            Recipe(2257, 29, mapOf(CHEESE to 1, TOMATO to 1))),
        GnomeDish.CRUNCHIES to listOf(
            Recipe(2215, 10, mapOf(TOADS_LEGS to 2, GNOME_SPICE to 1)),
            Recipe(2211, 12, mapOf(GNOME_SPICE to 3, EQUA_LEAVES to 2)),
            Recipe(2203, 14, mapOf(KING_WORM to 2, EQUA_LEAVES to 1, GNOME_SPICE to 1)),
            Recipe(2207, 16, mapOf(CHOCOLATE_BAR to 2, GNOME_SPICE to 1))),
        GnomeDish.BOWL to listOf(
            Recipe(2189, 30, mapOf(KING_WORM to 4, ONION to 2, GNOME_SPICE to 1)),
            Recipe(2193, 35, mapOf(ONION to 2, POTATO to 2, GNOME_SPICE to 1)),
            Recipe(2187, 40, mapOf(TOADS_LEGS to 4, CHEESE to 2, EQUA_LEAVES to 2, GNOME_SPICE to 2,
                DWELLBERRIES to 1), finished = true),
            Recipe(2179, 42, mapOf(CHOCOLATE_BAR to 4, EQUA_LEAVES to 1))))

    val COCKTAILS = listOf(
        Recipe(2082, 6, mapOf(PINEAPPLE to 1, ORANGE to 1, LEMON to 1)),
        Recipe(2042, 8, mapOf(PINEAPPLE to 2, ORANGE to 1, LEMON to 1)),
        Recipe(2050, 18, mapOf(VODKA to 2, GIN to 1, PINEAPPLE to 1, ORANGE to 1, LIME to 1, LEMON to 1)),
        Recipe(2076, 20, mapOf(VODKA to 1, LIME to 3)),
        Recipe(2086, 32, mapOf(VODKA to 1, GIN to 1, DWELLBERRIES to 1)),
        Recipe(2066, 33, mapOf(WHISKY to 1, EQUA_LEAVES to 1, BUCKET_OF_MILK to 1)),
        Recipe(2056, 37, mapOf(VODKA to 1, GIN to 1, BRANDY to 1, LEMON to 2, ORANGE to 1)))

    const val ODD_COCKTAIL = 2094
    private const val FRUITY = 2096
    private const val CREAMY = 2098
    private const val SLICED = 2100

    /**
     * An unfinished dish or cocktail: the toppings it accepts and what each makes of it. Anything else spoils it
     * into [spoilt].
     */
    class Unfinished(val dish: GnomeDish?, val spoilt: Int, val next: Map<Int, Int>)

    private fun dish(dish: GnomeDish, vararg next: Pair<Int, Int>) = Unfinished(dish, dish.spoilt, mapOf(*next))

    private fun drink(spoilt: Int, vararg next: Pair<Int, Int>) = Unfinished(null, spoilt, mapOf(*next))

    val UNFINISHED = mapOf(
        2211 to dish(GnomeDish.CRUNCHIES, GNOME_SPICE to 2213),
        2215 to dish(GnomeDish.CRUNCHIES, EQUA_LEAVES to 2217),
        2203 to dish(GnomeDish.CRUNCHIES, GNOME_SPICE to 2205),
        2207 to dish(GnomeDish.CRUNCHIES, CHOCOLATE_DUST to 2209),

        2179 to dish(GnomeDish.BOWL, POT_OF_CREAM to 2181),
        2181 to dish(GnomeDish.BOWL, POT_OF_CREAM to 2183),
        2183 to dish(GnomeDish.BOWL, CHOCOLATE_DUST to 2185),
        2189 to dish(GnomeDish.BOWL, EQUA_LEAVES to 2191),
        2193 to dish(GnomeDish.BOWL, EQUA_LEAVES to 2195),

        2251 to dish(GnomeDish.BATTA, EQUA_LEAVES to 2253),
        2257 to dish(GnomeDish.BATTA, EQUA_LEAVES to 2259),
        2279 to dish(GnomeDish.BATTA, EQUA_LEAVES to 2281),
        // A fruit batta takes its three kinds of fruit in any order, then the spice.
        2261 to dish(GnomeDish.BATTA, LIME_CHUNKS to 2263, ORANGE_CHUNKS to 2265, PINEAPPLE_CHUNKS to 2267),
        2263 to dish(GnomeDish.BATTA, ORANGE_CHUNKS to 2269, PINEAPPLE_CHUNKS to 2271),
        2265 to dish(GnomeDish.BATTA, PINEAPPLE_CHUNKS to 2273, LIME_CHUNKS to 2269),
        2267 to dish(GnomeDish.BATTA, LIME_CHUNKS to 2271, ORANGE_CHUNKS to 2273),
        2269 to dish(GnomeDish.BATTA, PINEAPPLE_CHUNKS to 2275),
        2271 to dish(GnomeDish.BATTA, ORANGE_CHUNKS to 2275),
        2273 to dish(GnomeDish.BATTA, LIME_CHUNKS to 2275),
        2275 to dish(GnomeDish.BATTA, GNOME_SPICE to 2277),

        2042 to drink(FRUITY, PINEAPPLE_CHUNKS to 2044),
        2044 to drink(FRUITY, LIME_CHUNKS to 2046),
        2046 to drink(FRUITY, ORANGE_SLICES to 2048),
        2056 to drink(FRUITY, ORANGE_CHUNKS to 2058),
        2058 to drink(FRUITY, LEMON_CHUNKS to 2060),
        2060 to drink(FRUITY, LIME_SLICES to 2062),
        2062 to drink(SLICED, EQUA_LEAVES to 2064),
        2066 to drink(CREAMY, CHOCOLATE_BAR to 2068),
        // The next step for these two is the oven.
        2068 to drink(CREAMY),
        2070 to drink(CREAMY, POT_OF_CREAM to 2072),
        2072 to drink(CREAMY, CHOCOLATE_DUST to 2074),
        2086 to drink(FRUITY, PINEAPPLE_CHUNKS to 2088),
        2088 to drink(FRUITY, POT_OF_CREAM to 2090),
        2090 to drink(CREAMY),
        2082 to drink(FRUITY, LEMON_SLICES to 2084),
        2076 to drink(FRUITY, EQUA_LEAVES to 2078),
        2078 to drink(FRUITY, LIME_SLICES to 2080),
        2050 to drink(FRUITY, PINEAPPLE_CHUNKS to 2052),
        2052 to drink(FRUITY, LIME_SLICES to 2054))

    /**
     * Drinks that are warmed in an oven, and what they become. Only the drunk dragon is finished by it.
     */
    val WARMED = mapOf(2068 to 2070, 2090 to 2092)

    /**
     * Dishes and cocktails that need nothing more.
     */
    val FINISHED = setOf(2213, 2217, 2205, 2209, 2185, 2191, 2195, 2253, 2259, 2281, 2277,
        2048, 2064, 2074, 2092, 2084, 2080, 2054)

    /**
     * What a wrong topping makes of the unfinished cocktail [drink]: a slice or cream marks the spoilt drink.
     */
    fun spoiltDrink(drink: Unfinished, topping: Int) = when (topping) {
        LEMON_SLICES, ORANGE_SLICES, LIME_SLICES -> SLICED
        POT_OF_CREAM -> CREAMY
        else -> drink.spoilt
    }

    private var Player.battaFilling by Attr.string().persist("gnome_batta_filling")
    private var Player.crunchiesFilling by Attr.string().persist("gnome_crunchies_filling")
    private var Player.bowlFilling by Attr.string().persist("gnome_bowl_filling")
    private var Player.shakerFilling by Attr.string().persist("gnome_shaker_filling")

    /**
     * The fillings in the player's half baked [dish], or in their cocktail shaker when [dish] is `null`.
     */
    fun filling(plr: Player, dish: GnomeDish?): List<Int> {
        val saved = when (dish) {
            GnomeDish.BATTA -> plr.battaFilling
            GnomeDish.CRUNCHIES -> plr.crunchiesFilling
            GnomeDish.BOWL -> plr.bowlFilling
            null -> plr.shakerFilling
        }
        return saved.split(',').mapNotNull { it.toIntOrNull() }
    }

    /**
     * Replaces the fillings in the player's half baked [dish], or in their shaker when [dish] is `null`.
     */
    fun setFilling(plr: Player, dish: GnomeDish?, filling: List<Int>) {
        val saved = filling.joinToString(",")
        when (dish) {
            GnomeDish.BATTA -> plr.battaFilling = saved
            GnomeDish.CRUNCHIES -> plr.crunchiesFilling = saved
            GnomeDish.BOWL -> plr.bowlFilling = saved
            null -> plr.shakerFilling = saved
        }
    }

    /**
     * The recipe among [recipes] that [filling] matches exactly, or `null` if it makes nothing.
     */
    fun match(recipes: List<Recipe>, filling: List<Int>): Recipe? {
        val counts = filling.groupingBy { it }.eachCount()
        return recipes.firstOrNull { it.filling == counts }
    }

    /**
     * Lists [filling] in the order and wording of [names], as "2 King worms, 1 piece of Cheese".
     */
    fun describe(filling: List<Int>, names: Map<Int, Pair<String, String>>): String {
        val counts = filling.groupingBy { it }.eachCount()
        return names.entries.mapNotNull { (id, name) ->
            counts[id]?.let { "$it ${if (it == 1) name.first else name.second}" }
        }.joinToString(", ")
    }
}
