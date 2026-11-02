package game.obj.entrances

import io.luna.game.model.Position

/**
 * Native Killerwatt rift identities and landings shared by player interactions and web-walking.
 * The existing warning choices and insulated-boots behavior remain in the player script.
 *
 * @author lare96
 */
object KillerwattAccess {

    /**
     * Professor Oddenstein presents the rift's native warnings.
     */
    const val PROFESSOR = 286

    /**
     * The laboratory rift and the return portal on the electrical plane.
     */
    const val RIFT = 11355
    const val PORTAL = 11356

    /**
     * The exact cached rift and portal placements, both on the second floor.
     */
    val RIFT_POSITION = Position(3110, 3362, 2)
    val PORTAL_POSITION = Position(2678, 5214, 2)

    /**
     * The native landing anchors beside each object. The native near landing resolves a blocked anchor if necessary.
     */
    val REALM = Position(2677, 5214, 2)
    val LAB = Position(3110, 3363, 2)
    val REALM_LANDING = EntranceLanding.near(REALM.x, REALM.y, REALM.z)
    val LAB_LANDING = EntranceLanding.near(LAB.x, LAB.y, LAB.z)
}
