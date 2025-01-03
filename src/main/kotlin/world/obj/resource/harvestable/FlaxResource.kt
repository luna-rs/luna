package world.obj.resource.harvestable

import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.item.Item
import io.luna.util.Rational

/**
 * A [HarvestableResource] representing flax.
 */
object FlaxResource : HarvestableResource() {

    override fun computeHarvest() = Item(1779)

    override fun computeRespawnTicks(): IntRange = 10..10

    override fun computeExhaustionChance(): Rational = Rational(3, 16)

    override fun isResource(obj: GameObjectDefinition): Boolean =
        obj.name.equals("Flax") && obj.actions.contains("Pick")
}