package game.skill.farming.patch

import game.skill.farming.bush.*
import game.skill.farming.special.*

/**
 * A patch whose fully grown crop must have its health checked before it can be picked from, such as a [CactusPatch] or
 * a [BushPatch].
 *
 * @author hydrozoa
 */
interface HealthCheckPatch {

    /**
     * The name of the fully grown crop in the health check message, for example "cactus".
     */
    val checkedName: String

    /**
     * @return True if the fully grown crop is waiting to have its health checked.
     */
    fun canCheckHealth(): Boolean

    /**
     * Checks the health of the fully grown crop.
     */
    fun checkHealth()
}
