package game.skill.thieving.stealFromStall

import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.player.Messages
import game.player.Sound
import game.skill.thieving.Thieving
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * A [LockedAction] that handles players stealing from market stalls.
 *
 * @author lare96
 */
class StealFromAction(plr: Player, val obj: GameObject, val thievable: ThievingStallType) : LockedAction(plr) {

    companion object {

        /**
         * A set of names of guards in local cities.
         */
        val GUARD_NAMES = setOf("Guard", "Market Guard")
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
            mob.sendMessage("You need a Thieving level of ${thievable.level} to steal from here.")
            return true
        } else if (obj.state == EntityState.INACTIVE) {
            return true
        }
        return when (state) {
            State.SEARCHING -> searchStall()
            State.LOOTING -> stealLoot()
        }
    }

    /**
     * Start searching the stall for something to steal. At this stage, nearby guards can be alerted by your actions.
     */
    private fun searchStall(): Boolean {
        if (mob.combat.inCombat()) {
            mob.sendMessage("You can't steal from the market stall during combat!")
            return true
        }
        val rolls = if (Thieving.isDoubleLoot(mob)) 2 else 1
        repeat(rolls) {
            loot += thievable.drops.roll(mob, obj)
        }
        if (!mob.inventory.hasSpaceForAll(loot)) {
            mob.sendMessage(Messages.inventoryFull())
            return true
        }
        mob.interact(obj)
        thievable.attempt?.let { mob.sendMessage(it) }
        mob.animation(Animations.PICKPOCKET)
        if (alertOwner() || alertGuards()) {
            // Seen: the theft doesn't happen.
            return true
        }
        state = State.LOOTING
        return false
    }

    /**
     * Start searching the stall for something to steal. At this stage, nearby guards can be alerted by your actions.
     */
    private fun stealLoot(): Boolean {
        if (mob.state != EntityState.INACTIVE && obj.state != EntityState.INACTIVE) {
            replaceStall()
            val stolen = loot.firstOrNull()?.let { thievable.stolen[it.itemDef.name] }
            mob.sendMessage(if (stolen != null) "You steal $stolen." else "You steal from the ${obj.def().name}.")
            mob.thieving.addExperience(thievable.xp)
            if (loot.isNotEmpty()) {
                mob.inventory.addAll(loot)
                mob.playSound(Sound.PICKUP_ITEM)
            }
            Thieving.rollRogueEquipment(mob, obj, UNCOMMON)
        } else {
            mob.sendMessage("You were too late!")
        }
        return true
    }

    /**
     * Determines if the stall's owner can see you stealing and if so, has them shout.
     *
     * @return `true` if the owner saw you.
     */
    private fun alertOwner(): Boolean {
        val owners = world.locator.findViewableNpcs(mob) { thievable.owners.contains(it.def().name) }
        for (owner in owners) {
            if (world.collisionManager.raycast(owner.position, mob.position) && owner.inViewCone(mob)) {
                owner.speak("Hey! Get your hands off there!")
                return true
            }
        }
        return false
    }

    /**
     * Determines if nearby guards will be alerted by you stealing and if so, alerts them.
     *
     * @return `true` if a guard saw you.
     */
    private fun alertGuards(): Boolean {
        val nearbyGuards = world.locator.findViewableNpcs(mob) {
            !it.combat.inCombat() && GUARD_NAMES.contains(it.def().name) && it.combat.isAttackable
        }
        for (guard in nearbyGuards) {
            if (world.collisionManager.raycast(guard.position, mob.position) && guard.inViewCone(mob) && !guard.combat.inCombat()) {
                // The guard can see the player.
                guard.speak("Hey! Get your hands off there!")
                guard.combat.attack(mob)
                return true
            }
        }
        return false
    }

    /**
     * Replaces the stall object once its been looted.
     */
    private fun replaceStall() {
        val view = if (thievable.globalRefresh) ChunkUpdatableView.globalView() else ChunkUpdatableView.localView(mob)
        val newId = ThievingStallType.FULL_TO_EMPTY[obj.id]
        if (newId == null || newId == -1) {
            return
        }
        val emptyObj = GameObject.createDynamic(
            ctx, newId, obj.position, obj.objectType,
            obj.direction, view)
        if (world.objects.register(emptyObj)) {
            world.scheduleOnce(thievable.respawnTicks) {
                world.objects.register(GameObject.createDynamic(ctx, obj.id, obj.position, obj.objectType,
                                                                obj.direction, view))
            }
        }
    }
}