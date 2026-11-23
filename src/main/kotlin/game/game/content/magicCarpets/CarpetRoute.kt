package game.content.magicCarpets

import game.content.magicCarpets.CarpetStation.*
import io.luna.game.model.Position

/**
 * A flight between two carpet stations.
 *
 * @property from The station the carpet leaves.
 * @property to The station it lands at.
 * @property option What the player says to choose this flight.
 * @property path The tiles the carpet turns at after leaving [from], ending on [to]'s rug.
 * @author TheLining
 */
enum class CarpetRoute(val from: CarpetStation,
                       val to: CarpetStation,
                       val option: String,
                       val path: List<Position>) {
    // Listed in the order each merchant offers them.
    SHANTAY_TO_UZER(SHANTAY_PASS, UZER, "I want to travel to Uzer.", TO_UZER),
    SHANTAY_TO_BEDABIN(SHANTAY_PASS, BEDABIN_CAMP, "I want to travel to the Bedabin camp.", TO_BEDABIN),
    SHANTAY_TO_POLLNIVNEACH(SHANTAY_PASS, POLLNIVNEACH_NORTH, "I want to travel to Pollnivneach.", TO_POLLNIVNEACH),
    UZER_TO_SHANTAY(UZER, SHANTAY_PASS, "That sounds good, take me there.", back(TO_UZER, SHANTAY_PASS)),
    BEDABIN_TO_SHANTAY(BEDABIN_CAMP, SHANTAY_PASS, "Take me there.", back(TO_BEDABIN, SHANTAY_PASS)),
    POLLNIVNEACH_TO_SHANTAY(POLLNIVNEACH_NORTH, SHANTAY_PASS, "Take me to the Pass then.",
                            back(TO_POLLNIVNEACH, SHANTAY_PASS)),
    POLLNIVNEACH_TO_NARDAH(POLLNIVNEACH_SOUTH, NARDAH, "I want to travel to Nardah.", TO_NARDAH),
    POLLNIVNEACH_TO_MENAPHOS(POLLNIVNEACH_SOUTH, MENAPHOS, "I want to travel to Menaphos.", TO_MENAPHOS),
    POLLNIVNEACH_TO_SOPHANEM(POLLNIVNEACH_SOUTH, SOPHANEM, "I want to travel to Sophanem.", TO_SOPHANEM),
    NARDAH_TO_POLLNIVNEACH(NARDAH, POLLNIVNEACH_SOUTH, "Let's go then.", back(TO_NARDAH, POLLNIVNEACH_SOUTH)),
    MENAPHOS_TO_POLLNIVNEACH(MENAPHOS, POLLNIVNEACH_SOUTH, "Let's go then.", back(TO_MENAPHOS, POLLNIVNEACH_SOUTH)),
    SOPHANEM_TO_POLLNIVNEACH(SOPHANEM, POLLNIVNEACH_SOUTH, "Pollnivneach will do.",
                             back(TO_SOPHANEM, POLLNIVNEACH_SOUTH))
}

/**
 * The flights out of the two hubs, each ending on the far station's rug.
 */
private val TO_UZER = path(3308, 3107, 3469, 3107, 3469, 3113)
private val TO_BEDABIN = path(3305, 3107, 3299, 3107, 3285, 3088, 3285, 3073, 3268, 3073, 3263, 3068, 3246, 3068,
                              3246, 3057, 3179, 3057, 3179, 3047, 3180, 3045)
private val TO_POLLNIVNEACH = path(3308, 3096, 3308, 3066, 3311, 3057, 3319, 3042, 3332, 3033, 3341, 3020, 3350, 3009,
                                   3351, 3003, 3349, 3003)
private val TO_NARDAH = path(3350, 2936, 3362, 2936, 3380, 2928, 3392, 2920, 3397, 2916, 3401, 2916)
private val TO_SOPHANEM = path(3351, 2919, 3346, 2902, 3339, 2884, 3328, 2877, 3328, 2845, 3318, 2838, 3307, 2828,
                               3292, 2817, 3285, 2818, 3285, 2813)
private val TO_MENAPHOS = path(3351, 2919, 3346, 2902, 3339, 2884, 3328, 2877, 3328, 2845, 3318, 2838, 3307, 2828,
                               3292, 2817, 3252, 2817, 3245, 2813)

/**
 * Turns pairs of coordinates into tiles.
 */
private fun path(vararg xy: Int) = xy.toList().chunked(2) { (x, y) -> Position(x, y) }

/**
 * Flies [path] the other way, back to [hub]'s rug.
 */
private fun back(path: List<Position>, hub: CarpetStation) = path.dropLast(1).reversed() + hub.spot
