package world.player.skill.thieving.stealFromStall

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.RepeatingAction
import io.luna.game.model.EntityState
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import world.player.Animations
import world.player.Messages
import world.player.Sounds
import world.player.following.FollowingAction
import world.player.skill.thieving.Thieving

/**
 * A [RepeatingAction] that handles players stealing from market stalls.
 */
class StealFromAction(plr: Player, val obj: GameObject, val thievable: ThievableStall) :
    RepeatingAction<Player>(plr, true, 1) {

    companion object {

        /**
         * A set of names of guards in local cities.
         */
        private val GUARD_NAMES = setOf("Guard", "Market Guard")
    }

    /**
     * The possible states this action can be in.
     */
    private enum class State {
        SEARCHING,
        LOOTING
    }

    /**
     * The current state of this action.
     */
    private var state = State.SEARCHING

    /**
     * The loot that will be obtained.
     */
    private val loot = ArrayList<Item>()

    override fun start(): Boolean = true

    override fun repeat() {
        if (mob.thieving.level < thievable.level) {
            mob.sendMessage("You need a Thieving level of ${thievable.level} to steal from here.")
            interrupt()
            return
        } else if (obj.state == EntityState.INACTIVE) {
            interrupt()
            return
        }
        when (state) {
            State.SEARCHING -> searchStall()
            State.LOOTING -> stealLoot()
        }
    }

    override fun ignoreIf(other: Action<*>?): Boolean = when (other) {
        is StealFromAction -> other.thievable == thievable && obj == other.obj
        else -> false
    }

    /**
     * Start searching the stall for something to steal. At this stage, nearby guards can be alerted by your actions.
     */
    private fun searchStall() {
        val rolls = if (Thieving.isDoubleLoot(mob)) 2 else 1
        repeat(rolls) {
            loot += thievable.drops.roll(mob, obj)
        }
        if (!mob.inventory.hasSpaceForAll(loot)) {
            mob.sendMessage(Messages.INVENTORY_FULL)
            return
        }
        mob.interact(obj)
        mob.animation(Animations.PICKPOCKET)
        alertGuards()
        state = State.LOOTING
    }

    /**
     * Start searching the stall for something to steal. At this stage, nearby guards can be alerted by your actions.
     */
    private fun stealLoot() {
        if (mob.state != EntityState.INACTIVE && obj.state != EntityState.INACTIVE) {
            replaceStall()
            mob.sendMessage("You steal from the ${obj.definition.name}.")
            mob.thieving.addExperience(thievable.xp)
            if (loot.isNotEmpty()) {
                mob.inventory.addAll(loot)
                mob.playSound(Sounds.PICKUP_ITEM)
            }
            Thieving.rollRogueEquipment(mob, obj, UNCOMMON)
        } else {
            mob.sendMessage("You were too late!")
        }
        interrupt()
    }

    /**
     * Determines if nearby guards will be alerted by you stealing and if so, alerts them.
     */
    private fun alertGuards() {
        val nearbyGuards = world.chunks.getViewableEntities<Npc>(mob.position, TYPE_NPC)
            .filter { GUARD_NAMES.contains(it.definition.name) && it.definition.actions.contains("Attack") }
        for (guard in nearbyGuards) {
            if (!world.collisionManager.raycast(guard.position, mob.position) && guard.isInViewCone(mob)) {
                // The guard can see the player.
                // TODO Make them attack when combat completed
                guard.forceChat("Hey! Get your hands off there!")
                guard.actions.submit(FollowingAction(guard, mob))
                break
            }
        }
    }

    /**
     * Replaces the stall object once its been looted.
     */
    private fun replaceStall() {
        if (world.objects.unregister(obj)) {
            val view =
                if (thievable.globalRefresh) ChunkUpdatableView.globalView() else ChunkUpdatableView.localView(mob)
            val newId = ThievableStall.FULL_TO_EMPTY[obj.id]
            if (newId != null && newId != -1) {
                world.objects.register(GameObject.createDynamic(
                    ctx, newId, obj.position, obj.objectType,
                    obj.direction, view))
            }

            world.scheduleOnce(thievable.respawnTicks) {
                world.objects.register(GameObject.createDynamic(ctx, obj.id, obj.position, obj.objectType,
                                                                obj.direction, view))
            }
        }
    }
}