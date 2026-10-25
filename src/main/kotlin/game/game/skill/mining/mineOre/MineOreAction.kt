package game.skill.mining.mineOre

import api.predef.*
import api.predef.ext.*
import game.skill.mining.Mining
import game.skill.mining.Ore
import game.skill.mining.Ore.PURE_ESSENCE
import game.skill.mining.Ore.RUNE_ESSENCE
import game.skill.mining.Pickaxe
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectType
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

        /**
         * The tile and type of a mined rock -> the rock placed there in the map, until it grows back.
         */
        private val minedRocks = HashMap<Pair<Position, ObjectType>, MinedRock>()
    }

    /**
     * A rock that has been mined, and is waiting to turn back into the rock placed in the map.
     */
    private class MinedRock(val placedId: Int) {

        /**
         * Counts the times this rock was mined. Only the timer started by the last of them grows the rock back.
         */
        var timesMined = 0
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
            mob.newDialogue().text("You need a Mining level of ${ore.level} to mine this rock.").open()
            false
        }

        !Pickaxe.hasPick(mob, pick) -> {
            // Check if we still have a pickaxe.
            Pickaxe.sendNoPickaxeMessage(mob)
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
            val mined = currentAdd.first().id
            mob.sendMessage(ore.minedMessage(mined))
            mob.mining.addExperience(ore.exp(mined))
          //  mob.playSound(Sound.MINING_COMPLETED)
            if (Ore.ORE_TO_EMPTY[rockObj.id] == -1) {
                // A rock with no empty rock never runs out: mine until the inventory is full.
                if (mob.inventory.isFull) {
                    stopFull = true
                }
                return
            }
            deleteAndRespawnRock()
            complete()
        }
    }

    override fun add(): List<Item> {
        if (ore.rollsGems) {
            // The gem roll comes before the ore roll.
            gem = Mining.rollGem(mob)
            val foundGem = gem
            if (foundGem != null) {
                return listOf(Item(foundGem))
            }
        }
        if (ore == RUNE_ESSENCE) {
            return listOf(Item(if (mob.mining.level >= PURE_ESSENCE.level) PURE_ESSENCE.item else RUNE_ESSENCE.item))
        }
        val mined = ore.mine(mob, rockObj) ?: return emptyList()
        successTick = world.currentTick
        return listOf(Item(mined))
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
     * Replaces the rock with its empty rock, or the next smaller rock, and grows the rock placed in the map back
     * later.
     */
    private fun deleteAndRespawnRock() {
        if (rockObj.state == EntityState.INACTIVE) {
            // Another player emptied it on the same tick.
            return
        }
        emptiedOn[rockObj] = successTick
        val emptyId = Ore.ORE_TO_EMPTY[rockObj.id]
        if (emptyId == null || emptyId == -1) {
            return
        }
        val tile = rockObj.position to rockObj.objectType
        val minedRock = minedRocks.getOrPut(tile) { MinedRock(rockObj.id) }
        val timesMined = ++minedRock.timesMined
        world.addObject(emptyId, rockObj.position, rockObj.objectType, rockObj.direction, null)
        val respawn = ore.respawnTicks(rockObj.id) ?: return
        world.scheduleOnce(respawn) {
            if (minedRock.timesMined == timesMined) {
                minedRocks.remove(tile)
                world.addObject(minedRock.placedId, rockObj.position, rockObj.objectType, rockObj.direction, null)
            }
        }
    }
}
