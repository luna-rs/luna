package world.player.skill.mining.mineOre

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
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
class MineOreActionItem(plr: Player, val pick: Pickaxe, val ore: Ore, val rockObj: GameObject) :
    InventoryAction(plr, false, 1, rand(RANDOM_FAIL_RATE)) {

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
         * The mining animation delay.
         */
        private val ANIMATION_DELAY = 4

        /**
         * All gems that can be received, in order of lowest level -> highest.
         */
        val GEMS = Gem.values().sortedBy { it.level }
    }

    /**
     * The counter for the mining animation delay.
     */
    private var animationDelay = 0

    override fun executeIf(start: Boolean) = when {
        mob.mining.level < ore.level -> {
            // Check if we have required level.
            mob.sendMessage("You need a Mining level of ${ore.level} to mine this.")
            false
        }

        !Pickaxe.hasPick(mob, pick) -> {
            mob.sendMessage("You need a pickaxe to mine ores.")
            false
        }
        // Check if rock isn't already mined and if we still have a pickaxe.
        rockObj.state == EntityState.INACTIVE || !Pickaxe.hasPick(mob, pick) -> false
        else -> {
            if (start) {
                mob.sendMessage("You swing your pick at the rock.")
                delay = getMiningDelay()
            }
            true
        }
    }

    override fun execute() {
        val hasGlory = mob.equipment.amulet?.itemDef?.name?.contains("Amulet of glory")
        val gemChance = if (hasGlory != null && hasGlory) BOOSTED_GEM_CHANCE else BASE_GEM_CHANCE
        if (!mob.inventory.isFull && rand().nextInt(gemChance) == 0) {
            dropGem()
        } else {
            mob.sendMessage("You manage to mine some ${ore.typeName.toLowerCase()}.")
            mob.mining.addExperience(ore.exp)
        }

        val removeChance = ore.depletionChance
        if (removeChance != null && (removeChance == 1 || rand().nextInt(removeChance) == 0)) {
            mob.playSound(Sounds.MINING_COMPLETED)
            deleteAndRespawnRock()
            interrupt()
        } else {
            delay = getMiningDelay()
        }
    }

    override fun process() {
        if (animationDelay > 0) {
            animationDelay--
            if (animationDelay != 0) {
                mob.playSound(Sounds.MINE_ROCK)
            }
        } else {
            mob.animation(pick.animation)
            animationDelay = ANIMATION_DELAY
        }
    }

    override fun add(): List<Item?> = listOf(Item(ore.item))

    override fun stop() = mob.animation(Animation.CANCEL)

    override fun ignoreIf(other: Action<*>?) =
        when (other) {
            is MineOreActionItem -> rockObj == other.rockObj
            else -> false
        }

    /**
     * Deletes and respawns an [Ore] rock object.
     */
    private fun deleteAndRespawnRock() {
        if (world.removeObject(rockObj)) {
            val emptyId = Ore.ORE_TO_EMPTY[rockObj.id]
            if (emptyId != null) {
                world.addObject(emptyId, rockObj.position, rockObj.objectType, rockObj.direction, null)
            }
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