package game.skill.thieving.searchForTraps

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import game.player.Animations
import game.player.Messages
import game.player.Sounds
import game.skill.thieving.Thieving

/**
 * A [LockedAction] that handles searching chests for traps.
 *
 * @author lare96
 */
class SearchForTrapsAction(plr: Player, val obj: GameObject, val thievable: ThievableChest) :
    LockedAction(plr) {

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

    override fun run(): Boolean {
        if (mob.thieving.level < thievable.level) {
            mob.sendMessage("You need a Thieving level of ${thievable.level} to search for traps here.")
            return true
        } else if (obj.state == EntityState.INACTIVE) {
            mob.sendMessage("You were too late!")
            return true
        }
        return when (state) {
            State.SEARCHING -> searchChest()
            State.LOOTING -> lootChest()
        }
    }

    /**
     * Start searching the chest for traps.
     */
    private fun searchChest(): Boolean {
        val rolls = if (Thieving.isDoubleLoot(mob)) 2 else 1
        repeat(rolls) {
            loot += thievable.drops.roll(mob, obj)
        }
        if (!mob.inventory.hasSpaceForAll(loot)) {
            mob.sendMessage(Messages.INVENTORY_FULL)
            return true
        }
        mob.interact(obj)
        mob.animation(Animations.PICKPOCKET)
        state = State.LOOTING
        return false
    }

    /**
     * Loot the chest.
     */
    private fun lootChest(): Boolean {
        if (obj.state != EntityState.INACTIVE) {
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
        return true
    }

    /**
     * Replaces the chest object once it's been looted.
     */
    private fun replaceChest() {
        val view = if (thievable.globalRefresh) ChunkUpdatableView.globalView() else ChunkUpdatableView.localView(mob)
        val emptyObj = GameObject.createDynamic(ctx, UNLOCKED_CHEST, obj.position, obj.objectType, obj.direction, view)
        if (world.objects.register(emptyObj)) {
            world.scheduleOnce(thievable.respawnTicks) {
                world.objects.register(GameObject.createDynamic(ctx, obj.id, obj.position, obj.objectType,
                                                                                                             obj.direction, view))
            }
        }
    }
}