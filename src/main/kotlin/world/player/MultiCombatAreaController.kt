package world.player

import com.google.common.collect.ImmutableSet
import io.luna.game.model.Area
import io.luna.game.model.Location
import io.luna.game.model.Region
import io.luna.game.model.chunk.Chunk
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerLocationController
import io.luna.net.msg.out.MultiCombatMessageWriter

/**
 * A [PlayerLocationController] implementation for multi-combat areas.
 *
 * @author lare96 
 */
object MultiCombatAreaController : PlayerLocationController() {

    override fun canEnter(plr: Player): Boolean {
        plr.queue(MultiCombatMessageWriter(true))
        return true
    }

    override fun canExit(plr: Player): Boolean {
        plr.queue(MultiCombatMessageWriter(false))
        return true
    }

    override fun computeLocations(): ImmutableSet<Location> = ImmutableSet.of(
            // Pest control arena
            Region(10536),

            // Ape Atoll
            Region(11051),
            Region(11050),
            Region(10794),
            Region(10795),

            // Bandit camp
            Region(12590),

            // Wizards' tower
            Region(12337),

            // Al-kharid
            Region(13105),

            //Burgh de rott
            Region(14129),
            Area.of(3511, 3233, 3519, 3248),

            // Mort myre swamp - The hollows
            Region(13876),

            // Morytania - East

            // Jail - East of Draynor Village
            Area.of(3106, 3233, 3133, 3261),

            // White wolf mountain
            Region(11318),

            // Castle wars
            Region(9520),

            // Burthorpe
            Area.of(2880, 3524, 2904, 3544),

            // Piscatoris fishing colony
            Region(9273),

            // Jiggig
            Area.of(2451, 3032, 2492, 3060),

            // Arandar gate
            Chunk(291, 409),
            Chunk(290, 408),

            // Battlefield
            Region(10034),

            // Grand tree - North
            Region(9783),

            // Keldagrim entrance
            Region(10554),
            Area.of(2688, 3711, 2734, 3741),

            // Ranging guild
            Area.of(2650, 3409, 2686, 3446),

            // Necromancer
            Area.of(2650, 3409, 2686, 3446),
            Area.of(2658, 3220, 2682, 3255),

            // Falador
            Region(11828),
            Region(11829),
            Area.of(2944, 3307, 3007, 3327),
            Area.of(3008, 3299, 3020, 3327),
            Area.of(3021, 3297, 3043, 3313),

            // Chaos temple
            Area.of(2929, 3512, 2941, 3519),

            // Barbarian village
            Region(12341),
            Area.of(3050, 3401, 3071, 3447),

            // South of port phasmatys
            Area.of(3681, 3433, 3711, 3455),

            // South-west of port phasmatys, next to the graveyard
            Region(14389),

            // Wilderness
            Area.of(3008, 3856, 3045, 3902), // Lava maze
            Area.of(3008, 3890, 3071, 3903), // Lava maze
            Region(12604),  // Lava dragon isle NW
            Region(12860), // Lava dragon isle NE
            Region(12603), // Lava dragon isle SW
            Region(12859), // Lava dragon isle SE
            Region(13116), // Demonic ruins and bear
            Region(13115), // ^ but south
            Region(13372),  // Fountain of rune
            Region(13373), // ^ but north
            Area.of(2944, 3814, 2959, 3826), // Chaos temple
            Region(12089), // Bandit camp
            Region(12088), // Dark warriors' fortress
            Region(13117), // Rogues Castle
            Region(12861), // Scorpion pit
            Region(12858), // Bone yard
            Region(13114), // ^ but east
            Area.of(2980, 3911, 3006, 3924), // Frozen waste plateau
            Region(12856), // Chaos temple
            Region(12857), // ^ but north
            Region(13113), // ^ but north-east
            Region(13112), // ^ but east
            Region(12600), // ^ but west
            Region(12599), // Early wilderness, north of varrock
            Region(12855), // ^
            Region(13111), // ^
            Region(13117), // Rogues Castle
            Region(12861) // Scorpion pit
    )
}