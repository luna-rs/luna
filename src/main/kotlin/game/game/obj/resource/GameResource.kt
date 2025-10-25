package game.obj.resource

import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.`object`.GameObject

/**
 * Represents a regenerative [GameObject] based resource that can be interacted with by the player.
 *
 * @author lare96
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