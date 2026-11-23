package game.skill.cooking.prepareFood

/**
 * A one-step recipe: using [first] with [second] makes [product] at once, with no make-X menu.
 *
 * @param kept Ingredients that stay in the inventory.
 * @param returned Containers handed back with the product.
 */
class Mix(val first: Int,
          val second: Int,
          val product: Int,
          val message: String,
          val lvl: Int = 1,
          val exp: Double = 0.0,
          val knife: Boolean = false,
          val kept: Set<Int> = emptySet(),
          val returned: List<Int> = emptyList()) {

    companion object {
        const val KNIFE = 946
        const val BOWL = 1923
        const val GNOME_SPICE = 2169
        const val EQUA_LEAVES = 2128
        const val IRON_SPIT = 7225

        private const val TOMATO = 1982
        private const val ONION = 1957
        private const val UGTHANKI_MEAT = 1861
        private const val CHOPPED_TOMATO = 1869
        private const val CHOPPED_ONION = 1871
        private const val CHOPPED_UGTHANKI = 1873
        private const val ONION_AND_TOMATO = 1875
        private const val UGTHANKI_AND_ONION = 1877
        private const val UGTHANKI_AND_TOMATO = 1879
        const val KEBAB_MIX = 1881

        private const val COOKED_MEAT = 2142
        private const val MINCED_MEAT = 7070
        private const val CHOPPED_GARLIC = 7074
        private const val SPICY_SAUCE = 7072
        private const val CHILLI_CON_CARNE = 7062
        private const val SCRAMBLED_EGG = 7078
        private const val EGG_AND_TOMATO = 7064
        private const val FRIED_MUSHROOMS = 7082
        private const val FRIED_ONIONS = 7084
        private const val MUSHROOM_AND_ONION = 7066
        private const val TUNA = 361
        private const val COOKED_SWEETCORN = 5988
        private const val CHOPPED_TUNA = 7086
        private const val BOWL_OF_SWEETCORN = 7088
        private const val TUNA_AND_CORN = 7068

        private const val BAKED_POTATO = 6701
        private const val POTATO_WITH_BUTTER = 6703

        private const val TOADS_LEGS = 2152
        private const val EQUA_TOADS_LEGS = 2154
        private const val SPICY_TOADS_LEGS = 2156
        private const val SEASONED_LEGS = 2158
        private const val KING_WORM = 2162

        private const val SPIDER_CARCASS = 6291

        private val SPICE = setOf(GNOME_SPICE)

        /**
         * Every one-step recipe. Levels and experience follow the 2007 Knowledge Base cooking tables; its potato
         * totals count every step from scratch, so a mixing step is worth only what the earlier steps leave over.
         */
        val ALL = listOf(
            // Kebab mix, in any order.
            Mix(BOWL, TOMATO, CHOPPED_TOMATO, "You cut the tomato into the bowl.", knife = true),
            Mix(BOWL, ONION, CHOPPED_ONION, "You cut the onion into the bowl.", knife = true),
            Mix(BOWL, UGTHANKI_MEAT, CHOPPED_UGTHANKI, "You cut the ugthanki meat into the bowl.", knife = true),
            Mix(CHOPPED_TOMATO, ONION, ONION_AND_TOMATO, "You add the onion into the tomato mixture.",
                knife = true),
            Mix(CHOPPED_ONION, TOMATO, ONION_AND_TOMATO, "You add the tomato into the onion mixture.",
                knife = true),
            Mix(CHOPPED_TOMATO, UGTHANKI_MEAT, UGTHANKI_AND_TOMATO,
                "You add the ugthanki meat into the tomato mixture.", knife = true),
            Mix(CHOPPED_UGTHANKI, TOMATO, UGTHANKI_AND_TOMATO, "You add the tomato into the meat mixture.",
                knife = true),
            Mix(CHOPPED_ONION, UGTHANKI_MEAT, UGTHANKI_AND_ONION,
                "You add the ugthanki meat into the onion mixture.", knife = true),
            Mix(CHOPPED_UGTHANKI, ONION, UGTHANKI_AND_ONION, "You add the onion into the meat mixture.",
                knife = true),
            Mix(ONION_AND_TOMATO, UGTHANKI_MEAT, KEBAB_MIX,
                "You add the ugthanki meat into the tomato and onion mixture.", knife = true),
            Mix(UGTHANKI_AND_ONION, TOMATO, KEBAB_MIX, "You add the tomato into the meat and onion mixture.",
                knife = true),
            Mix(UGTHANKI_AND_TOMATO, ONION, KEBAB_MIX, "You add the onion into the meat and tomato mixture.",
                knife = true),

            // Potato toppings.
            Mix(BOWL, COOKED_MEAT, MINCED_MEAT, "You cut the meat into the bowl.", knife = true),
            Mix(BOWL, 1550, CHOPPED_GARLIC, "You cut the garlic into the bowl.", knife = true),
            Mix(CHOPPED_GARLIC, GNOME_SPICE, SPICY_SAUCE, "You mix the gnome spice into the garlic.",
                lvl = 9, exp = 25.0),
            Mix(SPICY_SAUCE, MINCED_MEAT, CHILLI_CON_CARNE, "You mix the meat into the spicy sauce.",
                lvl = 11, returned = listOf(BOWL)),
            Mix(SPICY_SAUCE, COOKED_MEAT, CHILLI_CON_CARNE, "You cut the meat into the spicy sauce.",
                lvl = 11, knife = true),
            Mix(BOWL, 1944, 7076, "You crack the egg into the bowl.", lvl = 13),
            Mix(SCRAMBLED_EGG, CHOPPED_TOMATO, EGG_AND_TOMATO, "You mix the tomato into the scrambled egg.",
                lvl = 23, returned = listOf(BOWL)),
            Mix(SCRAMBLED_EGG, TOMATO, EGG_AND_TOMATO, "You cut the tomato into the scrambled egg.",
                lvl = 23, knife = true),
            Mix(BOWL, 6004, 7080, "You cut the mushroom into the bowl.", knife = true),
            Mix(FRIED_MUSHROOMS, FRIED_ONIONS, MUSHROOM_AND_ONION,
                "You mix the fried onions and mushrooms.", lvl = 57, returned = listOf(BOWL)),
            Mix(BOWL, TUNA, CHOPPED_TUNA, "You cut the tuna into the bowl.", knife = true),
            Mix(BOWL, COOKED_SWEETCORN, BOWL_OF_SWEETCORN, "You put the sweetcorn into the bowl."),
            Mix(CHOPPED_TUNA, COOKED_SWEETCORN, TUNA_AND_CORN, "You mix the sweetcorn into the tuna.", lvl = 67),
            Mix(BOWL_OF_SWEETCORN, TUNA, TUNA_AND_CORN, "You cut the tuna into the sweetcorn.",
                lvl = 67, knife = true),
            Mix(CHOPPED_TUNA, BOWL_OF_SWEETCORN, TUNA_AND_CORN, "You mix the sweetcorn into the tuna.",
                lvl = 67, returned = listOf(BOWL)),

            // Baked potatoes.
            Mix(BAKED_POTATO, 6697, POTATO_WITH_BUTTER, "You add the butter to the potato.", lvl = 39, exp = 40.0),
            Mix(POTATO_WITH_BUTTER, 1985, 6705, "You add the cheese to the potato.", lvl = 47, exp = 40.0),
            Mix(POTATO_WITH_BUTTER, CHILLI_CON_CARNE, 7054, "You add the chilli con carne to the potato.",
                lvl = 41, exp = 15.0, returned = listOf(BOWL)),
            Mix(POTATO_WITH_BUTTER, EGG_AND_TOMATO, 7056, "You add the egg and tomato to the potato.",
                lvl = 51, exp = 50.0, returned = listOf(BOWL)),
            Mix(POTATO_WITH_BUTTER, MUSHROOM_AND_ONION, 7058, "You add the mushroom and onion to the potato.",
                lvl = 64, exp = 55.0, returned = listOf(BOWL)),
            Mix(POTATO_WITH_BUTTER, TUNA_AND_CORN, 7060, "You add the tuna and corn to the potato.",
                lvl = 68, exp = 10.0, returned = listOf(BOWL)),

            // Skewers.
            Mix(3226, IRON_SPIT, 7224, "You pierce the rabbit with the iron spit.", lvl = 16),
            Mix(2876, IRON_SPIT, 7230, "You pierce the chompy with the iron spit.", lvl = 30),
            Mix(SPIDER_CARCASS, 6305, 6293, "You pierce the spider carcass with the skewer stick.", lvl = 16),
            Mix(SPIDER_CARCASS, 52, 6295, "You pierce the spider carcass with the arrow shaft.", lvl = 16),

            // Gnome seasonings. The spice shaker is never used up.
            Mix(TOADS_LEGS, EQUA_LEAVES, EQUA_TOADS_LEGS, "You add the equa leaves to the toad legs."),
            Mix(TOADS_LEGS, GNOME_SPICE, SPICY_TOADS_LEGS, "You add the gnome spice to the toad legs.",
                kept = SPICE),
            Mix(SPICY_TOADS_LEGS, EQUA_LEAVES, SEASONED_LEGS, "You add the equa leaves to the spicy toad legs."),
            Mix(EQUA_TOADS_LEGS, GNOME_SPICE, SEASONED_LEGS, "You add the gnome spice to the equa toad legs.",
                kept = SPICE),
            Mix(KING_WORM, GNOME_SPICE, 2160, "You add the gnome spice to the king worm.", kept = SPICE))
    }
}
