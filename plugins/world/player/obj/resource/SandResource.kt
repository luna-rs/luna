package world.player.obj.resource

import io.luna.game.model.def.ObjectDefinition
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player

/**
 * A [Resource] implementation for sand.
 *
 * @author lare96
 */
object SandResource : Resource() {

    override fun matches(obj: ObjectDefinition): Boolean {
        return when (obj.name) {
            "Sand pit", "Sandpit", "Sand pile" -> true
            else -> false
        }
    }

    override fun getFilled(empty: Int): Int? =
        when (empty) {
            1925 -> 1783 // Empty bucket -> Bucket of sand
            else -> null
        }

    override fun onFill(plr: Player) = plr.animation(Animation(832))
}