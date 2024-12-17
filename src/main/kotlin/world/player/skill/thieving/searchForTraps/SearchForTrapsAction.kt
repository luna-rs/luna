package world.player.skill.thieving.searchForTraps

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.RepeatingAction
import io.luna.game.model.EntityState
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import world.player.Animations
import world.player.Messages
import world.player.Sounds
import world.player.skill.thieving.Thieving

/**
 * A [RepeatingAction] that handles searching chests for traps.
 */
class SearchForTrapsAction(plr: Player, val obj: GameObject, val thievable: ThievableChest) :
    RepeatingAction<Player>(plr, true, 1) {

    companion object {

        /**
         * The ID of the temporary unlocked chest.
         */
        private val UNLOCKED_CHEST = 2574
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
            mob.sendMessage("You need a Thieving level of ${thievable.level} to search for traps here.")
            interrupt()
            return
        } else if (obj.state == EntityState.INACTIVE) {
            interrupt()
            return
        }
        when (state) {
            State.SEARCHING -> searchChest()
            State.LOOTING -> lootChest()
        }
    }

    override fun ignoreIf(other: Action<*>?): Boolean = when (other) {
        is SearchForTrapsAction -> other.thievable == thievable
        else -> false
    }

    /**
     * Start searching the chest for traps.
     */
    private fun searchChest() {
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
        state = State.LOOTING
    }

    /**
     * Loot the chest.
     */
    private fun lootChest() {
        if (mob.state != EntityState.INACTIVE && obj.state != EntityState.INACTIVE) {
            replaceChest()
            mob.sendMessage("You unlock the chest.")
            mob.thieving.addExperience(thievable.xp)
            if (loot.isNotEmpty()) {
                mob.inventory.addAll(loot)
                mob.playSound(Sounds.PICK_LOCK)
            }
            Thieving.rollRogueEquipment(mob, obj, UNCOMMON)
        } else {
            mob.sendMessage("You were too late!")
        }
        interrupt()
    }

    /**
     * Replaces the chest object once it's been looted.
     */
    private fun replaceChest() {
        if (world.objects.unregister(obj)) {
            val view =
                if (thievable.globalRefresh) ChunkUpdatableView.globalView() else ChunkUpdatableView.localView(mob)
                world.objects.register(GameObject.createDynamic(
                    ctx, UNLOCKED_CHEST, obj.position, obj.objectType,
                    obj.direction, view))

            world.scheduleOnce(thievable.respawnTicks) {
                world.objects.register(GameObject.createDynamic(ctx, obj.id, obj.position, obj.objectType,
                                                                obj.direction, view))
            }
        }
    }
}