package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.action.BotNpcDialogues
import api.bot.script.BotScript
import api.predef.ext.totalItemAmount
import game.content.godBooks.GodBook
import game.content.questRewards.QuestRewards
import game.content.quests.Quest
import game.content.quests.QuestGates
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Claims one prayer book from Jossik and fills it with owned or purchased pages.
 * The native book and its saved page flags determine progress when a trade interrupts the activity.
 *
 * @author lare96
 */
class GodBookBotScript private constructor(bot: Bot, private val book: GodBook) : BotScript(bot) {

    companion object {
        private val retryDelay = 30.minutes
        private var Bot.godBookRetryAt by Attr.long().persist("god_book_bot_retry_at")

        /**
         * Continues an owned book or reclaims the first lost book Jossik will return before choosing a new one.
         */
        fun select(bot: Bot): GodBookBotScript? {
            if (!ActionSafety.NON_COMBAT.allows(bot) || bot.overlays.hasWindow() ||
                hasCompleteBook(bot) || System.currentTimeMillis() < bot.godBookRetryAt) return null

            val owned = GodBook.entries.filter { QuestRewards.owns(bot, it.damaged) }
            if (owned.isNotEmpty()) {
                val book = owned.filterNot { it.completed(bot) }.maxByOrNull { it.addedPages(bot).size }
                return book?.let { GodBookBotScript(bot, it) }
            }
            if (!canClaim(bot)) return null
            val personality = bot.personality
            val book = GodBook.entries.firstOrNull { it.given(bot) } ?: when {
                personality.isKind -> GodBook.SARADOMIN
                personality.isMean -> GodBook.ZAMORAK
                else -> GodBook.GUTHIX
            }
            return GodBookBotScript(bot, book)
        }

        private fun hasCompleteBook(bot: Bot): Boolean = GodBook.entries.any { QuestRewards.owns(bot, it.complete) }

        private fun canClaim(bot: Bot): Boolean {
            // TODO Run Horror from the Deep first when quest gates are enabled and the quest is implemented.
            return QuestGates.passes(bot, Quest.HORROR_FROM_THE_DEEP)
        }
    }

    private val deadline = System.currentTimeMillis() + 12.minutes.inWholeMilliseconds
    private val unavailablePages = mutableSetOf<Int>()

    override fun snapshot() = null

    override suspend fun run(): Boolean {
        val remaining = deadline - System.currentTimeMillis()
        if (remaining > 0 && !hasCompleteBook(bot)) {
            withTimeoutOrNull(remaining) {
                if ((book.owned(bot) || claimBook()) && !hasCompleteBook(bot)) fillBook()
            }
        }
        if (!hasCompleteBook(bot)) bot.godBookRetryAt = System.currentTimeMillis() + retryDelay.inWholeMilliseconds
        return true
    }

    /**
     * Uses the same lighthouse conversation as players. Jossik decides whether to grant or recover a book.
     */
    private suspend fun claimBook(): Boolean {
        if (!canClaim(bot) || !makeRoom(emptySet())) return false
        val jossik = bot.world.npcs.byCurrentNpcIds(setOf(GodBook.JOSSIK))
            .firstOrNull { it.state == EntityState.ACTIVE } ?: return false
        if (!handler.interactions.approach(jossik, timeout = 3.minutes, allowOutOfView = true,
                canContinue = { canClaim(bot) })) return false

        val responses = if (book.given(bot)) listOf(GodBook.PRAYER_BOOKS_OPTION)
            else listOf(GodBook.PRAYER_BOOKS_OPTION, book.god)
        return BotNpcDialogues.respond(bot, jossik, responses, allowed = { canClaim(bot) },
            completed = { book.owned(bot) }, advanceSpeech = true) {
            bot.output.sendNpcInteraction(1, jossik)
        }
    }

    /**
     * Applies owned pages first, then gives each missing page a bounded wanted-item purchase attempt.
     */
    private suspend fun fillBook() {
        val added = book.addedPages(bot)
        val missing = book.pages.filterIndexed { index, _ -> index + 1 !in added }
            .sortedByDescending { bot.totalItemAmount(it) > 0 }
        for (page in missing) {
            if (!isActionSafe() || hasCompleteBook(bot)) return
            if (bot.totalItemAmount(page) == 0) {
                if (page in unavailablePages) continue
                handler.shop.obtainWantedItems(mapOf(page to 1), deadline)
                // A purchase can pause this script and restart it with the banked book and any new pages.
                coroutineContext.ensureActive()
                if (bot.totalItemAmount(page) == 0) {
                    unavailablePages += page
                    continue
                }
            }
            if (!bot.inventory.contains(page)) {
                if (!handler.shop.obtainWantedItem(page) || !bot.inventory.contains(page)) continue
            }
            if (!carryBook(page) || !isActionSafe()) return
            val pageNumber = book.pages.indexOf(page) + 1
            if (pageNumber in book.addedPages(bot)) continue
            if (!handler.inventory.useItem(page).onItem(book.damaged) ||
                !waitFor(6.seconds) { pageNumber in book.addedPages(bot) }) return
        }
    }

    private suspend fun carryBook(page: Int): Boolean {
        if (bot.inventory.contains(book.damaged)) return true
        val keep = setOf(page)
        val slot = bot.equipment.computeIndexForId(book.damaged)
        if (slot >= 0) {
            return makeRoom(keep) && handler.equipment.unequip(slot) && bot.inventory.contains(book.damaged)
        }
        if (!bot.bank.contains(book.damaged) || !handler.banking.travelToBankOpen()) return false
        return try {
            (bot.inventory.computeRemainingSize() > 0 || handler.banking.depositInventory(keep)) &&
                handler.banking.withdrawUnnoted(Item(book.damaged))
        } finally {
            if (bot.bank.isOpen) bot.overlays.closeWindows()
        }
    }

    private suspend fun makeRoom(keep: Set<Int>): Boolean {
        if (bot.inventory.computeRemainingSize() > 0) return true
        if (!handler.banking.travelToBankOpen()) return false
        return try {
            handler.banking.depositInventory(keep) && bot.inventory.computeRemainingSize() > 0
        } finally {
            if (bot.bank.isOpen) bot.overlays.closeWindows()
        }
    }
}
