package game.skill.agility

import api.predef.*
import api.predef.ext.*
import game.skill.Skills
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject

/**
 * The unrepaired Lighthouse bridge from Lost City's horror_cross_bridge script. Both ends can be crossed without
 * a minimum level: current Agility controls the jump, and a failed jump deals one to five damage on the far bank.
 * TODO Use Horror from the Deep's repaired-bridge sidestep once its persistent bridge states exist.
 *
 * @author lare96
 */
object LighthouseBridge {

    val WEST = Position(2596, 3608)
    val EAST = Position(2598, 3608)
    val WEST_OBJECT = Position(2596, 3608, 1)
    val EAST_OBJECT = Position(2598, 3608, 1)
    val WEST_EXIT = Position(2595, 3608)
    val EAST_EXIT = Position(2599, 3608)

    /**
     * Only these two native spots lead across the broken bridge.
     */
    fun start(obj: GameObject): Position? = when {
        obj.id == 4615 && obj.position == WEST_OBJECT -> WEST
        obj.id == 4616 && obj.position == EAST_OBJECT -> EAST
        else -> null
    }

    /**
     * Aligns the player on the clicked spot, then preserves the source jump, failure damage and half-point XP.
     */
    fun cross(player: Player, obj: GameObject) {
        val from = start(obj) ?: return
        if (!ready(player) || player.position.z != 0 ||
            player.position.computeLongestDistance(from) > AgilityApproach.RADIUS) return
        val begin = begin@ {
            if (!ready(player) || player.position != from) return@begin
            val dx = if (obj.id == 4615) 1 else -1
            val failed = !Skills.success(5, 255, minOf(player.agility.level, 99))
            val landing = from.translate(2 * dx, 0)
            val steps = mutableListOf(AgilityStep(landing, if (failed) 3 else 2,
                moveStart = if (failed) 30 else 58, moveEnd = if (failed) 49 else 70,
                begin = { player.animation(Animation(if (failed) 1115 else 769, if (failed) 20 else 30,
                    Animation.AnimationPriority.NORMAL)) }))
            if (!failed) steps += AgilityStep(from.translate(3 * dx, 0), 1, walkingAnimation = AgilityWalk.NORMAL)
            player.submitAction(AgilityAction(player, steps, null, valid = {
                player.state == EntityState.ACTIVE && player.isAlive
            }).also {
                it.onSuccess = {
                    if (failed) player.damage(rand(1, 5))
                    player.agility.addExperience(0.5)
                }
            })
        }
        if (player.position == from) begin() else player.submitAction(AgilityApproach(player, from) { begin() })
    }

    private fun ready(player: Player) = player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked
}
