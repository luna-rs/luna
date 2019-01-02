package world.player.item.fill

import io.luna.game.model.def.ObjectDefinition
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player

object SandSource : Source() {

    override fun matchesDef(def: ObjectDefinition): Boolean {
        return when (def.name) {
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