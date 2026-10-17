package game.content.abyss

import io.luna.game.model.Position

/**
 * The rifts in the inner ring of the Abyss, which lead to the Runecrafting altars.
 *
 * @property id The rift object.
 * @property destination Where the rift puts players, beside the altar. `null` for rifts that don't open.
 * @author TheLining
 */
enum class AbyssRift(val id: Int, val destination: Position?) {
    AIR(7139, Position(2846, 4836)),
    MIND(7140, Position(2784, 4843)),
    WATER(7137, Position(2714, 4838)),
    EARTH(7130, Position(2660, 4843)),
    FIRE(7129, Position(2587, 4836)),
    BODY(7131, Position(2523, 4834)),
    COSMIC(7132, Position(2142, 4836)),
    CHAOS(7134, Position(2269, 4840)),
    NATURE(7133, Position(2400, 4844)),
    LAW(7135, Position(2464, 4835)),
    DEATH(7136, Position(2207, 4834)),
    BLOOD(7141, null),
    SOUL(7138, null);
}
