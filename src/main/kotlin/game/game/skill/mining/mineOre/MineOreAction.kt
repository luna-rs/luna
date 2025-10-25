package game.skill.mining.mineOre

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.impl.ItemContainerAction.AnimatedInventoryAction
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject
import game.player.Sounds
import game.skill.Skills
import game.skill.mining.Mining
import game.skill.mining.Ore
import game.skill.mining.Ore.PURE_ESSENCE
import game.skill.mining.Ore.RUNE_ESSENCE
import game.skill.mining.Pickaxe

/**
 * An [AnimatedInventoryAction] that will enable the mining of rocks.
 *
 * @author lare96
 */
class MineOreAction(plr: Player, val pick: Pickaxe, val ore: Ore, val rockObj: GameObject) :
    AnimatedInventoryAction(plr, 1, 4, Int.MAX_VALUE) {

    override fun executeIf(start: Boolean) = when {
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
        // Check if rock isn't already mined .
        rockObj.state == EntityState.INACTIVE -> false
        else -> true
    }

    override fun execute() {
        if (executions == 0) {
            mob.sendMessage("You swing your pick at the rock.")
            mob.interact(rockObj)
            delay = pick.speed
        } else {
            val gemItems = Mining.MINING_GEM_DROP_TABLE.roll(mob, rockObj)
            if (!mob.inventory.isFull && gemItems.isNotEmpty()) {
                // Gem rolls happen independently of ore rolls.
                mob.inventory.add(gemItems.removeFirst())
                mob.sendMessage("You find a gem in the rock.")
            }
            if (currentAdd.isNotEmpty()) {
                // Ore roll was successful.
                mob.sendMessage("You manage to mine some ${ore.typeName.lowercase()}.")
                mob.mining.addExperience(ore.exp)
                mob.playSound(Sounds.MINING_COMPLETED)
                deleteAndRespawnRock()
                complete()
            }
        }
    }

    override fun animation(): Animation {
        mob.playSound(Sounds.MINE_ROCK)
        return pick.animation
    }

    override fun add(): List<Item> {
        val (low, high) = ore.chance
        return when {
            executions == 0 -> emptyList()

            ore == RUNE_ESSENCE -> if (mob.mining.level >= PURE_ESSENCE.level)
                listOf(Item(PURE_ESSENCE.item)) else listOf(Item(RUNE_ESSENCE.item))

            Skills.success(low, high, mob.mining.level) -> listOf(Item(ore.item))

            else -> emptyList()
        }
    }

    override fun onFinished() = mob.animation(Animation.CANCEL)

    /**
     * Deletes and respawns an [Ore] rock object.
     */
    private fun deleteAndRespawnRock() {
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