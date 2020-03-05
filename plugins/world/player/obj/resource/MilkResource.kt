package world.player.obj.resource

import api.predef.*
import io.luna.game.model.def.ObjectDefinition
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player

/**
 * A [Resource] implementation for cow's milk.
 *
 * @author lare96
 */
object MilkResource : Resource() {

    override fun matches(obj: ObjectDefinition) =
        when (obj.name) {
            "Dairy Cow" -> true
            else -> false
        }


    override fun getFilled(empty: Int): Int? =
        when (empty) {
            1925 -> 1927 // Empty bucket -> Bucket of milk
            else -> null
        }

    override fun onFill(plr: Player) = plr.animation(Animation(2292))

    override fun onMatch(obj: ObjectDefinition) {
        object1(obj.id) {
            if (plr.inventory.contains(1925)) {
                fill(plr, 1925)
            }
        }
    }
}