package game.item.teleport

import api.predef.*
import game.player.Sound
import game.skill.magic.teleportSpells.TeleportAction
import game.skill.magic.teleportSpells.TeleportSequence
import io.luna.game.model.LocalSound
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.block.Animation

/**
 * The Ectophial's teleport: the player pours the ectoplasm out and lands at the Ectofuntus three ticks later.
 *
 * @author TheLining
 */
object EctophialTeleport : TeleportSequence {

    /**
     * The full Ectophial item id.
     */
    const val FULL = 4251

    /**
     * The empty Ectophial item id.
     */
    const val EMPTY = 4252

    /**
     * The Ectofuntus object id, which refills the Ectophial.
     */
    const val ECTOFUNTUS = 5282

    /**
     * The tile north of the Ectofuntus the player lands on.
     */
    val DESTINATION = Position(3660, 3522, 0)

    /**
     * The animation of the player pouring out the Ectophial.
     */
    private val POUR_ANIMATION = Animation(1652)

    override fun step(action: TeleportAction): Boolean {
        val plr = action.mob
        return when (action.executions) {
            0 -> {
                plr.animation(POUR_ANIMATION)
                LocalSound.of(ctx, Sound.LIQUID, plr.position, ChunkUpdatableView.globalView()).display()
                true
            }

            1, 2 -> true
            3 -> {
                // Unlike a regular teleport, the pour isn't cancelled on landing and plays out at the destination.
                action.land()
                false
            }

            else -> false
        }
    }
}
