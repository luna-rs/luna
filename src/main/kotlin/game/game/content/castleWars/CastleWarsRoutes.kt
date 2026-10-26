package game.content.castleWars

import game.content.castleWars.CastleWarsMatch.Team
import io.luna.game.model.Position

/**
 * Revision 377 staircase destinations from Lost City's ladders and stairs scripts.
 * Decorative stair models have no operation and are deliberately absent.
 *
 * @author lare96
 */
object CastleWarsRoutes {
    private fun tile(x: Int, y: Int, plane: Int) = Position(2368 + x, 3072 + y, plane)

    val stairs = mapOf(
        (4415 to tile(62, 9, 2)) to tile(59, 9, 1),
        (4415 to tile(57, 2, 3)) to tile(57, 5, 2),
        (4415 to tile(51, 8, 1)) to tile(51, 5, 0),
        (4415 to tile(51, 6, 0)) to tile(52, 8, 1),
        (4415 to tile(1, 54, 2)) to tile(4, 54, 1),
        (4415 to tile(6, 61, 3)) to tile(6, 58, 2),
        (4415 to tile(12, 55, 1)) to tile(12, 58, 0),
        (4417 to tile(60, 9, 1)) to tile(62, 8, 2),
        (4417 to tile(57, 2, 2)) to tile(58, 2, 3),
        (4417 to tile(51, 6, 0)) to tile(52, 8, 1),
        (4418 to tile(1, 54, 1)) to tile(1, 55, 2),
        (4418 to tile(6, 59, 2)) to tile(5, 61, 3),
        (4418 to tile(12, 55, 0)) to tile(11, 55, 1)
    )

    fun destination(id: Int, objectPosition: Position, playerPosition: Position,
                    team: Team, flag: Team?): Position? {
        if (!playerPosition.isWithinDistance(objectPosition, 4)) return null
        if (flag == team && (id == 4415 || id == 4418 && team == Team.SARADOMIN ||
                id == 4419 && team == Team.ZAMORAK)) return null
        stairs[id to objectPosition]?.let { return it }
        return when (id) {
            4419 -> if (objectPosition == tile(49, 2, 0)) {
                if (playerPosition.x < objectPosition.x) tile(49, 5, 0) else tile(48, 2, 0)
            } else null
            4420 -> if (objectPosition == tile(14, 59, 0)) {
                if (playerPosition.x > objectPosition.x) tile(14, 58, 0) else tile(15, 61, 0)
            } else null
            6280, 6281 -> {
                val owner = if (id == 6280) Team.SARADOMIN else Team.ZAMORAK
                if (team != owner || flag != null || playerPosition.z != 0) null
                else Position(playerPosition.x, playerPosition.y, 1)
            }
            4471, 4472, 4911 -> if (playerPosition.z > 0)
                Position(playerPosition.x, playerPosition.y, playerPosition.z - 1) else null
            4912 -> if (playerPosition.z == 0 && playerPosition.y < 6400)
                Position(playerPosition.x, playerPosition.y + 6400) else null
            else -> null
        }
    }
}
