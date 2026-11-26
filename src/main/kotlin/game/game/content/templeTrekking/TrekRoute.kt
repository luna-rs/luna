package game.content.templeTrekking

import io.luna.game.model.Position

/**
 * When a fight can be evaded on a route.
 */
enum class EvadeRule {

    /**
     * Any time.
     */
    ANY,

    /**
     * Once half of the monsters, rounded up, are dead.
     */
    HALF,

    /**
     * Never.
     */
    NEVER
}

/**
 * A place on the way where an event can only be of one kind.
 */
enum class TrekSpot {

    /**
     * Mort'ton, where the shades are.
     */
    MORTTON,

    /**
     * The boat trip on the hard route, where the tentacle beast is.
     */
    BOAT
}

/**
 * One leg of a route on the trek map.
 *
 * @property trip The animation moving the pawns along the leg.
 * @property ticks How long the trip animation plays.
 * @property hold The animation holding the pawns where the leg ends, or -1 for the last leg.
 * @property marker The varbit 1958 value that flashes the leg's "!" when an event happens there, or 0.
 * @property spot The kind of place the leg ends at, if it decides the event.
 */
class TrekLeg(val trip: Int, val ticks: Int, val hold: Int, val marker: Int, val spot: TrekSpot? = null) {

    /**
     * Whether an event can happen where this leg ends.
     */
    val eventful get() = marker > 0
}

/**
 * The three routes from Burgh de Rott to Paterdomus.
 *
 * @property button The route's button on the route map.
 * @property token The reward token for finishing the route.
 * @property killPoints Trek points for each monster killed on the route.
 * @property evade When fights can be evaded.
 * @property events How many events a trek on the route has, each count weighted.
 * @property legs The legs of the route on the map.
 * @property escapes Where an escaped or failed trek leaves the player, from the start of the route to its end.
 */
enum class TrekRoute(val button: Int,
                     val token: Int,
                     val killPoints: Int,
                     val evade: EvadeRule,
                     val events: Map<Int, Int>,
                     val legs: List<TrekLeg>,
                     val escapes: List<Position>) {

    // The swamp: the long way round, through Mort Myre.
    ROUTE_ONE(18607, TempleTrekking.BLUE_TOKEN, 325, EvadeRule.ANY,
              mapOf(0 to 1, 1 to 2, 2 to 2),
              listOf(TrekLeg(3749, 2, 3778, 1, TrekSpot.MORTTON),
                     TrekLeg(3750, 3, 3779, 2),
                     TrekLeg(3751, 2, 3780, 3),
                     TrekLeg(3752, 2, 3781, 4),
                     TrekLeg(3753, 2, 3782, 5),
                     TrekLeg(3754, 3, 3783, 6),
                     TrekLeg(3755, 2, 3784, 7),
                     TrekLeg(3756, 2, 3785, 8),
                     TrekLeg(3757, 2, 3786, 9),
                     TrekLeg(3758, 2, 3787, 10),
                     TrekLeg(3759, 3, -1, 0)),
              listOf(Position(3488, 3269), Position(3461, 3350), Position(3478, 3368), Position(3470, 3384),
                     Position(3475, 3394), Position(3453, 3407), Position(3438, 3424))),

    // Along the Salve.
    ROUTE_TWO(18611, TempleTrekking.YELLOW_TOKEN, 625, EvadeRule.HALF,
              mapOf(1 to 1, 2 to 2, 3 to 2, 4 to 1),
              listOf(TrekLeg(3749, 2, 3778, 1, TrekSpot.MORTTON),
                     TrekLeg(3750, 3, 3779, 2),
                     TrekLeg(3751, 2, 3780, 3),
                     TrekLeg(3760, 2, 3788, 11),
                     TrekLeg(3761, 2, 3789, 12),
                     TrekLeg(3762, 2, 3790, 13),
                     TrekLeg(3763, 2, 3791, 14),
                     TrekLeg(3764, 2, 3792, 15),
                     TrekLeg(3765, 2, 3793, 16),
                     TrekLeg(3766, 2, 3787, 10),
                     TrekLeg(3759, 3, -1, 0)),
              listOf(Position(3429, 3295), Position(3425, 3315), Position(3422, 3338), Position(3412, 3353),
                     Position(3412, 3377), Position(3417, 3402), Position(3418, 3437))),

    // Over the river by boat, through the Hollows and Canifis.
    ROUTE_THREE(18615, TempleTrekking.RED_TOKEN, 1125, EvadeRule.NEVER,
                mapOf(1 to 1, 2 to 2, 3 to 4, 4 to 2, 5 to 1),
                listOf(TrekLeg(3749, 2, 3778, 1, TrekSpot.MORTTON),
                       TrekLeg(3767, 3, 3794, 17, TrekSpot.BOAT),
                       TrekLeg(3768, 2, 3795, 18),
                       TrekLeg(3769, 2, 3796, 19),
                       TrekLeg(3770, 2, 3797, 20),
                       TrekLeg(3771, 2, 3798, 21),
                       TrekLeg(3772, 2, 3799, 22),
                       TrekLeg(3773, 2, 3800, 23),
                       TrekLeg(3774, 2, 3801, 24),
                       TrekLeg(3775, 2, 3802, 25),
                       TrekLeg(3776, 3, -1, 0)),
                listOf(Position(3499, 3349), Position(3500, 3366), Position(3488, 3376), Position(3492, 3387),
                       Position(3509, 3396), Position(3530, 3437), Position(3482, 3426)));

    /**
     * The route's level on the reward and tome tables, from 1 to 3.
     */
    val level = ordinal + 1

    /**
     * Where a trek left on [leg] leaves the player.
     */
    fun escape(leg: Int): Position = escapes[(leg.coerceIn(0, legs.size - 1) * escapes.size / legs.size)]

    companion object {

        /**
         * The route for a route-map button.
         */
        fun forButton(id: Int): TrekRoute? = entries.firstOrNull { it.button == id }

        /**
         * The route a token was given for.
         */
        fun forToken(id: Int): TrekRoute? = entries.firstOrNull { it.token == id }
    }
}
