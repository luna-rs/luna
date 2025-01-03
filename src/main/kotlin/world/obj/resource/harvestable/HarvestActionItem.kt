package world.obj.resource.harvestable

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.EntityState
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.util.RandomUtils
import java.util.*

/**
 * An [InventoryAction] that harvests based on the [HarvestableResource] type.
 */
class HarvestActionItem(plr: Player, val gameObject: GameObject, val resource: HarvestableResource) :
    InventoryAction(plr, true, 2, Int.MAX_VALUE) {

    override fun execute() {
        if (gameObject.state == EntityState.INACTIVE) {
            interrupt()
            return
        }

        mob.animation(Animation(827))
        val chance = resource.computeExhaustionChance()
        if (RandomUtils.rollSuccess(chance)) {
            interrupt()
            world.removeObject(gameObject)

            // Schedule the resource for respawning.
            val ticks = resource.computeRespawnTicks()
            world.scheduleOnce(ticks.random()) {
                world.addObject(gameObject.id, gameObject.position, ObjectType.DEFAULT, ObjectDirection.SOUTH)
            }
        }
    }

    override fun add() = arrayListOf(resource.computeHarvest())

    override fun ignoreIf(other: Action<*>): Boolean {
        return when (other) {
            is HarvestActionItem -> gameObject == other.gameObject
                    && resource == other.resource

            else -> false
        }
    }
}