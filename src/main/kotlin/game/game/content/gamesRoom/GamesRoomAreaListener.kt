package game.content.gamesRoom

import com.google.common.collect.ImmutableSet
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption
import io.luna.game.model.mob.controller.PlayerAreaListener

/**
 * Offers board-game challenges only on the two Burthorpe Games Room floors.
 *
 * @author lare96
 */
object GamesRoomAreaListener : PlayerAreaListener() {
    override fun computeLocatables(): ImmutableSet<Locatable> = ImmutableSet.of(object : Locatable {
        override fun abs(): Position = Position(x, y)
        override fun getX(): Int = 2176
        override fun getY(): Int = 4928
        override fun contains(position: Position): Boolean = GamesRoom.inRoom(position)
    })

    override fun enter(plr: Player) { plr.contextMenu.show(PlayerContextMenuOption.CHALLENGE) }

    override fun exit(plr: Player) {
        if (plr.contextMenu.contains(PlayerContextMenuOption.CHALLENGE)) {
            plr.contextMenu.hide(PlayerContextMenuOption.CHALLENGE)
        }
        GamesRoom.leave(plr)
    }
}
