package world.player.item.fill

import io.luna.game.model.def.ObjectDefinition
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player

/**
 * A [Resource] implementation for cow's milk.
 *
 * @author lare96
 */
object MilkResource : Resource() {

    override fun matches(obj: ObjectDefinition): Boolean {
        return when (obj.name) {
            "Dairy Cow" -> true
            else -> false
        }
    }

    override fun getFilled(empty: Int): Int? =
        when (empty) {
            1925 -> 1927 // Empty bucket -> Bucket of milk
            else -> null
        }

    override fun onFill(plr: Player) = plr.animation(Animation(2292))
}