package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.zone.SubZone
import com.google.gson.JsonObject
import game.content.partyRoom.dropParty.DropPartyOption
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Attends one native party, choosing unclaimed balloons without inspecting their concealed prizes.
 *
 * @author lare96
 */
class DropPartyBotScript(bot: Bot, private val partyId: Long, private val deadline: Long) : BotScript(bot) {
    /**
     * Identifies the party and limits the visit across interruptions and logouts.
     *
     * @author lare96
     */
    class PartyData : BotScriptData() {
        var partyId = 0L
        var deadline = 0L
        override fun load(data: JsonObject) {
            partyId = data.get("party_id")?.asLong ?: 0L
            deadline = data.get("deadline")?.asLong ?: 0L
        }
        override fun save(data: JsonObject) {
            data.addProperty("party_id", partyId)
            data.addProperty("deadline", deadline)
        }
    }

    constructor(bot: Bot, data: PartyData) : this(bot, data.partyId, data.deadline)

    private var failedInteractions = 0

    override suspend fun init(resumed: Boolean): Boolean {
        if (ended()) return true
        cleanup()
        bot.actions.interruptWeak()
        bot.combat.target = null
        val arrived = withTimeoutOrNull(120_000) {
            handler.widgets.clickCloseInterface() && handler.travelTo(SubZone.PARTY_ROOM)
        } == true
        return !arrived || ended()
    }

    override suspend fun run(): Boolean {
        if (ended() || bot.inventory.isFull || failedInteractions >= 3) return true
        val balloon = DropPartyOption.availableBalloons(partyId)
            .minByOrNull { bot.position.computeLongestDistance(it.position) }
        if (balloon == null) {
            if (bot.inventory.isFull) return true
            delay(1.seconds)
            return false
        }
        val success = withTimeoutOrNull(30_000) {
            if (!handler.interactions.interact(1, balloon)) return@withTimeoutOrNull false
            waitFor(3.seconds) { balloon.stompedBy != null }
            if (balloon.stompedBy !== bot) return@withTimeoutOrNull true
            if (!waitFor(3.seconds) { balloon.state == EntityState.INACTIVE }) return@withTimeoutOrNull false
            val reward = balloon.reward ?: return@withTimeoutOrNull false
            if (reward.state != EntityState.ACTIVE || !reward.view.isViewableFor(bot)) return@withTimeoutOrNull false
            if (!bot.inventory.hasSpaceFor(reward.toItem())) return@withTimeoutOrNull false
            if (!handler.interactions.interact(1, reward)) return@withTimeoutOrNull false
            waitFor(3.seconds) { reward.state == EntityState.INACTIVE }
        } == true
        if (success) failedInteractions = 0 else {
            failedInteractions++
            cleanup()
        }
        delay(1.seconds)
        return false
    }

    internal fun ended(): Boolean = System.currentTimeMillis() >= deadline ||
        !DropPartyOption.isCurrentParty(partyId)

    private fun cleanup() {
        bot.navigator.cancel()
        bot.walking.clear()
    }

    override fun paused() {
        cleanup()
    }

    override suspend fun finish() {
        if (!isPaused()) cleanup()
    }

    override fun snapshot() = PartyData().also {
        it.partyId = partyId
        it.deadline = deadline
    }
}
