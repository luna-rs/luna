package game.content.seasonalEvents

import api.drops.DropTableHandler
import api.drops.DropTableItemList
import api.predef.ALWAYS
import api.predef.ext.of
import java.time.Month
import java.time.Year

/**
 * Defines the calendar month and reward entries for each seasonal event.
 *
 * Easter's month is calculated for the current year when [month] is invoked. Christmas and Halloween use fixed months.
 *
 * Reward chances are passed to [SeasonalEventDropTable], which controls how the entries are selected. The
 * nominal `1 of N` values depend on that implementation.
 *
 * @property title The event's display name.
 * @property month Supplies the event's month.
 * @property items The event's reward entries and their chance values.
 *
 * @author lare96
 */
enum class SeasonalEvent(
    val title: String,
    val month: () -> Month,
    val items: DropTableItemList
) {

    /**
     * Easter rewards, available during the month containing Gregorian Easter.
     */
    EASTER(
        title = "Easter",
        month = {
            val year = Year.now().value
            val a = year % 19
            val b = year / 100
            val c = year % 100
            val d = b / 4
            val e = b % 4
            val f = (b + 8) / 25
            val g = (b - f + 1) / 3
            val h = (19 * a + b - d - g + 15) % 30
            val i = c / 4
            val k = c % 4
            val l = (32 + 2 * e + 2 * i - h - k) % 7
            val m = (a + 11 * h + 22 * l) / 451
            val month = (h + l - 7 * m + 114) / 31
            Month.of(month)
        },
        items = DropTableHandler.createList {
            1961 x 1 chance ALWAYS // Easter egg

            1037 x 1 chance (1 of 2) // Bunny ears
            4565 x 1 chance (1 of 4) // Basket of eggs
            4566 x 1 chance (1 of 8) // Rubber chicken
            7927 x 1 chance (1 of 16) // Easter ring
        }
    ),

    /**
     * December rewards, including three distinct marionette colours with equal chances.
     */
    CHRISTMAS(
        title = "Christmas",
        month = { Month.DECEMBER },
        items = DropTableHandler.createList {
            962 x 1 chance (1 of 3) // Christmas cracker
            4079 x 1 chance (1 of 4) // Yo-yo
            1050 x 1 chance (1 of 6) // Santa hat
            6856 x 1 chance (1 of 8) // Bobble hat
            6857 x 1 chance (1 of 8) // Bobble scarf
            6858 x 1 chance (1 of 8) // Jester hat
            6859 x 1 chance (1 of 8) // Jester scarf
            6860 x 1 chance (1 of 8) // Tri-jester hat
            6861 x 1 chance (1 of 8) // Tri-jester scarf
            6862 x 1 chance (1 of 8) // Woolly hat
            6863 x 1 chance (1 of 8) // Woolly scarf
            6865 x 1 chance (1 of 10) // Blue marionette
            6866 x 1 chance (1 of 10) // Green marionette
            6867 x 1 chance (1 of 10) // Red marionette
        }
    ),

    /**
     * October rewards, with pumpkins and sweets identified as consumables.
     */
    HALLOWEEN(
        title = "Halloween",
        month = { Month.OCTOBER },
        items = DropTableHandler.createList {
            1959 x 1 chance ALWAYS // Pumpkin
            4558 x 1 chance ALWAYS // Blue sweets
            4559 x 1 chance ALWAYS // Deep blue sweets
            4560 x 1 chance ALWAYS // White sweets
            4561 x 1 chance ALWAYS // Purple sweets
            4562 x 1 chance ALWAYS // Red sweets
            4563 x 1 chance ALWAYS // Green sweets
            4564 x 1 chance ALWAYS // Pink sweets

            1053 x 1 chance (1 of 2) // Green h'ween mask
            1057 x 1 chance (1 of 4) // Red h'ween mask
            1055 x 1 chance (1 of 8) // Blue h'ween mask
            1419 x 1 chance (1 of 8) // Scythe
            6722 x 1 chance (1 of 16) // Zombie head
        }
    );

    companion object {

        /**
         * All consumables dropped by all event types.
         */
        val CONSUMABLES = entries.flatMap { it.consumables }.toSet()
    }

    /**
     * The specialized drop table constructed from this event's reward entries.
     */
    val table =
        DropTableHandler.create {
            items += this@SeasonalEvent.items
        }.table {
            SeasonalEventDropTable(items)
        }

    /**
     * The reward IDs marked with [ALWAYS], used to identify this event's consumables. Their selection during a
     * roll is controlled by [SeasonalEventDropTable].
     */
    val consumables: Set<Int> =
        items.filter { it.chance == ALWAYS }.mapTo(HashSet()) { it.id }
}