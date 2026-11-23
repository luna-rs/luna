package engine.bot.event

import game.bot.scripts.CastleWarsBotScript
import game.bot.scripts.DuelArenaBotScript
import game.bot.scripts.FightPitBotScript
import game.bot.scripts.FishingTrawlerBotScript
import game.bot.scripts.GamesRoomBotScript
import game.bot.scripts.PestControlBotScript
import game.content.castleWars.CastleWars
import game.content.castleWars.CastleWarsGame
import game.content.duelArena.DuelArena
import game.content.fightPit.FightPit
import game.content.fightPit.FightPitController
import game.content.fightPit.FightPitRound
import game.content.fishingTrawler.FishingTrawler
import game.content.gamesRoom.GamesRoom
import game.content.pestControl.PestControl
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot

/**
 * Temporary rosters for activities with shared lobbies or opponents.
 *
 * @author lare96
 */
internal object TeamBotEvents {
    val activities: List<BotEventActivity> = listOf(
        BotEventActivity(
            name = "Pest Control",
            defaultMinutes = 30,
            spawn = { _, _ -> PestControl.DOCK },
            prepare = { bot, participant ->
                bot.meleeKit(participant, listOf(Item(2347), Item(1511, 8), Item(385, 10)))
            },
            play = { bot, participant ->
                PestControlBotScript(bot, deadline = Long.MAX_VALUE, eventCanStart = participant.canStart)
            },
            inMatch = { PestControl.round.playing(it) },
            leave = { PestControl.leave(it) }
        ),
        BotEventActivity(
            name = "Castle Wars",
            defaultMinutes = 30,
            spawn = { _, _ -> CastleWarsGame.LOBBY },
            prepare = { bot, participant -> bot.meleeKit(participant, castleWars = true) },
            play = { bot, participant ->
                CastleWarsBotScript(bot, deadline = Long.MAX_VALUE, eventCanStart = participant.canStart)
            },
            inMatch = { CastleWars.game.match.isPlaying(it) },
            leave = { CastleWars.game.exit(it) }
        ),
        BotEventActivity(
            name = "Fight Pit",
            defaultMinutes = 20,
            spawn = { _, _ -> FightPit.OUTSIDE },
            prepare = { bot, participant -> bot.meleeKit(participant, listOf(Item(379, 8))) },
            play = { bot, participant ->
                FightPitBotScript(bot, deadline = Long.MAX_VALUE, eventCanStart = participant.canStart)
            },
            inMatch = { FightPitRound.fighting(it) },
            leave = { (it.controllers.primary as? FightPitController)?.withdraw() }
        ),
        BotEventActivity(
            name = "Fishing Trawler",
            defaultMinutes = 25,
            spawn = { _, _ -> FishingTrawler.DOCK },
            prepare = { bot, participant ->
                bot.applyEventKit(
                    levels = mapOf(Skill.FISHING to (50 + participant.index % 5 * 5)),
                    supplies = listOf(Item(583), Item(954, 20), Item(1941, 200))
                )
            },
            play = { bot, participant ->
                FishingTrawlerBotScript(bot, deadline = Long.MAX_VALUE, eventCanStart = participant.canStart)
            },
            inMatch = { FishingTrawler.onVoyage(it) },
            leave = { FishingTrawler.abandon(it) }
        ),
        BotEventActivity(
            name = "Duel Arena",
            defaultMinutes = 20,
            spawn = { _, _ -> DuelArena.LOBBY },
            prepare = { bot, participant -> bot.meleeKit(participant, listOf(Item(379, 6))) },
            play = { bot, participant ->
                DuelArenaBotScript(bot, deadline = Long.MAX_VALUE, eventCanStart = participant.canStart)
            },
            inMatch = { bot -> DuelArena.session(bot)?.let { it.started && !it.ended } == true },
            leave = { DuelArena.leave(it) }
        ),
        BotEventActivity(
            name = "Games Room",
            defaultMinutes = 20,
            spawn = { _, _ -> GamesRoomBotScript.LOBBY },
            prepare = { bot, _ -> bot.applyEventKit() },
            play = { bot, participant ->
                GamesRoomBotScript(bot, runeLink = participant.index / 2 % 2 == 0,
                    deadline = Long.MAX_VALUE, eventCanStart = participant.canStart)
            },
            inMatch = { GamesRoom.playing(it) },
            leave = { GamesRoom.leave(it) }
        )
    )

    private fun Bot.meleeKit(participant: BotEventParticipant, supplies: List<Item> = emptyList(),
                             castleWars: Boolean = false) {
        val level = 62 + participant.index % 4 * 4
        val levels = mapOf(Skill.ATTACK to level, Skill.STRENGTH to level + 5,
            Skill.DEFENCE to level, Skill.HITPOINTS to level + 8)
        val gear = mutableMapOf(
            Equipment.AMULET to Item(1725),
            Equipment.WEAPON to Item(1333),
            Equipment.CHEST to Item(1113),
            Equipment.SHIELD to Item(1201),
            Equipment.LEGS to Item(1079)
        )
        if (!castleWars) gear[Equipment.HEAD] = Item(1163)
        val keepGear = if (castleWars && CastleWars.game.match.team(this) != null) setOf(Equipment.CAPE) else emptySet()
        applyEventKit(levels, gear, supplies, keepGear)
    }
}
