package game.item.teleport

import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.player.Jingles
import game.player.Sound
import game.skill.magic.teleportSpells.TeleportSequence
import io.luna.game.model.LocalSound
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic

/**
 * Constants and the teleport for the Enchanted lyre, which teleports the player to Rellekka once Fossegrimen has
 * enchanted it at the Strange altar.
 *
 * @author TheLining
 */
object EnchantedLyre {

    /**
     * The plain strung lyre. Fossegrimen only enchants this one, and an enchanted lyre turns back into it once its
     * last charge is used.
     */
    const val LYRE = 3689

    /**
     * Each enchanted lyre, from (4) down to (1), mapped to the lyre it becomes once a charge is used.
     */
    val NEXT_CHARGE = mapOf(6127 to 6126, 6126 to 6125, 6125 to 3691, 3691 to LYRE)

    /**
     * The tile the lyre teleports to.
     */
    val RELLEKKA = Position(2662, 3644)

    /**
     * How far from [RELLEKKA] the player may land.
     */
    const val RELLEKKA_RADIUS = 2

    /**
     * The deepest Wilderness level the lyre works from.
     */
    const val MAX_WILDERNESS_LEVEL = 20

    /**
     * The strum played on the tick the lyre is played.
     */
    private val STRUM = Animation(1320)

    /**
     * The bubble the player vanishes in, the same one a Tele Other target gets.
     */
    private val BUBBLE = Graphic(342)

    /**
     * The lyre's teleport: a strum and the 'perfectly tuned' jingle when played, the Tele Other bubble three ticks
     * later, and the landing three ticks after that.
     */
    val TELEPORT = TeleportSequence { action ->
        val plr = action.mob
        when (action.executions) {
            0 -> {
                plr.animation(STRUM)
                plr.playJingle(Jingles.PERFECTLY_TUNED)
                true
            }

            3 -> {
                LocalSound.of(ctx, Sound.TELEPORT_ALL, plr.position, ChunkUpdatableView.globalView()).display()
                plr.animation(Animations.RECEIVE_TELEOTHER)
                plr.graphic(BUBBLE)
                true
            }

            6 -> {
                action.land()
                plr.animation(Animation.CANCEL)
                false
            }

            else -> true
        }
    }
}
