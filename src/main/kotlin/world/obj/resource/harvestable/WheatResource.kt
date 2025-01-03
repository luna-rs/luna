package world.obj.resource.harvestable

import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.item.Item

/**
 * A [HarvestableResource] representing wheat.
 */
object WheatResource : HarvestableResource() {

    override fun computeHarvest() = Item(1947)
    override fun computeRespawnTicks(): IntRange = 20..20

    override fun isResource(obj: GameObjectDefinition): Boolean =
        obj.name.equals("Wheat") && obj.actions.contains("Pick")
}