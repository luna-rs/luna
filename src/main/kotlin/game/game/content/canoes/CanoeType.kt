package game.content.canoes

/**
 * The canoes that can be shaped at a canoe station.
 *
 * @property level The Woodcutting level needed to shape this canoe.
 * @property exp The Woodcutting experience for shaping this canoe.
 * @property range How many stations along the river this canoe can paddle. Only a waka also reaches the Wilderness.
 * @property button The button on the shaping interface that picks this canoe.
 * @property unlocked The shaping interface layer that offers this canoe, or `null` if it's always shown.
 * @property locked The shaping interface layer that shows this canoe as out of reach, or `null` if there isn't one.
 * @property shapedState The station state for this canoe lying on the bank.
 * @property floatingState The station state for this canoe sliding into the water.
 * @property afloatState The station state for this canoe waiting in the water.
 * @property sinking The object for this canoe sinking after a journey.
 * @author TheLining
 */
enum class CanoeType(val level: Int,
                     val exp: Double,
                     val range: Int,
                     val button: Int,
                     val unlocked: Int?,
                     val locked: Int?,
                     val shapedState: Int,
                     val floatingState: Int,
                     val afloatState: Int,
                     val sinking: Int) {
    LOG(level = 12,
        exp = 30.0,
        range = 1,
        button = 18204,
        unlocked = null,
        locked = null,
        shapedState = 1,
        floatingState = 5,
        afloatState = 11,
        sinking = 12159),
    DUGOUT(level = 27,
           exp = 60.0,
           range = 2,
           button = 18205,
           unlocked = 18185,
           locked = 18212,
           shapedState = 2,
           floatingState = 6,
           afloatState = 12,
           sinking = 12160),
    STABLE_DUGOUT(level = 42,
                  exp = 90.0,
                  range = 3,
                  button = 18206,
                  unlocked = 18182,
                  locked = 18215,
                  shapedState = 3,
                  floatingState = 7,
                  afloatState = 13,
                  sinking = 12161),
    WAKA(level = 57,
         exp = 150.0,
         range = 3,
         button = 18207,
         unlocked = 18193,
         locked = 18209,
         shapedState = 4,
         floatingState = 8,
         afloatState = 14,
         sinking = 12162);

    companion object {

        /**
         * The canoe that a station in [state] holds, or `null` if it holds none.
         */
        fun of(state: Int): CanoeType? = values().firstOrNull {
            state == it.shapedState || state == it.floatingState || state == it.afloatState
        }
    }
}
