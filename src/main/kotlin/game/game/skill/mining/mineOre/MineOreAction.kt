package game.skill.mining.mineOre

import api.predef.*
import api.predef.ext.*
import game.skill.Skills
import game.skill.mining.Mining
import game.skill.mining.Ore
import game.skill.mining.Ore.PURE_ESSENCE
import game.skill.mining.Ore.RUNE_ESSENCE
import game.skill.mining.Pickaxe
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject
import java.util.WeakHashMap

/**
 * An [InventoryAction] that will enable the mining of rocks. The swing comes on the tick after the click and the first
 * roll two ticks before one [Pickaxe.speed] has passed, then one roll every [Pickaxe.speed] ticks, each restarting the
 * animation.
 *
 * @author lare96
 */
class MineOreAction(plr: Player, val pick: Pickaxe, val ore: Ore, val rockObj: GameObject) :
    InventoryAction(plr, false, pick.speed - 1, Int.MAX_VALUE) {

    companion object {

        /**
         * Rock -> the tick of the roll that emptied it. Players who succeed on the same tick all get ore.
         */
        private val emptiedOn = WeakHashMap<GameObject, Long>()
    }

    /**
     * The tick of this action's last successful roll.
     */
    private var successTick = -1L

    /**
     * If the swing at the start has happened.
     */
    private var swung = false

    /**
     * The uncut gem found on this attempt instead of ore, if any.
     */
    private var gem: Int? = null

    /**
     * If the last free inventory slot was just filled, so that mining stops on the next tick.
     */
    private var stopFull = false

        // TODO@0.5.0 Implement correct sounds: MINE_QUICK, MINE, FOUND_GEM, MINE_3, MINING_3, MINE_5.
    override fun executeIf(start: Boolean) = when {
        mob.inventory.isFull -> {
            // No inventory space.
            mob.sendMessage(onInventoryFull())
            false
        }

        mob.mining.level < ore.level -> {
            // Check if we have required level.
            mob.sendMessage("You need a Mining level of ${ore.level} to mine this.")
            false
        }

        !Pickaxe.hasPick(mob, pick) -> {
            // Check if we still have a pickaxe.
            mob.sendMessage("You need a pickaxe to mine ores.")
            false
        }
        // Check if rock isn't already mined, unless this player found ore on the same tick as the one who emptied it.
        rockObj.state == EntityState.INACTIVE && emptiedOn[rockObj] != world.currentTick -> false
        else -> {
            if (!start) {
                delay = pick.speed
                swing()
            }
            true
        }
    }

    override fun onProcess() {
        if (stopFull) {
            mob.sendMessage(onInventoryFull())
            complete()
            return
        }
        if (!swung) {
            swung = true
            mob.sendMessage("You swing your pick at the rock.")
            swing()
        }
    }

    override fun execute() {
        val foundGem = gem
        if (foundGem != null) {
            // A gem takes the place of this attempt's ore, and the rock keeps its ore.
            gem = null
            mob.sendMessage(Mining.gemMessage(foundGem))
            if (mob.inventory.isFull) {
                stopFull = true
            }
            return
        }
        if (currentAdd.isNotEmpty()) {
            if (ore == RUNE_ESSENCE) {
                // The essence rock never runs out and sends no message per essence: mine until the inventory is full.
                mob.mining.addExperience(ore.exp)
                if (mob.inventory.isFull) {
                    stopFull = true
                }
                return
            }
            // Ore roll was successful.
            mob.sendMessage("You manage to mine some ${ore.typeName.lowercase()}.")
            mob.mining.addExperience(ore.exp)
          //  mob.playSound(Sound.MINING_COMPLETED)
            deleteAndRespawnRock()
            complete()
        }
    }

    override fun add(): List<Item> {
        if (ore != RUNE_ESSENCE) {
            // The gem roll comes before the ore roll.
            gem = Mining.rollGem(mob)
            val foundGem = gem
            if (foundGem != null) {
                return listOf(Item(foundGem))
            }
        }
        val (low, high) = ore.chance
        return when {
            ore == RUNE_ESSENCE -> if (mob.mining.level >= PURE_ESSENCE.level)
                listOf(Item(PURE_ESSENCE.item)) else listOf(Item(RUNE_ESSENCE.item))

            Skills.success(low, high, mob.mining.level) -> {
                successTick = world.currentTick
                listOf(Item(ore.item))
            }

            else -> emptyList()
        }
    }

    override fun onFinished() = mob.animation(Animation.CANCEL)

    override fun onInventoryFull() =
        "Your inventory is too full to hold any more ${if (ore == RUNE_ESSENCE) "rune stones" else ore.typeName.lowercase()}."

    /**
     * Faces the rock and starts the mining animation.
     */
    private fun swing() {
        mob.interact(rockObj)
        mob.animation(pick.animation)
    }

    /**
     * Deletes and respawns an [Ore] rock object.
     */
    private fun deleteAndRespawnRock() {
        if (rockObj.state == EntityState.INACTIVE) {
            // Another player emptied it on the same tick.
            return
        }
        emptiedOn[rockObj] = successTick
        val emptyId = Ore.ORE_TO_EMPTY[rockObj.id]
        if (emptyId != null && emptyId != -1) {
            world.addObject(emptyId, rockObj.position, rockObj.objectType, rockObj.direction, null)
            val respawn = ore.respawnTicks
            if (respawn != null) {
                world.scheduleOnce(respawn) {
                    world.addObject(rockObj.id, rockObj.position, rockObj.objectType, rockObj.direction, null)
                }
            }
        }
    }
}
