package world.obj.resource.fillable

import api.predef.*
import com.google.common.collect.HashBiMap
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player

/**
 * A [FillableResource] implementation for water.
 *
 * @author lare96
 */
object WaterResource : FillableResource() {

    /**
     * A [HashBiMap] of all empty container ids and their filled counterpart ids.
     */
    val FILLABLES = HashBiMap.create(mapOf(
            1923 to 1921, // Bowl -> Bowl of water
            229 to 227, // Vial -> Vial of water
            1925 to 1929, // Bucket -> Bucket of water
            1980 to 4458, // Cup -> Cup of water
            1935 to 1937 // Jug -> Jug of water
    ))

    /**
     * A list of just filled container ids.
     */
    val FILLED_IDS = FILLABLES.values.map { it }

    /**
     * A list of just empty container ids.
     */
    val EMPTY_IDS = FILLABLES.keys.map { it }

    /**
     * The names of all water resources.
     */
    private val NAMES = setOf("Fountain", "Well", "Pump", "Waterpump", "Water Barrel", "Water", "Sink")

    override fun isResource(obj: GameObjectDefinition): Boolean = NAMES.contains(obj.name)

    override fun registerResource(obj: GameObjectDefinition) {
        for (emptyId in EMPTY_IDS) {
            useItem(emptyId).onObject(obj.id) { fill(plr, emptyId, gameObject) }
        }
    }

    override fun getFilled(empty: Int): Int? = FILLABLES[empty]

    override fun onFill(plr: Player) = plr.animation(Animation(832))
}