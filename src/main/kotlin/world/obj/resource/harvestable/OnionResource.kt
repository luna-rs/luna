package world.obj.resource.harvestable

import api.predef.*
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.item.Item

/**
 * A [HarvestableResource] representing onion.
 */
object OnionResource : HarvestableResource() {

    override fun computeHarvest() = if (rand().nextInt(128) == 0) Item(5319) else Item(1957)
    override fun computeRespawnTicks(): IntRange = 50..50

    override fun isResource(obj: GameObjectDefinition): Boolean =
        obj.name.equals("Onion") && obj.actions.contains("Pick")
}