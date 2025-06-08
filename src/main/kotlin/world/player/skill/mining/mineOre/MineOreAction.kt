package world.player.skill.mining.mineOre

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.impl.ItemContainerAction.AnimatedInventoryAction
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject
import world.player.Sounds
import world.player.skill.crafting.gemCutting.Gem
import world.player.skill.mining.Ore
import world.player.skill.mining.Pickaxe

/**
 * An [InventoryAction] that will enable the mining of rocks.
 */
class MineOreAction(plr: Player, val pick: Pickaxe, val ore: Ore, val rockObj: GameObject) :
    AnimatedInventoryAction(plr, 1, 4, rand(RANDOM_FAIL_RATE)) {

    companion object {

        /**
         * How often the player will randomly stop mining.
         */
        val RANDOM_FAIL_RATE = 15..55

        /**
         * The base mining rate, the lower this number the faster ores will be mined overall.
         */
        val BASE_MINE_RATE = 25

        /**
         * The base chance of mining a gem.
         */
        val BASE_GEM_CHANCE = 256

        /**
         * The base chance of mining a gem when wearing an amulet of glory.
         */
        val BOOSTED_GEM_CHANCE = 86

        /**
         * All gems that can be received, in order of lowest level -> highest.
         */
        val GEMS = Gem.values().sortedBy { it.level }
    }

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
            delay = getMiningDelay()
        } else {
            val hasGlory = mob.equipment.amulet?.itemDef?.name?.contains("Amulet of glory")
            val gemChance = if (hasGlory != null && hasGlory) BOOSTED_GEM_CHANCE else BASE_GEM_CHANCE
            if (!mob.inventory.isFull && rand().nextInt(gemChance) == 0) {
                dropGem()
            } else {
                mob.sendMessage("You manage to mine some ${ore.typeName.lowercase()}.")
                mob.mining.addExperience(ore.exp)
            }

            val removeChance = ore.depletionChance
            if (removeChance != null && (removeChance == 1 || rand().nextInt(removeChance) == 0)) {
                mob.playSound(Sounds.MINING_COMPLETED)
                deleteAndRespawnRock()
                complete()
            } else {
                delay = getMiningDelay()
            }
        }
    }

    override fun animation(): Animation {
        mob.playSound(Sounds.MINE_ROCK)
        return pick.animation
    }

    override fun add(): List<Item> = if (executions == 0) emptyList() else listOf(Item(ore.item))

    override fun onFinished() = mob.animation(Animation.CANCEL)

    /**
     * Deletes and respawns an [Ore] rock object.
     */
    private fun deleteAndRespawnRock() {
        val emptyId = Ore.ORE_TO_EMPTY[rockObj.id]
        if (emptyId != null) {
            world.addObject(emptyId, rockObj.position, rockObj.objectType, rockObj.direction, null)
            val respawn = ore.respawnTicks
            if (respawn != null) {
                world.scheduleOnce(respawn) {
                    world.addObject(rockObj.id, rockObj.position, rockObj.objectType, rockObj.direction, null)
                }
            }
        }
    }

    /**
     * Computes the mining delay.
     */
    private fun getMiningDelay(): Int {
        var baseTime = rand(BASE_MINE_RATE / 2, BASE_MINE_RATE)
        baseTime -= (pick.strength - ore.resistance)
        if (mob.mining.level > ore.level) {
            val oreLvlFactor = ore.level / 9
            val miningLvlFactor = mob.mining.level / 8
            baseTime -= (miningLvlFactor - oreLvlFactor)
        }
        if (baseTime < 1) {
            baseTime = 1
        }
        if (baseTime > BASE_MINE_RATE) {
            baseTime = BASE_MINE_RATE
        }
        return baseTime
    }

    /**
     * Drops a random gem from the [Ore] rock.
     */
    private fun dropGem() {
        var foundGem: Gem? = null
        var chance = 2
        for (gem in GEMS) {
            if (rand().nextInt(chance) == 0) {
                foundGem = gem
            } else {
                chance += 2
            }
        }
        if (foundGem == null) {
            foundGem = GEMS.first()
        }
        mob.sendMessage("You find a gem in the rock.")
        mob.inventory.add(Item(foundGem.uncut))
    }
}