package game.skill.magic.teleOther

import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.player.Sound
import io.luna.game.action.Action
import io.luna.game.action.ActionState
import io.luna.game.action.ActionType
import io.luna.game.model.LocalSound
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic

/**
 * A npc-controlled tele-other style action that moves one or more mobs to a destination.
 *
 * This action is used for NPCs such as Aubury, where the NPC performs the casting animation and every queued target
 * receives the teleport graphic before being moved. Additional mobs can be queued while the action is already running,
 * allowing several players to share the same NPC casting sequence instead of starting overlapping actions.
 *
 * @param npc The npc performing the tele-other action.
 * @param target The first mob that requested the teleport.
 * @param destination The position all queued mobs are moved to when the action completes.
 * @author lare96
 */
class NpcTeleOtherAction(npc: Npc, val target: Mob, val destination: Position) : Action<Npc>(npc, ActionType.STRONG) {

    /**
     * The mobs currently waiting to be teleported by this action.
     *
     * A set is used so the same mob cannot be queued more than once during the same casting sequence.
     */
    private val requests = HashSet<Mob>()

    override fun onSubmit() {
        mob.interact(target)
        requests.add(target)
        mob.speak("Senventior Disthine Molenko!")

        requests.forEach {
            it.lockMovement()
        }
    }

    override fun onFinished() {
        mob.interact(null)

        requests.forEach {
            it.unlockMovement()
        }
    }

    override fun run(): Boolean {
        return when (executions) {
            0 -> {
                mob.animation(Animations.CAST_TELEOTHER)
                mob.graphic(Graphic(343, 50))
                false
            }

            1, 2 -> false

            3 -> {
                val sound = LocalSound.of(
                    ctx,
                    Sound.TELEPORT_ALL,
                    target.position,
                    ChunkUpdatableView.globalView()
                )
                sound.display()

                requests.forEach {
                    it.graphic(Graphic(342))
                    it.animation(Animations.RECEIVE_TELEOTHER)
                }

                false
            }

            4, 5 -> false

            6 -> {
                requests.forEach {
                    it.move(destination)

                    if (it is Player) {
                        it.sendMessage("You are teleported to the Rune Essence Mine.")
                    }

                    it.animation(Animation.CANCEL)
                }

                true
            }

            else -> true
        }
    }

    /**
     * Adds [mob] to the current teleport request queue if this action is [ActionState.PROCESSING].
     *
     * If the mob was not already queued, it is locked immediately so late requests behave the same as the initial target.
     *
     * @param mob The mob waiting to be teleported.
     */
    fun addRequest(mob: Mob) {
        if (state == ActionState.PROCESSING && requests.add(mob)) {
            mob.lockMovement()
        }
    }

    /**
     * Locks this mob in place while it waits for an NPC tele-other action to complete.
     */
    private fun Mob.lockMovement() {
        if (this is Player) {
            lock()
        } else {
            walking.isLocked = true
        }
    }

    /**
     * Unlocks this mob after an NPC tele-other action ends.
     */
    private fun Mob.unlockMovement() {
        if (this is Player) {
            unlock()
        } else {
            walking.isLocked = false
        }
    }
}