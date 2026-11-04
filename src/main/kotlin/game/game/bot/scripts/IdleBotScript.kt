package game.bot.scripts

import api.bot.isRealBot

import api.bot.Suspendable.delay
import api.bot.action.trading.BotMarketplace
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.social.BotConversations
import api.bot.social.BotHomeGathering
import api.bot.social.BotIdleEmotes
import api.bot.social.BotSocialInteractions
import api.bot.social.SocialBotScript
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.predef.rand
import api.predef.world
import com.google.gson.JsonObject
import engine.bot.speech.BotConversationPool
import engine.bot.speech.BotConversationPool.ConversationSpeech
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
open class IdleBotScript(bot: Bot, private val destination: SubZone, private val deadline: Long) :
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
    private var nextReposition = 0L
    private var nextEmote = 0L
    private var lastEmote = -1

    override suspend fun init(resumed: Boolean): Boolean {
        BotConversations.cancel(bot)
        arrived = false
        if (ended() || !supported()) return true
        return !arrive()
    }

    override suspend fun run(): Boolean {
        if (ended()) {
            if (!BotConversations.engaged(bot)) return true
            delay(1.seconds)
            return false
        }
        if (BotSocialInteractions.participate(bot)) {
            delay(1.seconds)
            return false
        }
        if (!inside() || homeVisit() && !BotHomeGathering.settled(this)) {
            arrived = false
            BotConversations.cancel(bot)
            if (!arrive()) return true
        }
        BotConversations.tick(bot)
        val now = System.currentTimeMillis()
        if (BotConversations.engaged(bot)) {
            nextConsideration = now + 60_000
        } else if (ambientConversationTopic() == null && now >= nextConsideration && BotConversations.available(bot)) {
            nextConsideration = now + (30_000L..90_000L).random()
            considerConversation()
            // Keep the existing proximity policy for spontaneous filler. Explicit paired conversations work bot-only.
            if (!BotConversations.engaged(bot) && bot.localHumans.isNotEmpty()) bot.speechStack.pushFiller()
        }
        if (now >= nextEmote) {
            nextEmote = now + BotIdleEmotes.DECISION_MILLIS.random()
            BotIdleEmotes.perform(bot, lastEmote)?.let { lastEmote = it.id }
        }
        if (homeVisit() && now >= nextReposition && !BotConversations.engaged(bot) &&
            !BotSocialInteractions.engaged(bot)) {
            nextReposition = now + BotHomeGathering.REPOSITION_MILLIS.random()
            reposition()
        }
        delay(1.seconds)
        return false
    }

    override fun canSocialize(): Boolean = !ended() && arrived && inside()

    override fun canContinueConversation(): Boolean = arrived && inside() &&
        System.currentTimeMillis() < deadline + 120_000L

    override fun ambientConversationTopic(): ConversationSpeech? =
        if (javaClass != IdleBotScript::class.java || ended()) null
        else if (homeVisit()) ConversationSpeech.HOME else ConversationSpeech.IDLE

    override fun paused() {
        BotSocialInteractions.cancel(bot)
        cancelTravel()
        arrived = false
        BotConversations.cancel(bot)
        BotHomeGathering.release(this)
    }

    override suspend fun finish() {
        BotSocialInteractions.cancel(bot)
        cancelTravel()
        arrived = false
        BotConversations.cancel(bot)
        BotHomeGathering.release(this)
    }

    override fun snapshot(): IdleData? = IdleData().also {
        it.destination = destination
        it.deadline = deadline
    }

    /**
     * Selects casual company during an available idle visit. Specialized social scripts can offer a different pitch
     * while retaining the same arrival, expiry, interruption and conversation lifecycle.
     */
    protected open fun considerConversation() {
        if (!rand(bot.personality.social)) return
        val partner = world.locator.findViewablePlayers(bot) {
            it.isRealBot() && it !== bot && it.isWithinDistance(bot, 8) && BotConversations.available(it)
        }.filterIsInstance<Bot>().shuffled().firstOrNull() ?: return
        BotConversationPool.select(bot, partner)?.let { BotConversations.start(bot, partner, it) }
    }

    /**
     * Prevents restored or manually constructed visits from becoming an arbitrary travel activity.
     */
    private fun supported(): Boolean = destination == SubZone.HOME || BotMarketplace.forZone(destination) != null

    /**
     * Tests the exact safe work rectangles rather than merely being near a bank or a broad parent zone.
     */
    private fun inside(): Boolean = if (homeVisit()) BotHomeGathering.contains(bot.position)
        else BotMarketplace.forZone(destination)?.contains(bot.position) ?: (bot.position in destination.area)

    /**
     * Saved HOME visits retain their identity while using the separate social geometry on arrival.
     */
    private fun homeVisit(): Boolean = destination == SubZone.HOME

    /**
     * Enforces the saved absolute deadline for travel and recruitment; an existing exchange has bounded finish time.
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
                val travelZone = if (homeVisit()) SubZone.VARROCK_HOME_GATHERING else destination
                if (!handler.widgets.clickCloseInterface() ||
                    !handler.webWalkTo(zone = travelZone, radius = 0, bank = BankAccess.COINS)) {
                    return@withTimeoutOrNull false
                }
                if (!isActionSafe() || ended()) return@withTimeoutOrNull false
                val tile: Position = if (homeVisit()) BotHomeGathering.claim(this@IdleBotScript)
                    ?: return@withTimeoutOrNull false else {
                    val market = BotMarketplace.forZone(destination)
                    (market?.availableWorkPositions(bot) ?: destination.area.computePositions()
                        .filter { it.z == 0 && !world.collisionManager.isBlocked(it, false) })
                        .randomOrNull() ?: return@withTimeoutOrNull false
                }
                handler.webWalkTo(tile, bank = BankAccess.COINS) && isActionSafe() &&
                    bot.position == tile && inside() && !ended()
            } == true
            arrived = reached
            if (reached) {
                val now = System.currentTimeMillis()
                nextReposition = now + BotHomeGathering.REPOSITION_MILLIS.random()
                nextEmote = now + BotIdleEmotes.DECISION_MILLIS.random()
            }
            return reached
        } finally {
            if (!reached) {
                cancelTravel()
                BotHomeGathering.release(this)
            }
            travelling = false
        }
    }

    /**
     * Occasionally rejoins a different loose group without cutting off an exchange or overriding urgent work.
     * Short local walks use carried supplies and never trigger banking or a recovery teleport.
     */
    private suspend fun reposition() {
        if (!BotIdleEmotes.ready(bot)) return
        val tile = BotHomeGathering.claim(this, reposition = true) ?: return
        if (tile == bot.position) return
        arrived = false
        travelling = true
        try {
            arrived = withTimeoutOrNull(20_000L) {
                handler.webWalkTo(tile, bank = BankAccess.NONE, fallback = false) &&
                    isActionSafe() && bot.position == tile && inside() && !ended()
            } == true
        } finally {
            if (!arrived) {
                cancelTravel()
                BotHomeGathering.release(this)
            }
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
