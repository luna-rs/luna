package engine.bot.coordinator

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.Suspendable.delay
import api.bot.zone.SubZone
import api.predef.*
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.DepositPartyRoomItemBotScript
import game.bot.scripts.DropPartyBotScript
import game.bot.scripts.FishingTrawlerBotScript
import game.bot.scripts.CastleWarsBotScript
import game.bot.scripts.PestControlBotScript
import game.content.pestControl.PestControl
import game.bot.scripts.GnomeBallBotScript
import game.bot.scripts.BrimhavenArenaBotScript
import game.bot.scripts.RangingGuildBotScript
import game.content.rangingGuild.ArcheryCompetition.count
import game.content.brimhavenArena.BrimhavenArena
import game.bot.scripts.GamesRoomBotScript
import game.bot.scripts.DuelArenaBotScript
import game.content.duelArena.DuelArena
import game.content.gamesRoom.GamesRoom
import game.bot.scripts.PartyRoomDonations
import game.bot.scripts.LogoutBotScript
import game.bot.scripts.trading.TradeBotScript
import game.content.partyRoom.dropParty.DropPartyOption
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import kotlin.time.Duration.Companion.seconds

/**
 * Considers supported minigames before a bounded Party Room visit.
 * Native parties invite every eligible bot, regardless of
 * the host, personality or prize value; unsafe bots are reconsidered while that party remains active.
 *
 * @author lare96
 */
object MinigameCoordinator : ProgressiveCoordinator() {
    /**
     * The last runtime party this bot was invited to, preventing duplicate attendance scripts.
     */
    private var Bot.attendedParty by Attr.long()
    override val activity get() = BotActivity.MINIGAMES
    override fun firstScript(bot: Bot): BotScript? = PartyRoomDonations.select(bot)
    override fun nextScript(bot: Bot): BotScript {
        val id = DropPartyOption.currentPartyId
        if (DropPartyOption.isCurrentParty(id) && !bot.inventory.isFull) {
            bot.attendedParty = id
            return DropPartyBotScript(bot, id, DropPartyOption.currentPartyDeadline)
        }
        if (GamesRoom.inRoom(bot.position) && GamesRoomBotScript.eligible(bot)) return GamesRoomBotScript(bot)
        if (PestControlBotScript.eligible(bot) && (PestControl.atOutpost(bot.position) ||
                PestControl.round.waiting().isNotEmpty() || rand(0.25))) return PestControlBotScript(bot)
        if (RangingGuildBotScript.eligible(bot) && (bot.count > 0 || rand(0.25))) return RangingGuildBotScript(bot)
        if (BrimhavenArenaBotScript.eligible(bot) && (BrimhavenArena.inside(bot.position) || rand(0.25)))
            return BrimhavenArenaBotScript(bot)
        if (GnomeBallBotScript.eligible(bot) && rand(0.25)) return GnomeBallBotScript(bot)
        if (DuelArenaBotScript.eligible(bot) && (DuelArena.inside(bot.position) || rand().nextBoolean()))
            return DuelArenaBotScript(bot)
        if (FishingTrawlerBotScript.eligible(bot)) return FishingTrawlerBotScript(bot)
        if (CastleWarsBotScript.eligible(bot)) return CastleWarsBotScript(bot)
        if (GamesRoomBotScript.eligible(bot)) return GamesRoomBotScript(bot)
        return object : DynamicBotScript(bot) {
            override suspend fun run(): Boolean {
                if (handler.webWalkTo(SubZone.PARTY_ROOM)) delay(30.seconds)
                return true
            }
        }
    }

    /**
     * Attendance never interrupts combat, trade windows, reflexes, death or a donation already in progress.
     */
    internal fun safeToAttend(bot: Bot): Boolean = bot.state == EntityState.ACTIVE &&
        BotScript.ActionSafety.NON_COMBAT.allows(bot) && !bot.inWilderness() &&
        !bot.emotions.isNervousAboutHp && bot.reflex.activeReflex == null &&
        !bot.overlays.hasWindow() && bot.dialogues == null && !bot.inventory.isFull &&
        bot.scriptStack.current() !is DepositPartyRoomItemBotScript &&
        bot.scriptStack.current() !is LogoutBotScript &&
        bot.scriptStack.current() !is TradeBotScript

    /**
     * Invites bots once per runtime party, retrying those that are currently ineligible on later party ticks.
     */
    fun onPartyStarted() {
        val id = DropPartyOption.currentPartyId
        if (!DropPartyOption.isCurrentParty(id)) return
        for (player in world.players) {
            if (player !is Bot || player.attendedParty == id || !safeToAttend(player)) continue
            if (player.scriptStack.current() is DropPartyBotScript) continue
            player.attendedParty = id
            player.scriptStack.pushHead(DropPartyBotScript(player, id, DropPartyOption.currentPartyDeadline))
        }
    }
}
