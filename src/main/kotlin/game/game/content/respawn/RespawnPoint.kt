package game.content.respawn

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.Luna
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * Where a player reappears after dying or being saved by a Ring of life.
 *
 * @author TheLining
 */
object RespawnPoint {

    /**
     * The Falador respawn, in the grounds of the White Knights' Castle.
     */
    val FALADOR = Position(2970, 3342)

    /**
     * Whether the player chose Sir Tiffy's Falador respawn over Lumbridge.
     */
    var Player.faladorRespawn by Attr.boolean().persist("falador_respawn")

    /**
     * The tile [plr] respawns on.
     */
    fun position(plr: Player): Position =
        if (plr.faladorRespawn) FALADOR else Luna.settings().game().startingPosition()
}
