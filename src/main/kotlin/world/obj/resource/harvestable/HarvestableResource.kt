package world.obj.resource.harvestable

import api.predef.*
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.util.Rational
import world.obj.resource.GameResource

/**
 * A [GameResource] that can be harvested by a player.
 */
abstract class HarvestableResource : GameResource() {

    final override fun registerResource(obj: GameObjectDefinition) {
        object2(obj.id) { harvest(plr, gameObject) }
    }

    /**
     * Determines which item wil be harvested.
     */
    abstract fun computeHarvest(): Item

    /**
     * Determines how many ticks it takes to respawn this resource.
     */
    abstract fun computeRespawnTicks(): IntRange

    /**
     * Determines the chance of the resource object being exhausted.
     */
    open fun computeExhaustionChance(): Rational = Rational.ALWAYS

    /**
     * Harvests this resource for [plr] and schedules a task to respawn it.
     */
    fun harvest(plr: Player, gameObject: GameObject) {
        if (plr.inventory.isFull) {
            plr.sendMessage("Your inventory is full.")
        } else /*if(!gameObject.isHidden)*/ {// TODO after cache loading
            plr.submitAction(HarvestActionItem(plr, gameObject, this))
        }
    }
}