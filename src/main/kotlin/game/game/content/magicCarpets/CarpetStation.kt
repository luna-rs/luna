package game.content.magicCarpets

import io.luna.game.model.Position

/**
 * A magic carpet station in the Kharidian desert.
 *
 * @property merchant Where the station's rug merchant stands.
 * @property face The merchant players see, whose head shows in the dialogue.
 * @property spot The tile of the station's rug that riders sit on, take off from and land on.
 * @property exit The tile next to the rug that riders step off onto.
 * @property offer What the merchant says about where the carpets go from here, a chat box each.
 * @author TheLining
 */
enum class CarpetStation(val merchant: Position,
                         val face: Int,
                         val spot: Position,
                         val exit: Position,
                         val offer: List<String>) {
    SHANTAY_PASS(merchant = Position(3311, 3109),
                 face = 2291,
                 spot = Position(3308, 3110),
                 exit = Position(3309, 3110),
                 offer = listOf("From here you can travel to Uzer, to the Bedabin camp or to the North of Pollnivneach.",
                                "The second major carpet hub station, to the south of Pollnivneach is in easy walking " +
                                "distance from there.")),
    BEDABIN_CAMP(merchant = Position(3182, 3043),
                 face = 2292,
                 spot = Position(3180, 3045),
                 exit = Position(3181, 3045),
                 offer = listOf("From here you can travel to the Shantay Pass.")),
    POLLNIVNEACH_NORTH(merchant = Position(3350, 3001),
                       face = 2294,
                       spot = Position(3349, 3003),
                       exit = Position(3349, 3002),
                       offer = listOf("From here you can travel to the Shantay Pass - the Southern gate of Al Kharid.")),
    POLLNIVNEACH_SOUTH(merchant = Position(3348, 2942),
                       face = 2293,
                       spot = Position(3351, 2942),
                       exit = Position(3350, 2942),
                       offer = listOf("From here you can travel to Nardah and the Menaphite cities of Sophanem and " +
                                      "Menaphos.")),
    UZER(merchant = Position(3467, 3110),
         face = 2296,
         spot = Position(3469, 3113),
         exit = Position(3468, 3113),
         offer = listOf("You can travel from here back to the Shantay Pass.")),
    NARDAH(merchant = Position(3401, 2918),
           face = 3020,
           spot = Position(3401, 2916),
           exit = Position(3401, 2917),
           offer = listOf("The carpets here will take you to the south of Pollnivneach.")),
    SOPHANEM(merchant = Position(3287, 2813),
             face = 2298,
             spot = Position(3285, 2813),
             exit = Position(3286, 2813),
             offer = listOf("The carpets here will take you to the south of Pollnivneach. Do you want to take a lift?")),
    MENAPHOS(merchant = Position(3243, 2813),
             face = 2300,
             spot = Position(3245, 2813),
             exit = Position(3244, 2813),
             offer = listOf("The carpets here will take you to the south of Pollnivneach."));

    /**
     * The routes that leave this station, in the order the merchant offers them.
     */
    val routes: List<CarpetRoute>
        get() = CarpetRoute.values().filter { it.from == this }

    companion object {

        /**
         * Returns the station whose merchant spawns at [spawn].
         */
        fun at(spawn: Position): CarpetStation? = values().firstOrNull { it.merchant == spawn }
    }
}
