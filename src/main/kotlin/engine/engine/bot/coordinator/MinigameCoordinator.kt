package engine.bot.coordinator

import api.bot.isRealBot

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
import game.bot.scripts.BarrowsBotScript
import game.bot.scripts.FightCaveBotScript
import game.bot.scripts.FightPitBotScript
import game.content.fightPit.FightPit
import game.bot.scripts.DropPartyBotScript
import game.bot.scripts.FishingTrawlerBotScript
import game.bot.scripts.MageArenaBotScript
import game.bot.scripts.MageTrainingArenaBotScript
import game.bot.scripts.BlastFurnaceBotScript
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
import game.bot.scripts.PartyRoomEntertainment
import game.bot.scripts.skills.SkullballBotScript
import game.bot.scripts.LogoutBotScript
import game.bot.scripts.trading.TradeBotScript
import game.content.partyRoom.dropParty.DropPartyOption
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import kotlin.time.Duration.Companion.seconds

/**
 * Considers supported minigames before a bounded Party Room visit.
 * Each bot close enough to walk to a native party in time decides once whether to attend, from its interest in
 * minigames, its distance from the Party Room and its personality. Those that go set off at different times across
 * the countdown.
 *
 * @author lare96
 */
object MinigameCoordinator : ProgressiveCoordinator() {
    /**
     * Bots within this many tiles of the Party Room are in or around Seers' Village.
     */
    private const val NEAR_DISTANCE = 80

    /**
     * How many ticks a bot takes to cover a tile on its way to the Party Room, doors and detours included.
     */
    private const val WALK_TICKS_PER_TILE = 1.4

    /**
     * The longest walk worth starting, in ticks. [DropPartyBotScript] gives up on a walk after two minutes.
     */
    private const val MAX_WALK_TICKS = 200

    /**
     * How many ticks after the first balloons drop a bot can still arrive and find some left.
     */
    private const val LATE_ARRIVAL_TICKS = 50

    /**
     * The highest chance any bot has of attending a party.
     */
    private const val MAX_ATTEND_CHANCE = 0.9

    /**
     * How many ticks a bot takes to notice that a party has been called.
     */
    private val NOTICE_TICKS = 5..50

    /**
     * The share of the countdown that bots spread their departures over.
     */
    private const val COUNTDOWN_SHARE = 0.6

    /**
     * The last runtime party this bot made up its mind about, so it decides once and never attends twice.
     */
    private var Bot.attendedParty by Attr.long()

    /**
     * The world tick this bot sets off for the party it decided to attend, or 0 if it isn't waiting to set off.
     */
    private var Bot.partyJoinTick by Attr.long()
    override val activity get() = BotActivity.MINIGAMES
    override fun firstScript(bot: Bot): BotScript? = PartyRoomDonations.select(bot) ?: PartyRoomEntertainment.select(bot)
    override fun nextScript(bot: Bot): BotScript {
        val id = DropPartyOption.currentPartyId
        if (DropPartyOption.isCurrentParty(id) && !bot.inventory.isFull) {
            bot.attendedParty = id
            bot.partyJoinTick = 0L
            return DropPartyBotScript(bot, id, DropPartyOption.currentPartyDeadline)
        }
        val pestReady = PestControlBotScript.eligible(bot)
        if (pestReady && (PestControl.atOutpost(bot.position) || PestControl.round.waiting().isNotEmpty())) {
            return PestControlBotScript(bot)
        }
        BlastFurnaceBotScript.select(bot)?.let { return it }
        if (FightCaveBotScript.canStart(bot) && rand(0.20)) return FightCaveBotScript(bot)
        if (BarrowsBotScript.eligible(bot) && rand(0.20)) return BarrowsBotScript(bot)
        if (FightPitBotScript.eligible(bot) && (FightPit.inside(bot.position) || rand(0.25))) return FightPitBotScript(bot)
        if (MageArenaBotScript.eligible(bot) && rand(0.25)) return MageArenaBotScript(bot)
        if (MageTrainingArenaBotScript.eligible(bot) && rand(0.25)) return MageTrainingArenaBotScript(bot)
        if (GamesRoom.inRoom(bot.position) && GamesRoomBotScript.eligible(bot)) return GamesRoomBotScript(bot)
        if (pestReady && rand(0.25)) return PestControlBotScript(bot)
        if (RangingGuildBotScript.eligible(bot) && (bot.count > 0 || rand(0.25))) return RangingGuildBotScript(bot)
        if (BrimhavenArenaBotScript.eligible(bot) && (BrimhavenArena.inside(bot.position) || rand(0.25)))
            return BrimhavenArenaBotScript(bot)
        if (SkullballBotScript.eligible(bot) && rand(0.25)) return SkullballBotScript(bot, 900.seconds)
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
    internal fun safeToAttend(bot: Bot): Boolean = !bot.isEventBot && bot.state == EntityState.ACTIVE &&
        BotScript.ActionSafety.NON_COMBAT.allows(bot) && !bot.inWilderness() &&
        !bot.emotions.isNervousAboutHp && bot.reflex.activeReflex == null &&
        !bot.overlays.hasWindow() && bot.dialogues == null && !bot.inventory.isFull &&
        bot.scriptStack.current() !is DepositPartyRoomItemBotScript &&
        bot.scriptStack.current() !is LogoutBotScript &&
        bot.scriptStack.current() !is TradeBotScript

    /**
     * Works out how likely a bot that can reach a party in time is to attend it.
     *
     * @param interest The bot's weight for [BotActivity.MINIGAMES]. Below 0.20 it never goes.
     * @param distance How far the bot is from the Party Room, in tiles.
     * @param keenness A multiplier from the bot's personality and mood.
     * @return The chance, from 0 to [MAX_ATTEND_CHANCE].
     */
    internal fun attendChance(interest: Double, distance: Int, keenness: Double): Double {
        if (interest < 0.20) return 0.0
        val reach = if (distance <= NEAR_DISTANCE) 1.0 else 0.4
        return (interest * reach * keenness).coerceIn(0.0, MAX_ATTEND_CHANCE)
    }

    /**
     * Works out how likely [bot] is to attend a party from [distance] tiles away. Social and greedy bots are keener,
     * antisocial bots less so.
     */
    private fun attendChance(bot: Bot, distance: Int): Double {
        val interest = bot.preferences.activities[BotActivity.MINIGAMES] ?: 0.0
        var keenness = when {
            bot.personality.isSocial -> 1.25
            bot.personality.isAntiSocial -> 0.75
            else -> 1.0
        }
        if (bot.emotions.isFeeling(EmotionType.GREEDY)) keenness *= 1.25
        return attendChance(interest, distance, keenness)
    }

    /**
     * Picks how long a bot waits before setting off: a moment to notice the party, then a random share of the
     * countdown, so the bots that go don't all leave on the tick the lever is pulled.
     *
     * @param countdownTicks The ticks left before the balloons drop.
     * @return The wait, in ticks.
     */
    internal fun joinDelay(countdownTicks: Int): Int =
        rand(NOTICE_TICKS) + rand(0, (countdownTicks * COUNTDOWN_SHARE).toInt())

    /**
     * Works out how long a bot can wait before setting off and still walk to the party in time.
     *
     * @param distance How far the bot is from the Party Room, in tiles.
     * @param countdownTicks The ticks left before the balloons drop.
     * @return The longest wait in ticks, or a negative number if the bot can't get there in time.
     */
    internal fun latestDeparture(distance: Int, countdownTicks: Int): Int {
        val walkTicks = (distance * WALK_TICKS_PER_TILE).toInt()
        return if (walkTicks > MAX_WALK_TICKS) -1 else countdownTicks + LATE_ARRIVAL_TICKS - walkTicks
    }

    /**
     * Decides whether [bot] attends the party and when it sets off. A bot that would have to leave before it could
     * have noticed the party doesn't go, and a bot far from the room leaves early enough to arrive.
     *
     * @return The world tick it sets off, or 0 if it isn't going.
     */
    private fun chooseJoinTick(bot: Bot, now: Long, countdownTicks: Int): Long {
        if (bot.scriptStack.current() is DropPartyBotScript) return 0L
        val distance = bot.position.computeLongestDistance(SubZone.PARTY_ROOM.inside)
        val latest = latestDeparture(distance, countdownTicks)
        if (latest < NOTICE_TICKS.first) return 0L
        val chance = attendChance(bot, distance)
        return if (chance > 0.0 && rand(chance)) now + minOf(joinDelay(countdownTicks), latest) else 0L
    }

    /**
     * Runs on every party tick. Until the countdown ends, each bot that is safe decides once whether to attend.
     * Those that go set off when their own join tick comes, or when they are next free if they can still arrive.
     */
    fun onPartyStarted() {
        val id = DropPartyOption.currentPartyId
        if (!DropPartyOption.isCurrentParty(id)) return
        val now = world.currentTick
        val countdownTicks = (DropPartyOption.secondsLeft ?: 0) * 1000 / 600
        for (player in world.players) {
            if (!player.isRealBot()) continue
            if (player.attendedParty != id) {
                if (countdownTicks <= 0 || !safeToAttend(player)) continue
                player.attendedParty = id
                player.partyJoinTick = chooseJoinTick(player, now, countdownTicks)
                continue
            }
            val joinTick = player.partyJoinTick
            if (joinTick == 0L || now < joinTick) continue
            val distance = player.position.computeLongestDistance(SubZone.PARTY_ROOM.inside)
            if (latestDeparture(distance, countdownTicks) < 0) {
                player.partyJoinTick = 0L
                continue
            }
            if (!safeToAttend(player)) continue
            player.partyJoinTick = 0L
            if (player.scriptStack.current() is DropPartyBotScript) continue
            player.scriptStack.pushHead(DropPartyBotScript(player, id, DropPartyOption.currentPartyDeadline))
        }
    }
}
