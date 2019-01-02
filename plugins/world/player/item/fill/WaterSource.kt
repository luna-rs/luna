package world.player.item.fill

import io.luna.game.model.def.ObjectDefinition
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player

object WaterSource : Source() {


    private val FILLABLES = mapOf(
            1923 to 1921, // Bowl
            229 to 227, // Vial
            1925 to 1929, // Bucket
            1980 to 4458, // Cup
            1935 to 1937 // Jug
    )

    override fun matchesDef(def: ObjectDefinition): Boolean {
        return when (def.name) {
            "Fountain", "Well", "Pump", "Waterpump" -> true
            else -> false
        }
    }

    override fun getFilled(empty: Int): Int? = FILLABLES[empty]

    override fun onFill(plr: Player) = plr.animation(Animation(832))
}