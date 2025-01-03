package world.obj.resource.harvestable

import api.predef.*
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.item.Item

/**
 * A [HarvestableResource] representing potato.
 */
object PotatoResource : HarvestableResource() {

    override fun computeHarvest() = if (rand().nextInt(128) == 0) Item(5318) else Item(1942)
    override fun computeRespawnTicks(): IntRange = 50..50

    override fun isResource(obj: GameObjectDefinition): Boolean =
        obj.name.equals("Potato") && obj.actions.contains("Pick")
}