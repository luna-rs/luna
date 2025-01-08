package world.player.skill.magic.teleOther

import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.RepeatingAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import world.player.Animations
import world.player.skill.magic.Magic

/**
 * A [RepeatingAction] that handles [source] teleporting [target] to another location based on [type].
 */
class TeleOtherAction(private val source: Player, private val target: Player, private val type: TeleOtherType) :
    RepeatingAction<Player>(source, true, 1) {

    override fun start(): Boolean = Magic.checkRequirements(source, type.level, type.requirements)

    override fun repeat() {
        when (executions) {
            0 -> {
                source.lock(4)
                target.lock(4)
                source.animation(Animations.CAST_TELEPORT_OTHER)
                source.graphic(Graphic(343, 50))
            }
            3 -> {
                target.graphic(Graphic(342))
                target.animation(Animations.RECEIVE_TELEPORT_OTHER)
            }
            6 -> {
                target.teleport(type.destination)
                source.sendMessage("You teleport ${target.username} to ${type.location}.")
                target.sendMessage("You are teleported to ${type.location}.")
                target.animation(Animation.CANCEL)
            }
        }
    }

    override fun ignoreIf(other: Action<*>?): Boolean = true // Other actions always ignored while in progress.
}