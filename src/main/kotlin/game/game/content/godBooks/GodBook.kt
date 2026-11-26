package game.content.godBooks

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.content.questRewards.QuestRewards
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * The pages in each book.
 */
const val PAGE_COUNT = 4

/**
 * The Saradomin, Zamorak and Guthix prayer books, their torn pages and what preaching from them says.
 *
 * @author TheLining
 */
enum class GodBook(val damaged: Int,
                   val complete: Int,
                   val firstPage: Int,
                   val god: String,
                   val found: String,
                   val passages: List<Pair<String, List<String>>>) {
    SARADOMIN(3839, 3840, 3827, "Saradomin", "It's a Holy Book of Saradomin!",
              listOf("Wedding Ceremony" to listOf("In the name of Saradomin,", "Protector of us all,",
                                                  "I now join you in the eyes of Saradomin."),
                     "Last Rites" to listOf("Thy cause was false, thy skills did lack;",
                                            "See you in Lumbridge when you get back."),
                     "Blessings" to listOf("Go in peace in the name of Saradomin;",
                                           "May his glory shine upon you like the sun."),
                     "Preach" to listOf("The currency of goodness is honour;",
                                        "It retains its value through scarcity.", "This is Saradomin's wisdom."))),
    ZAMORAK(3841, 3842, 3831, "Zamorak", "It's an Unholy Book of Zamorak!",
            listOf("Wedding Ceremony" to listOf("Two great warriors, joined by hand,",
                                                "to spread destruction across the land.",
                                                "In Zamorak's name, now two are one."),
                   "Last Rites" to listOf("The weak deserve to die,", "So the strong may flourish.",
                                          "This is the creed of Zamorak."),
                   "Blessings" to listOf("May your bloodthirst never be sated,",
                                         "and may all your battles be glorious.", "Zamorak bring you strength."),
                   "Preach" to listOf("There is no opinion that cannot be proven true...",
                                      "by crushing those who choose to disagree with it.",
                                      "Zamorak give me strength!"))),
    GUTHIX(3843, 3844, 3835, "Guthix", "It's Guthix's Book of Balance!",
           listOf("Wedding Ceremony" to listOf("Light and dark, day and night,", "Balance arises from contrast.",
                                               "I unify thee in the name of Guthix."),
                  "Last Rites" to listOf("Thy death was not in vain,", "For it brought some balance to the world.",
                                         "May Guthix bring thee rest."),
                  "Blessings" to listOf("Mayest thou walk the path, and never fall,",
                                        "For Guthix walks beside thee on thy journey.",
                                        "May Guthix bring thee peace."),
                  "Preach" to listOf("The trees, the earth, the sky, the waters:",
                                     "All play their part upon this land.", "May Guthix bring thee balance.")));

    /**
     * This book's four torn pages, in page order.
     */
    val pages = (firstPage until firstPage + PAGE_COUNT).toList()

    /**
     * The bit of this book in [givenBooks].
     */
    private val givenBit = 1 shl ordinal

    /**
     * The bits of this book's added pages in [addedPages].
     */
    private val pageShift = ordinal * PAGE_COUNT

    /**
     * The page numbers already in [plr]'s damaged book.
     */
    fun addedPages(plr: Player): List<Int> =
        (1..PAGE_COUNT).filter { plr.addedPages and (1 shl (pageShift + it - 1)) != 0 }

    /**
     * Whether [plr] has added all four pages.
     */
    fun completed(plr: Player) = addedPages(plr).size == PAGE_COUNT

    /**
     * Whether Jossik has already given [plr] this book.
     */
    fun given(plr: Player) = plr.givenBooks and givenBit != 0

    /**
     * Whether [plr] still has this book, damaged or complete.
     */
    fun owned(plr: Player) = QuestRewards.owns(plr, damaged) || QuestRewards.owns(plr, complete)

    /**
     * Records that Jossik gave [plr] this book.
     */
    fun markGiven(plr: Player) {
        plr.givenBooks = plr.givenBooks or givenBit
    }

    /**
     * Puts page number [page] into [plr]'s damaged book, which becomes the complete book with the fourth page.
     */
    fun addPage(plr: Player, page: Int) {
        val inventory = plr.inventory
        if (page in addedPages(plr)) {
            plr.sendMessage("The book already has that page.")
            return
        }
        if (!inventory.contains(damaged) || !inventory.remove(Item(pages[page - 1]))) return
        plr.addedPages = plr.addedPages or (1 shl (pageShift + page - 1))
        if (completed(plr)) {
            inventory.replace(damaged, complete)
            plr.sendMessage("You add the final page to the book. The book of $god is complete!")
        } else {
            plr.sendMessage("You add the page to the book.")
        }
    }

    /**
     * Tells [plr] which pages their damaged book has.
     */
    fun check(plr: Player) {
        val added = addedPages(plr)
        plr.sendMessage(when (added.size) {
            0 -> "No pages have been added to this book."
            1 -> "Page ${added[0]} has been added to this book."
            else -> "Pages ${added.dropLast(1).joinToString(", ")} and ${added.last()} have been added to this book."
        })
    }

    /**
     * Lets [plr] pick a passage from the complete book and recite it.
     */
    fun preach(plr: Player) {
        val (first, second, third, fourth) = passages
        plr.newDialogue()
            .options(first.first, { recite(it, first.second) }, second.first, { recite(it, second.second) },
                     third.first, { recite(it, third.second) }, fourth.first, { recite(it, fourth.second) })
            .open()
    }

    /**
     * Says [lines] over [plr]'s head, one at a time.
     */
    private fun recite(plr: Player, lines: List<String>) {
        plr.overlays.closeWindows()
        lines.forEachIndexed { index, line ->
            if (index == 0) {
                plr.speak(line)
            } else {
                world.scheduleOnce(index * RECITE_TICKS) { if (plr.state == EntityState.ACTIVE) plr.speak(line) }
            }
        }
    }

    companion object {

        /**
         * Jossik's NPC ID and the Talk-to response that opens his prayer-book dialogue.
         */
        const val JOSSIK = 1334
        const val PRAYER_BOOKS_OPTION = "Have you found any more prayerbooks?"

        /**
         * The ticks between recited lines.
         */
        const val RECITE_TICKS = 3

        /**
         * The page numbers added to each player's damaged books, four bits per book.
         */
        private var Player.addedPages by Attr.int().persist("god_book_pages")

        /**
         * The books Jossik has given each player, one bit per book.
         */
        private var Player.givenBooks by Attr.int().persist("god_books_given")
    }
}
