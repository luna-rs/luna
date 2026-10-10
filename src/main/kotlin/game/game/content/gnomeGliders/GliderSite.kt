package game.content.gnomeGliders

import io.luna.game.model.Position

/**
 * The Gnome Air glider sites. Every flight starts or ends at [TA_QUIR_PRIW].
 *
 * @property buttons The glider map buttons that pick this site.
 * @property landing Where players land at this site.
 * @property outbound The flight from Ta Quir Priw to this site, or `null` if there isn't one.
 * @property inbound The flight from this site to Ta Quir Priw, or `null` if there isn't one.
 * @author TheLining
 */
enum class GliderSite(val buttons: List<Int>, val landing: Position, val outbound: Int?, val inbound: Int?) {
    TA_QUIR_PRIW(buttons = listOf(825),
                 landing = Position(2465, 3501, 3),
                 outbound = null,
                 inbound = null),
    SINDARPOS(buttons = listOf(826),
              landing = Position(2850, 3497),
              outbound = 1,
              inbound = 2),

    /**
     * The Digsite glider crash-landed, so it can't fly back.
     */
    LEMANTO_ANDRA(buttons = listOf(827),
                  landing = Position(3320, 3430),
                  outbound = 3,
                  inbound = null),
    KAR_HEWO(buttons = listOf(828),
             landing = Position(3284, 3211),
             outbound = 4,
             inbound = 5),
    GANDIUS(buttons = listOf(824),
            landing = Position(2971, 2969),
            outbound = 7,
            inbound = 6),

    /**
     * Its hover highlight holds a second clickable copy of the button (12340).
     */
    LEMANTOLLY_UNDRI(buttons = listOf(12342, 12340),
                     landing = Position(2549, 2971),
                     outbound = 10,
                     inbound = 11);

    companion object {

        /**
         * How far from a site's landing tile its pilot spawns.
         */
        private const val PILOT_RANGE = 8

        /**
         * The site of a pilot who spawns at [spawn], or `null` if there's none.
         */
        fun near(spawn: Position): GliderSite? = values().firstOrNull {
            it.landing.isWithinDistance(spawn, PILOT_RANGE)
        }
    }
}
