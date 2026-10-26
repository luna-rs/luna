package game.content.brimhavenArena

import com.google.common.collect.ImmutableSet
import io.luna.game.model.Locatable
import io.luna.game.model.Region
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerAreaListener

/**
 * Tracks both arena floors, applies step-on traps and removes visit state on any departure route.
 * Ordinary combat and death rules remain in force; traversal locks belong to native agility actions.
 *
 * @author lare96
 */
object ArenaAreaListener : PlayerAreaListener() {
    override fun computeLocatables(): ImmutableSet<Locatable> = ImmutableSet.of(Region(43, 149))
    override fun enter(plr: Player) { if (!BrimhavenArena.enter(plr)) plr.move(BrimhavenArena.ENTRANCE) }
    override fun exit(plr: Player) { BrimhavenArena.leave(plr) }
    override fun process(plr: Player) { BrimhavenArena.process(plr) }
}
