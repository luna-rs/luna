package game.content.duelArena

import com.google.common.collect.ImmutableSet
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption
import io.luna.game.model.mob.controller.PlayerAreaListener

/**
 * Publishes the native Challenge action in the Duel Arena grounds.
 *
 * @author lare96
 */
object DuelAreaListener : PlayerAreaListener() {
    override fun computeLocatables(): ImmutableSet<Locatable> = ImmutableSet.of(object : Locatable {
        override fun abs(): Position = Position(x, y)
        override fun getX(): Int = 3328
        override fun getY(): Int = 3203
        override fun contains(position: Position): Boolean = DuelArena.inside(position)
    })
    override fun enter(plr: Player) {
        if (!DuelArena.arena(plr.position)) plr.contextMenu.show(PlayerContextMenuOption.CHALLENGE)
    }
    override fun exit(plr: Player) {
        if (DuelArena.session(plr)?.started != true) plr.contextMenu.hide(PlayerContextMenuOption.CHALLENGE)
        DuelArena.leave(plr)
    }
}
