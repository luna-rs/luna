package world.obj.resource.harvestable

import api.predef.*
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.item.Item

/**
 * A [HarvestableResource] representing cabbage.
 */
object CabbageResource : HarvestableResource() {

    override fun computeHarvest() = if (rand().nextInt(128) == 0) Item(5324) else Item(1965)

    override fun computeRespawnTicks(): IntRange = 40..80

    override fun isResource(obj: GameObjectDefinition): Boolean =
        obj.name.equals("Cabbage") && obj.actions.contains("Pick")
}