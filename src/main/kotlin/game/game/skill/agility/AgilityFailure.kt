package game.skill.agility

import game.skill.Skills
import io.luna.game.model.Position

/**
 * Failure routes and current-hitpoint damage from Lost City's 377 agility scripts.
 * The source marks its success rates as provisional. Players reach [approach] walking with [walkingAnimation], or
 * without one (-1) by jumping a stepping stone every two ticks.
 *
 * @author lare96
 */
enum class AgilityFailure(val low: Int,
                         val high: Int,
                         val damagePerThousand: Int,
                         val landing: Position,
                         val message: String,
                         val approach: Position? = null,
                         val walkingAnimation: Int = 762) {
    BARBARIAN_ROPE(200, 260, 150, Position(2551, 9948),
                   "You slip and fall to the pit below."),
    BARBARIAN_LOG(185, 260, 90, Position(2545, 3542),
                  "Something in the water bites you!", Position(2545, 3546)),
    BARBARIAN_LEDGE(210, 260, 150, Position(2534, 3545),
                    "You slip and fall onto the spikes below.", Position(2534, 3547, 1), 756),
    WILDERNESS_ROPE(200, 250, 150, Position(3004, 10357),
                    "You slip and fall to the pit below."),
    WILDERNESS_STONES(180, 250, 216, Position(3002, 3963),
                      "...You lose your footing and fall into the lava.", Position(2999, 3960), -1),
    WILDERNESS_LOG(200, 250, 150, Position(2998, 10345),
                   "You slip and fall onto the spikes below.", Position(2998, 3945)),

    // Ape Atoll: the course script plays each fall; the landing is where it ends and the message its first line. Jagex
    // gave no numbers: these chances stop failing at 75, and the damage is about a fifteenth of current hitpoints.
    APE_STEPPING_STONE(100, 306, 67, Position(2757, 2747), "You slip and fall in the river..."),
    APE_TROPICAL_TREE(120, 300, 67, Position(2757, 2747), "You lose your grip on the tree and fall into the water"),
    APE_MONKEY_BARS(120, 300, 67, Position(2757, 2747), "Whoops! You lose your grip and fall into the river."),
    APE_SKULL_SLOPE(120, 300, 67, Position(2747, 2741), "You miss a hand hold..."),
    APE_ROPE(120, 300, 67, Position(2752, 2741), "You lose your grip on the vine!"),
    APE_ZIP_VINE(120, 300, 67, Position(2758, 2734), "You jump up to seize the vine...");

    /**
     * Uses the current Agility level, including temporary boosts and drains.
     */
    fun fails(level: Int, roll: Double): Boolean = roll >= Skills.successRate(low, high, minOf(level, 99))

    /**
     * Calculates damage from current hitpoints using the source's integer arithmetic.
     */
    fun damage(hitpoints: Int): Int = hitpoints * damagePerThousand / 1000 + 1
}
