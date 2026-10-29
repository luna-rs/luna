package engine.obj

/**
 * A [Trapdoor] or [Manhole]: opened if it's closed, then climbed down.
 *
 * @author TheLining
 */
interface Hatch {

    /**
     * The closed hatch, which opens into [open]. `null` if it's always open.
     */
    val closed: Int?

    /**
     * The open hatch, which is climbed down.
     */
    val open: Int

    /**
     * Where climbing down takes the player.
     */
    val landing: TrapdoorLanding

    companion object {

        /**
         * Every trapdoor and manhole.
         */
        val ALL: List<Hatch> = Trapdoor.entries + Manhole.entries

        /**
         * Returns the hatch that [id] is the closed or open object of, or `null` if there isn't one.
         */
        fun forObject(id: Int): Hatch? = ALL.firstOrNull { it.closed == id || it.open == id }
    }
}
