package game.content.makeover

import io.luna.game.model.mob.block.PlayerAppearance

/**
 * The hairdresser's three brochures: a man's haircut, a woman's haircut and a man's shave.
 *
 * @param interfaceId The brochure window.
 * @param part The appearance slot that the brochure changes.
 * @param gender The gender that the brochure is for.
 * @param fee What the brochure's service costs.
 * @param kits The identity kit behind each style button, in button order.
 * @param firstStyleButton The first style button.
 * @param firstColourButton The first of the [HAIR_COLOURS] colour buttons, which are in hair colour order.
 * @param confirmButton The confirm button.
 * @param thanks What the hairdresser says after the work is done.
 * @author TheLining
 */
enum class HairStyle(val interfaceId: Int,
                     val part: Int,
                     val gender: Int,
                     val fee: Int,
                     val kits: List<Int>,
                     val firstStyleButton: Int,
                     val firstColourButton: Int,
                     val confirmButton: Int,
                     val thanks: String) {

    MAN_HAIR(interfaceId = 2653,
             part = PlayerAppearance.HEAD,
             gender = PlayerAppearance.GENDER_MALE,
             fee = 1000,
             kits = (0..8).toList(),
             firstStyleButton = 2789,
             firstColourButton = 2777,
             confirmButton = 2753,
             thanks = "Hope you like the new do!"),

    WOMAN_HAIR(interfaceId = 2505,
               part = PlayerAppearance.HEAD,
               gender = PlayerAppearance.GENDER_FEMALE,
               fee = 2000,
               kits = (45..54).toList(),
               firstStyleButton = 2643,
               firstColourButton = 2631,
               confirmButton = 2606,
               thanks = "It really suits you!"),

    BEARD(interfaceId = 2007,
          part = PlayerAppearance.BEARD,
          gender = PlayerAppearance.GENDER_MALE,
          fee = 1000,
          kits = listOf(11, 10, 13, 15, 17, 12, 14, 16),
          firstStyleButton = 2148,
          firstColourButton = 2136,
          confirmButton = 2113,
          thanks = "Mmm... very distinguished!");

    companion object {

        /**
         * How many hair colours there are to pick from.
         */
        const val HAIR_COLOURS = 12
    }
}
