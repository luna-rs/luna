package game.content.gnomeBall

import api.predef.*
import game.content.treasureTrails.TrailClues
import io.luna.game.event.impl.*
import io.luna.game.event.impl.PlayerClickEvent.PlayerSecondClickEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.interact.InteractionPolicy

on(ServerLaunchEvent::class) { GnomeBall.launch() }
on(LoginEvent::class) { GnomeBall.login(plr) }
on(LogoutEvent::class) { GnomeBall.leave(plr) }
object1(2394) { GnomeBall.openGate(plr, gameObject) }
object1(2393, InteractionPolicy.STANDARD_LINE_OF_SIGHT_BIF) { GnomeBall.shoot(plr, gameObject) }
npc1(635) {
    // The existing general clue listener answers the challenge; do not open a competing dialogue.
    if (TrailClues.all.none { it.npcId == 635 && plr.inventory.contains(it.id) }) GnomeBall.referee(plr, targetNpc)
}
for (id in listOf(621, 622, 625, 626, 629, 630)) npc1(id) { GnomeBall.tackle(plr, targetNpc) }
npc1(633, InteractionPolicy.STANDARD_LINE_OF_SIGHT_BIF) { GnomeBall.pass(plr, targetNpc) }
npc1(634) { plr.sendMessage("The winger is already helping another player.") }
on(PlayerSecondClickEvent::class, interaction = { _, _ -> InteractionPolicy.STANDARD_LINE_OF_SIGHT })
    .filter { GnomeBall.session(plr) != null }.then { GnomeBall.pass(plr, targetPlr) }
useItem(GnomeBall.BALL).onPlayer(InteractionPolicy.STANDARD_LINE_OF_SIGHT_BIF) {
    GnomeBall.passPersonal(plr, targetPlr)
}
