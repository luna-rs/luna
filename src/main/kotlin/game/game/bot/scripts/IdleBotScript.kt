package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.action.trading.BotMarketplace
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.social.BotConversations
import api.bot.social.SocialBotScript
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.predef.rand
import api.predef.world
import com.google.gson.JsonObject
import engine.bot.speech.BotConversationPool
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Spends a finite social visit at home or an existing marketplace, instead of immediately choosing more work.
 * Availability begins only after arrival, with nearby paired conversations and occasional ordinary filler.
 * Saved visits retain their destination and expiry; live conversations are cancelled and never replayed on resume.
 *
 * @property destination The existing safe hub containing this visit.
 * @property deadline Epoch-millisecond expiry, including time offline.
 * @author lare96
 */
class IdleBotScript(bot: Bot, private val destination: SubZone, private val deadline: Long) :
    BotScript(bot), SocialBotScript {

    /**
     * Destination and absolute expiry needed to restore a bounded visit.
     *
     * @author lare96
     */
    class IdleData : BotScriptData() {
        /**
         * Home or one of the configured marketplaces; arbitrary unsafe destinations are rejected on load.
         */
        var destination = SubZone.HOME
        /**
         * The original visit expiry; resuming does not grant another full visit.
         */
        var deadline = 0L
        override fun load(data: JsonObject) {
            destination = SubZone.valueOf(data.get("destination").asString)
            deadline = data.get("deadline").asLong
        }
        override fun save(data: JsonObject) {
            data.addProperty("destination", destination.name)
            data.addProperty("deadline", deadline)
        }
    }

    /**
     * Restores a visit without saving another bot, a queued turn, navigation or a live speech reservation.
     */
    constructor(bot: Bot, data: IdleData) : this(bot, data.destination, data.deadline)

    /**
     * Arrival and consideration timing are rebuilt after every interruption.
     */
    private var arrived = false
    private var nextConsideration = 0L
    private var travelling = false

    override suspend fun init(resumed: Boolean): Boolean {
        BotConversations.cancel(bot)
        arrived = false
        if (ended() || !supported()) return true
        return !arrive()
    }

    override suspend fun run(): Boolean {
        if (ended()) return true
        if (!inside()) {
            arrived = false
            BotConversations.cancel(bot)
            if (!arrive()) return true
        }
        BotConversations.tick(bot)
        val now = System.currentTimeMillis()
        if (BotConversations.engaged(bot)) {
            nextConsideration = now + 60_000
        } else if (now >= nextConsideration && BotConversations.available(bot)) {
            nextConsideration = now + (30_000L..90_000L).random()
            if (rand(bot.personality.social)) {
                val partner = world.locator.findViewablePlayers(bot) {
                    it is Bot && it !== bot && it.isWithinDistance(bot, 8) && BotConversations.available(it)
                }.filterIsInstance<Bot>().shuffled().firstOrNull()
                if (partner != null) {
                    BotConversationPool.select(bot, partner)?.let { BotConversations.start(bot, partner, it) }
                }
            }
            // Keep the existing proximity policy for spontaneous filler. Explicit paired conversations work bot-only.
            if (!BotConversations.engaged(bot) && bot.localHumans.isNotEmpty()) bot.speechStack.pushFiller()
        }
        delay(1.seconds)
        return false
    }

    override fun canSocialize(): Boolean = arrived && !ended() && inside()

    override fun paused() {
        cancelTravel()
        arrived = false
        BotConversations.cancel(bot)
    }

    override suspend fun finish() {
        cancelTravel()
        arrived = false
        BotConversations.cancel(bot)
    }

    override fun snapshot() = IdleData().also {
        it.destination = destination
        it.deadline = deadline
    }

    /**
     * Prevents restored or manually constructed visits from becoming an arbitrary travel activity.
     */
    private fun supported(): Boolean = destination == SubZone.HOME || BotMarketplace.forZone(destination) != null

    /**
     * Tests the exact safe work rectangles rather than merely being near a bank or a broad parent zone.
     */
    private fun inside(): Boolean = BotMarketplace.forZone(destination)?.contains(bot.position)
        ?: (bot.position in destination.area)

    /**
     * Enforces the saved absolute deadline while travelling, idling and exposing social availability.
     */
    private fun ended(): Boolean = System.currentTimeMillis() >= deadline

    /**
     * Uses ordinary travel and webwalking, with a bounded whole-trip timeout and verified final arrival.
     * The visit requests neither stock deposits nor equipment changes; ordinary travel may pay its usual costs.
     */
    private suspend fun arrive(): Boolean {
        if (ended() || !supported()) return false
        val remaining = minOf(120_000L, deadline - System.currentTimeMillis()).coerceAtLeast(1)
        travelling = true
        var reached = false
        try {
            reached = withTimeoutOrNull(remaining) {
                if (!handler.widgets.clickCloseInterface() || !handler.travelTo(destination, BankAccess.COINS)) {
                    return@withTimeoutOrNull false
                }
                if (!isActionSafe() || ended()) return@withTimeoutOrNull false
                val positions = BotMarketplace.forZone(destination)?.positions ?: destination.area.computePositions()
                    .filter { it.z == 0 && !world.collisionManager.isBlocked(it, false) }
                val tile: Position = positions.randomOrNull() ?: return@withTimeoutOrNull false
                handler.webWalkTo(tile, bank = BankAccess.COINS) && isActionSafe() &&
                    bot.position == tile && inside() && !ended()
            } == true
            arrived = reached
            return reached
        } finally {
            if (!reached) cancelTravel()
            travelling = false
        }
    }

    /**
     * Stops this visit's unfinished travel before an interrupt takes over. A replacement script's navigation is left alone.
     */
    private fun cancelTravel() {
        if (travelling && bot.scriptStack.current() === this) {
            bot.navigator.cancel()
            bot.walking.clear()
        }
    }
}
