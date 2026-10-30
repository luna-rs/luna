package game.content.darkness

import io.luna.game.model.Position
import io.luna.game.model.area.Area

/**
 * A place too dark to see in without a light.
 *
 * @param darkness How dark it is, from 1 to 3. Each point of light lifts one.
 * @param areas Where it is.
 * @author TheLining
 */
enum class DarkArea(val darkness: Int, vararg val areas: Area) {
    // TODO Swamp gas, which makes naked flames flare up and explode.
    LUMBRIDGE_SWAMP_CAVES(darkness = 3, Area.of(3145, 9540, 3258, 9600)),
    CAVE_GOBLIN_MINE(darkness = 3, Area.of(3221, 9602, 3325, 9657)),
    GENIE_CAVE(darkness = 3, Area.of(3369, 9299, 3377, 9312));

    companion object {

        /**
         * The darkness that stops players seeing to fight, where insects bite anyone without a light.
         */
        const val PITCH_DARK = 3

        /**
         * The dark area at [position], if any.
         */
        fun forPosition(position: Position): DarkArea? = entries.firstOrNull { area ->
            area.areas.any { it.contains(position) }
        }
    }
}
