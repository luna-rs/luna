package game.content.abyss

import io.luna.game.model.Position

/**
 * The 12 ways from the outer ring of the Abyss to the inner ring, named by where they lie. Each shows one
 * [AbyssObstacle], picked by the player's layout.
 *
 * @property id The passage object.
 * @property enter The tile in front of the passage, in the outer ring.
 * @property inner Where players come out in the inner ring.
 * @author TheLining
 */
enum class AbyssPassage(val id: Int, val enter: Position, val inner: Position) {
    SOUTH(7142, Position(3042, 4810), Position(3042, 4819)),
    SOUTH_SOUTH_WEST(7143, Position(3027, 4812), Position(3031, 4820)),
    WEST_SOUTH_WEST(7144, Position(3017, 4822), Position(3026, 4826)),
    WEST(7145, Position(3017, 4834), Position(3024, 4834)),
    WEST_NORTH_WEST(7146, Position(3020, 4843), Position(3027, 4840)),
    NORTH_NORTH_WEST(7147, Position(3029, 4851), Position(3032, 4844)),
    NORTH(7148, Position(3039, 4855), Position(3039, 4845)),
    NORTH_NORTH_EAST(7149, Position(3050, 4851), Position(3047, 4844)),
    EAST_NORTH_EAST(7150, Position(3060, 4840), Position(3052, 4838)),
    EAST(7151, Position(3062, 4831), Position(3054, 4831)),
    EAST_SOUTH_EAST(7152, Position(3059, 4822), Position(3051, 4825)),
    SOUTH_SOUTH_EAST(7153, Position(3050, 4812), Position(3045, 4821));
}

/**
 * The obstacles an [AbyssPassage] can show.
 *
 * @property id The obstacle object the passage turns into.
 * @property giving The layout value that shows every obstacle of this kind starting to give way.
 * @property gone The layout value that shows every obstacle of this kind cleared.
 */
enum class AbyssObstacle(val id: Int, val giving: Int = 0, val gone: Int = 0) {
    BLOCKAGE(7156),
    ROCK(7158, giving = 12, gone = 13),
    TENDRILS(7161, giving = 14, gone = 15),
    BOIL(7165, giving = 16, gone = 17),
    EYES(7168, giving = 18, gone = 19),
    GAP(7164),
    PASSAGE(7154);

    companion object {

        /**
         * Maps obstacle object ids to their [AbyssObstacle].
         */
        val ID_TO_OBSTACLE = entries.associateBy { it.id }
    }
}
