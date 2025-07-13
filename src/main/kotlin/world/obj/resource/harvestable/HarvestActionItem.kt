package world.obj.resource.harvestable

import api.predef.ext.addObject
import api.predef.ext.removeObject
import api.predef.ext.scheduleOnce
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject
import io.luna.util.RandomUtils

/**
 * An [InventoryAction] that harvests based on the [HarvestableResource] type.
 */
class HarvestActionItem(plr: Player, val gameObject: GameObject, val resource: HarvestableResource) :
    InventoryAction(plr, true, 2, Int.MAX_VALUE) {

    override fun execute() {
        if (gameObject.state == EntityState.INACTIVE) {
            complete()
            return
        }

        mob.animation(Animation(827))
        val chance = resource.computeExhaustionChance()
        if (RandomUtils.rollSuccess(chance)) {
            complete()
            world.removeObject(gameObject)

            // Schedule the resource for respawning.
            val ticks = resource.computeRespawnTicks()
            world.scheduleOnce(ticks.random()) {
                world.addObject(gameObject.id, gameObject.position, gameObject.objectType, gameObject.direction)
            }
        }
    }

    override fun add() = arrayListOf(resource.computeHarvest())
}