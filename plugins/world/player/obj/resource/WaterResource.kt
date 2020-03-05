package world.player.obj.resource

import io.luna.game.model.def.ObjectDefinition
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player

/**
 * A [Resource] implementation for water.
 *
 * @author lare96
 */
object WaterResource : Resource() {

    private val FILLABLES = mapOf(
            1923 to 1921, // Bowl -> Bowl of water
            229 to 227, // Vial -> Vial of water
            1925 to 1929, // Bucket -> Bucket of water
            1980 to 4458, // Cup -> Cup of water
            1935 to 1937 // Jug -> Jug of water
    )

    private val NAMES = setOf("Fountain", "Well", "Pump", "Waterpump", "Water Barrel", "Water", "Sink")

    override fun matches(obj: ObjectDefinition): Boolean = NAMES.contains(obj.name)

    override fun getFilled(empty: Int): Int? = FILLABLES[empty]

    override fun onFill(plr: Player) = plr.animation(Animation(832))
}