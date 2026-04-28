package game.obj.resource.harvestable

import api.predef.ext.*
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.item.Item

/**
 * A [HarvestableResource] representing flax.
 *
 * @author lare96
 */
object FlaxResource : HarvestableResource() {

    override fun computeHarvest() = Item(1779)

    override fun computeRespawnTicks(): IntRange = 10..10

    override fun computeExhaustionChance(): Double = 3 of 16

    override fun isResource(obj: GameObjectDefinition): Boolean =
        obj.name.equals("Flax") && obj.actions.contains("Pick")
}