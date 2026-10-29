package engine.obj

import engine.obj.TrapdoorLanding.Companion.BELOW
import engine.obj.TrapdoorLanding.Companion.byTrapdoor
import engine.obj.TrapdoorLanding.Companion.tile
import io.luna.game.model.Position

/**
 * Manholes, which are opened and climbed down like trapdoors but with their own messages and covers. They're handled in
 * trapdoors.kts.
 *
 * @author TheLining
 */
enum class Manhole(override val closed: Int?, override val open: Int, override val landing: TrapdoorLanding) : Hatch {

    // Into the Varrock and Ardougne sewers. Opening one puts its cover beside it.
    SEWERS(closed = 881, open = 882, landing = BELOW),

    // Into the Port Sarim and Varrock rat pits, which are left open.
    RAT_PITS(closed = null, open = 10321,
             landing = byTrapdoor(BELOW, Position(3018, 3232) to tile(2962, 9650),
                                  Position(3267, 3400) to tile(2894, 5097))),

    // West Ardougne, into the sewer pipe (Plague City). Opening it puts its cover beside it.
    PLAGUE_CITY(closed = 2543, open = 2544, landing = tile(2514, 9739));
}
