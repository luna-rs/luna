package game.obj.resource.fillable

import api.predef.*
import game.player.Sound
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject

/**
 * A [FillableResource] implementation for cow's milk.
 *
 * @author lare96
 */
object MilkResource : FillableResource() {

    override fun isResource(obj: GameObjectDefinition) =
        when (obj.name) {
            "Dairy Cow" -> true
            else -> false
        }

    override fun registerResource(obj: GameObjectDefinition) {
        useItem(1925).onObject(obj.id()) { fill(plr, 1925, gameObject) }
        object1(obj.id()) {
            if (plr.inventory.contains(1925)) {
                fill(plr, 1925, gameObject)
            }
        }
    }

    override fun getFilled(empty: Int): Int? =
        when (empty) {
            1925 -> 1927 // Empty bucket -> Bucket of milk
            else -> null
        }

    override fun onFill(plr: Player, resourceObject: GameObject) {
        plr.animation(Animation(2292))
        plr.playSound(Sound.MILK_COW)
    }
}
