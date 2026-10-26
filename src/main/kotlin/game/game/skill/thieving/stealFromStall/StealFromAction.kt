package game.skill.thieving.stealFromStall

import api.attr.Attr
import api.attr.getValue
import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.player.Messages
import game.player.Sound
import game.skill.thieving.Thieving
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.Locatable
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration

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

        /**
         * Determines if [npc] is a guard who would stop a theft right now. A guard who is already fighting doesn't.
         *
         * @param npc The NPC.
         * @return `true` if [npc] is a guard free to stop a theft.
         */
        fun isGuard(npc: Npc): Boolean =
            !npc.combat.inCombat() && GUARD_NAMES.contains(npc.def().name) && npc.combat.isAttackable

        /**
         * Determines if [npc] can see a theft by someone standing on [thief]: nothing blocks its line of sight, and
         * [thief] is inside its view cone.
         *
         * @param npc The NPC watching.
         * @param thief The thief, or the tile a thief would stand on.
         * @return `true` if [npc] sees the theft.
         */
        fun canSee(npc: Npc, thief: Locatable): Boolean =
            world.collisionManager.raycast(npc.position, thief.abs()) && npc.inViewCone(thief)

        /**
         * When the player last stole from each stall, by full stall object id, in epoch milliseconds.
         */
        private val Player.stallThefts by Attr.map<Int, Long>()

        /**
         * Determines if [plr] successfully stole from the stall [stallId] within the last [within].
         *
         * @param plr The player.
         * @param stallId The full stall object id.
         * @param within How far back a theft counts.
         * @return `true` if [plr] stole from the stall recently.
         */
        fun stoleRecently(plr: Player, stallId: Int, within: Duration): Boolean {
            val stolenAt = plr.stallThefts[stallId] ?: return false
            return System.currentTimeMillis() - stolenAt < within.inWholeMilliseconds
        }

        /**
         * Has the first nearby guard who is free to stop a thief attack [plr], as when a merchant calls for the guards.
         *
         * @param plr The thief.
         */
        fun callGuards(plr: Player) {
            world.locator.findViewableNpcs(plr) { isGuard(it) }.firstOrNull()?.combat?.attack(plr)
        }
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
            mob.stallThefts[obj.id] = System.currentTimeMillis()
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
            if (canSee(owner, mob)) {
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
        val nearbyGuards = world.locator.findViewableNpcs(mob) { isGuard(it) }
        for (guard in nearbyGuards) {
            if (canSee(guard, mob)) {
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