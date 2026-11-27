package engine.bot.speech

import api.bot.isRealBot
import api.predef.rand
import com.google.common.base.Ticker
import com.google.common.cache.CacheBuilder
import engine.bot.speech.BotReactionSpeechPool.ReactionSpeech
import engine.controllers.Controllers.inWilderness
import engine.player.chat.sendPrivateChat
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.net.codec.ByteMessage
import io.luna.util.StringUtils
import java.util.concurrent.TimeUnit

/**
 * Sends a short reaction from a social, mean bot to its credited human PKer.
 *
 * The game thread owns delivery and both cooldowns. Username hashes keep the limits in effect across logins,
 * and the caches discard expired entries without retaining player or bot instances.
 *
 * @author lare96
 */
internal class BotPkDeathMessages(ticker: Ticker = Ticker.systemTicker(),
                                 private val roll: () -> Boolean = { rand(0.20) }) {

    companion object {
        val live = BotPkDeathMessages()
    }

    /**
     * The most recent death PMs received by each player, from any bot.
     */
    private val recentByPlayer = CacheBuilder.newBuilder().ticker(ticker).expireAfterWrite(10, TimeUnit.MINUTES)
        .build<Long, Unit>()

    /**
     * The most recent death PMs from each bot to each player.
     */
    private val recentByPair = CacheBuilder.newBuilder().ticker(ticker).expireAfterWrite(30, TimeUnit.MINUTES)
        .build<Pair<Long, Long>, Unit>()

    /**
     * Tries to send a death PM before the victim is moved out of the Wilderness.
     */
    fun onDeath(victim: Player, source: Mob?) {
        if (!victim.isRealBot() || victim.isEventBot || !victim.inWilderness() ||
            !victim.personality.isSocial || !victim.personality.isMean || victim.isMuted ||
            victim.speechStack.isAllDisabled || source !is Player || source.isBot || source === victim) return

        val playerHash = source.usernameHash
        val pair = victim.usernameHash to playerHash
        if (recentByPlayer.getIfPresent(playerHash) != null || recentByPair.getIfPresent(pair) != null ||
            source.state != EntityState.ACTIVE || victim.world.getPlayer(playerHash).orElse(null) !== source ||
            source.client.isPendingLogout || !source.client.channel.isActive || !roll()) return

        val people = victim.memory.people
        val killerName = source.username
        val reaction = if (people.hasSentPkDeathMessage(killerName)) ReactionSpeech.PK_DEATH_PM_FOLLOWUP
                       else ReactionSpeech.PK_DEATH_PM
        val text = BotReactionSpeechPool.take(victim, reaction) ?: return
        val buffer = ByteMessage.raw()
        val packed = try {
            StringUtils.packText(text, buffer)
            buffer.getBytes(buffer.content().readableBytes())
        } finally {
            buffer.releaseAll()
        }
        if (sendPrivateChat(victim, source, packed, text)) {
            people.rememberPkDeathMessage(killerName)
            recentByPlayer.put(playerHash, Unit)
            recentByPair.put(pair, Unit)
        }
    }
}
