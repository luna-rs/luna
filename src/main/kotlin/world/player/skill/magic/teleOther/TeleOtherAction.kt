package world.player.skill.magic.teleOther

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.LocalSound
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import world.player.Animations
import world.player.Sounds
import world.player.skill.magic.Magic

/**
 * A [RepeatingAction] that handles [source] teleporting [target] to another location based on [type].
 */
class TeleOtherAction(private val source: Player, private val target: Player, private val type: TeleOtherType) :
    LockedAction(source) {

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

    override fun onLock() {
        val removeItems = Magic.checkRequirements(source, type.level, type.requirements)
        if (removeItems == null) {
            complete()
            return
        }
        source.inventory.removeAll(removeItems)
        source.magic.addExperience(type.xp)
        target.lock()
    }

    override fun run(): Boolean {
        return when (executions) {
            0 -> {
                val sound = LocalSound(ctx, Sounds.TELEOTHER, source.position,
                                       ChunkUpdatableView.globalView())
                sound.display()
                source.animation(Animations.CAST_TELEOTHER)
                source.graphic(Graphic(343, 50))
                false
            }

            1, 2 -> false
            3 -> {
                target.graphic(Graphic(342))
                target.animation(Animations.RECEIVE_TELEOTHER)
                false
            }

            4, 5 -> false
            6 -> {
                target.move(type.destination)
                source.sendMessage("You teleport ${target.username} to ${type.location}.")
                target.sendMessage("You are teleported to ${type.location} by {${source.username}.")
                target.animation(Animation.CANCEL)
                true
            }

            else -> true
        }
    }

    override fun onUnlock() {
        target.unlock()
    }
}