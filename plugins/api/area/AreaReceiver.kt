package api.area

/**
 * A receiver for registering area implementations.
 *
 * @author lare96
 */
open class AreaReceiver {

    /**
     * The south-west x coordinate.
     */
    var swX: Int? = null

    /**
     * The south-west y coordinate.
     */
    var swY: Int? = null

    /**
     * The north-east x coordinate.
     */
    var neX: Int? = null

    /**
     * The north-east y coordinate.
     */
    var neY: Int? = null

    /**
     * Validates all the properties to ensure they're defined.
     */
    fun validate() {
        require(swX != null) { "'southWestX' property not defined." }
        require(swY != null) { "'southWestY' property not defined." }
        require(neX != null) { "'northWestX' property not defined." }
        require(neY != null) { "'northEastY' property not defined." }
    }
}