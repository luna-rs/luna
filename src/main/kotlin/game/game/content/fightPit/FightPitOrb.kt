package game.content.fightPit

import io.luna.game.model.Position

/**
 * The viewing orb in the middle of the waiting room. Looking into it shows the pit from one of five views, with the
 * orb's sidebar in place of the tabs.
 *
 * @author TheLining
 */
object FightPitOrb {

    /**
     * The orb.
     */
    const val ORB = 9391

    /**
     * The orb's sidebar: five views and Stop Viewing.
     */
    const val SIDEBAR = 3209

    /**
     * The sidebar's Stop Viewing button.
     */
    const val STOP_VIEWING = 4463

    /**
     * The sidebar's view buttons, and the pit tiles each looks from: the centre and the four corners.
     */
    val VIEWS = mapOf(
        15239 to Position(2396, 5148),
        15240 to Position(2381, 5161),
        15241 to Position(2412, 5161),
        15242 to Position(2412, 5135),
        15243 to Position(2381, 5135)
    )

    /**
     * The view a player sees first.
     */
    val CENTRE = VIEWS.getValue(15239)
}
