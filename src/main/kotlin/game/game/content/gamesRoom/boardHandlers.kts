package game.content.gamesRoom

import api.predef.*
import io.luna.game.event.impl.ArrangeItemEvent
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.event.impl.PlayerClickEvent.PlayerFirstClickEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.PlayerContextMenuOption
import io.luna.game.model.mob.interact.InteractionPolicy

on(ServerLaunchEvent::class) { GamesRoom.launch() }
on(PlayerFirstClickEvent::class, interaction = { _, _ -> InteractionPolicy.STANDARD_SIZE })
    .filter { GamesRoom.inRoom(plr.position) && plr.contextMenu.contains(PlayerContextMenuOption.CHALLENGE) }
    .then { GamesRoom.challenge(plr, targetPlr) }
for (column in 0..6) button(10736 + column) { GamesRoom.column(plr, column) }
button(10713) { GamesRoom.draw(plr) }
button(10475) { GamesRoom.draw(plr) }
on(ArrangeItemEvent::class) {
    if (widgetId == 10494) GamesRoom.move(plr, fromIndex, toIndex)
}
on(LogoutEvent::class) { GamesRoom.leave(plr) }
for (table in listOf(4652, 4653)) object1(table) { GamesRoom.view(plr, gameObject.position) }
object1(4642) {
    plr.sendMessage("Challenge another player to play unranked Draughts or RuneLink.")
    plr.sendMessage("Draughts: drag pieces diagonally. Captures and chained jumps are compulsory.")
    plr.sendMessage("RuneLink: drop pieces into columns to connect four. Closing a board resigns.")
}
