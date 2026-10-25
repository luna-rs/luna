package game.skill.agility

import game.skill.Skills
import io.luna.game.model.Position

/**
 * Failure routes and current-hitpoint damage from Lost City's 377 agility scripts.
 * The source marks its success rates as provisional.
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
                      "...You lose your footing and fall into the lava.", Position(2999, 3960), 741),
    WILDERNESS_LOG(200, 250, 150, Position(2998, 10345),
                   "You slip and fall onto the spikes below.", Position(2998, 3945));

    /**
     * Uses the current Agility level, including temporary boosts and drains.
     */
    fun fails(level: Int, roll: Double): Boolean = roll >= Skills.successRate(low, high, minOf(level, 99))

    /**
     * Calculates damage from current hitpoints using the source's integer arithmetic.
     */
    fun damage(hitpoints: Int): Int = hitpoints * damagePerThousand / 1000 + 1
}
