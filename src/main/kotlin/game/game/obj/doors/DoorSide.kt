package game.obj.doors

/**
 * Which leaf of a double door or gate an object is. The side decides which way the leaf swings and where its partner
 * stands. In a gate, the [LEFT] leaf is the hinge that the whole fence swings around.
 *
 * @author Hydrozoa
 */
enum class DoorSide {
    LEFT,
    RIGHT
}
