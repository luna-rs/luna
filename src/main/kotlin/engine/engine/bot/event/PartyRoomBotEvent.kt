package engine.bot.event

import api.bot.zone.SubZone
import game.bot.scripts.DropPartyBotScript
import game.content.partyRoom.dropParty.DropPartyOption

/**
 * Brings temporary participants to native Party Room balloon drops.
 *
 * @author lare96
 */
internal object PartyRoomBotEvent {
    val activity = BotEventActivity(
        name = "Party Room",
        defaultMinutes = 10,
        spawn = { _, _ -> SubZone.PARTY_ROOM.inside },
        prepare = { bot, _ -> bot.applyEventKit() },
        play = { bot, _ ->
            val id = DropPartyOption.currentPartyId
            if (DropPartyOption.isCurrentParty(id)) DropPartyBotScript(bot, id, DropPartyOption.currentPartyDeadline)
            else null
        }
    )
}
