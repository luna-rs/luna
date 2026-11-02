package game.obj.entrances

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.mob.Player

/**
 * Personal rope state shared by the native Swamp Caves entrance and web-walker admission.
 * The existing persisted key is retained; tying a rope once lets this player descend again without another.
 *
 * @author lare96
 */
object SwampCaveEntrance {
    const val DARK_HOLE = 5947
    const val ROPE = 954

    /**
     * Whether this player has already attached their entrance rope.
     */
    var Player.swampCavesRoped by Attr.boolean().persist("swamp_caves_roped")

    /**
     * Forces attribute registration during native script bootstrap, before player profiles are loaded.
     */
    fun initialize() = Unit

    /**
     * Uses the same personal flag and carried rope that the native climb handler checks.
     */
    fun canEnter(player: Player): Boolean = player.swampCavesRoped || player.inventory.contains(ROPE)
}
