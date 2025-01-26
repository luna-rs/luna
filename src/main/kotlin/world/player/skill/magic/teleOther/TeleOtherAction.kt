package world.player.skill.magic.teleOther

import api.attr.Attr
import api.predef.*
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

    companion object {

        /**
         * Seconds that must be waited in-between teleother requests to the same person.
         */
        const val TELEOTHER_DELAY_SECONDS = 30

        /**
         * An attribute representing all recent teleother requests sent by the player.
         */
        val Player.teleOtherRequests by Attr.map<Long, Long>()
    }

    override fun start(): Boolean {
        val removeItems = Magic.checkRequirements(source, type.level, type.requirements)
        if (removeItems != null) {
            source.inventory.removeAll(removeItems)
            source.magic.addExperience(type.xp)
            return true
        }
        return false
    }

    override fun repeat() {
        when (executions) {
            0 -> {
                source.lock()
                target.lock()
                source.animation(Animations.CAST_TELEPORT_OTHER)
                source.graphic(Graphic(343, 50))
            }

            3 -> {
                target.graphic(Graphic(342))
                target.animation(Animations.RECEIVE_TELEPORT_OTHER)
            }

            6 -> {
                target.move(type.destination)
                source.sendMessage("You teleport ${target.username} to ${type.location}.")
                target.sendMessage("You are teleported to ${type.location}.")
                target.animation(Animation.CANCEL)
            }
        }
    }

    override fun stop() {
        source.unlock()
        target.unlock()
    }

    override fun ignoreIf(other: Action<*>?): Boolean = true // Other actions always ignored while in progress.
}