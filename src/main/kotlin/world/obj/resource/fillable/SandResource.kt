package world.obj.resource.fillable

import api.predef.*
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player

/**
 * A [FillableResource] implementation for sand.
 *
 * @author lare96
 */
object SandResource : FillableResource() {

    override fun isResource(obj: GameObjectDefinition): Boolean {
        return when (obj.name) {
            "Sand pit", "Sandpit", "Sand pile" -> true
            else -> false
        }
    }

    override fun registerResource(obj: GameObjectDefinition) {
        useItem(1925).onObject(obj.id) { fill(plr, usedItemId, gameObject) }
    }

    override fun getFilled(empty: Int): Int? =
        when (empty) {
            1925 -> 1783 // Empty bucket -> Bucket of sand
            else -> null
        }

    override fun onFill(plr: Player) = plr.animation(Animation(832))

}