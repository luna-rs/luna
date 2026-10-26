package game.content.gnomeBall

import com.google.common.collect.ImmutableSet
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerAreaListener

/**
 * Applies the pitch rules to all entry routes and cleans up possession immediately on departure.
 *
 * @author lare96
 */
object GnomeBallAreaListener : PlayerAreaListener() {
    override fun computeLocatables(): ImmutableSet<Locatable> = ImmutableSet.of(object : Locatable {
        override fun abs(): Position = GnomeBall.EXIT
        override fun getX(): Int = 2382
        override fun getY(): Int = 3488
        override fun contains(position: Position): Boolean = GnomeBall.inside(position)
    })
    override fun enter(plr: Player) { if (!GnomeBall.enter(plr)) plr.move(GnomeBall.EXIT) }
    override fun exit(plr: Player) { GnomeBall.leave(plr) }
}
