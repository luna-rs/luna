package world.obj.resource

import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.`object`.GameObject
import world.obj.resource.fillable.MilkResource
import world.obj.resource.fillable.SandResource
import world.obj.resource.fillable.WaterResource
import world.obj.resource.harvestable.CabbageResource
import world.obj.resource.harvestable.FlaxResource
import world.obj.resource.harvestable.OnionResource
import world.obj.resource.harvestable.PotatoResource
import world.obj.resource.harvestable.WheatResource

/**
 * Represents a regenerative [GameObject] based resource that can be interacted with by the player.
 */
abstract class GameResource {

    /**
     * Determines if [obj] matches this resource, if `true` is returned the argued object will be registered as
     * this resource.
     */
    abstract fun isResource(obj: GameObjectDefinition): Boolean

    /**
     * Registers event listeners for [obj] if [isResource] is successful.
     */
    abstract fun registerResource(obj: GameObjectDefinition)

    /**
     * Determine which objects should be registered and handle them.
     */
    fun load() {
        for (obj in GameObjectDefinition.ALL) {
            if (obj != null && isResource(obj)) {
                // Dynamically cached, so we don't have to worry about 'matches' performance.
                registerResource(obj)
            }
        }
    }
}