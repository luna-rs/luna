package game.content.darkness

import io.luna.game.model.mob.overlay.WalkableInterface

/**
 * The shade over a player's screen in a dark place: [DARKEST] for darkness 3 down to [LIGHTEST] for darkness 1.
 *
 * @author TheLining
 */
class DarknessOverlay(id: Int) : WalkableInterface(id) {

    companion object {

        /**
         * The overlays for darkness 1 to 3.
         */
        const val LIGHTEST = 12416
        const val MEDIUM = 12418
        const val DARKEST = 12414

        /**
         * The overlay for [darkness], or `null` when there's nothing to cover.
         */
        fun forDarkness(darkness: Int): Int? = when {
            darkness >= DarkArea.PITCH_DARK -> DARKEST
            darkness == 2 -> MEDIUM
            darkness == 1 -> LIGHTEST
            else -> null
        }
    }
}
