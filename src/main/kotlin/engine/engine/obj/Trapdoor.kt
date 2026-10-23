package engine.obj

import engine.obj.TrapdoorLanding.Companion.BELOW
import engine.obj.TrapdoorLanding.Companion.byTrapdoor
import engine.obj.TrapdoorLanding.Companion.floorsDown
import engine.obj.TrapdoorLanding.Companion.tile
import io.luna.game.model.Position

/**
 * Trapdoors that are opened, climbed down and closed the usual way. Trapdoors that work differently (manholes and
 * some quest trapdoors) are handled in trapdoors.kts instead.
 *
 * Quest and item requirements aren't checked because Luna doesn't have that content yet. Trapdoors added after 2004
 * land on the tile the ladder back up is climbed from.
 *
 * @param closed The closed trapdoor, which opens into [open]. `null` if it's always open.
 * @param open The open trapdoor, which is climbed down and, if it has the option, closed again.
 * @param landing Where climbing down takes the player.
 *
 * @author TheLining
 */
enum class Trapdoor(val closed: Int?, val open: Int, val landing: TrapdoorLanding) {

    // Trapdoors into the cellar below, such as Edgeville dungeon's.
    CELLAR(closed = 1568, open = 1570, landing = BELOW),

    // Paterdomus, into the temple's basement (Priest in Peril).
    PATERDOMUS(closed = 3432, open = 3433, landing = tile(3440, 9887)),

    // Lumbridge Castle cellar.
    LUMBRIDGE_CASTLE_CELLAR(closed = 14879, open = 14880, landing = tile(3209, 9617)),

    // Port Sarim, into the Asgarnian Ice Dungeon.
    PORT_SARIM(closed = null, open = 9472, landing = tile(3007, 9549)),

    // Falador, into the Dwarven Mine.
    DWARVEN_MINE(closed = null, open = 11867, landing = tile(3019, 9848)),

    // Burthorpe, into the Rogues' Den.
    ROGUES_DEN(closed = null, open = 7257, landing = tile(3061, 4984, 1)),

    // South of the Ranging Guild, into the Temple of Ikov.
    TEMPLE_OF_IKOV(closed = null, open = 6278, landing = BELOW),

    // Draynor Village sewers. The southern trapdoor lands by the ladder back up.
    DRAYNOR_SEWERS(closed = 6434, open = 6435,
                   landing = byTrapdoor(BELOW, Position(3118, 3244) to tile(3118, 9644))),

    // Canifis, into the Werewolf Agility Course.
    WEREWOLF_AGILITY_COURSE(closed = 5131, open = 5132, landing = tile(3549, 9865)),

    // Port Phasmatys, into the Ectofuntus basement.
    ECTOFUNTUS(closed = 5267, open = 5268, landing = tile(3669, 9888, 3)),

    // Port Phasmatys, into the brewery's cellar.
    PHASMATYS_BREWERY(closed = 7434, open = 7435, landing = tile(3682, 9961)),

    // Champions' Guild, into the Champions' Challenge.
    CHAMPIONS_GUILD(closed = 10558, open = 10559, landing = tile(3189, 9758)),

    // Seers' Village, down from the seer's roof (One Small Favour).
    SEERS_ROOF(closed = null, open = 5835, landing = floorsDown(2)),

    // Port Sarim, down from the jail's roof.
    PORT_SARIM_JAIL_ROOF(closed = null, open = 9560, landing = floorsDown(1)),

    // Ape Atoll, down from the corners of the jungle demon's lair to the banana plantation below (Monkey Madness).
    JUNGLE_DEMON_LAIR_NW(closed = 5799, open = 5803, landing = tile(2696, 9210)),
    JUNGLE_DEMON_LAIR_NE(closed = 5800, open = 5804, landing = tile(2739, 9205)),
    JUNGLE_DEMON_LAIR_SW(closed = 5801, open = 5805, landing = tile(2692, 9163)),
    JUNGLE_DEMON_LAIR_SE(closed = 5802, open = 5806, landing = tile(2736, 9161));
}
