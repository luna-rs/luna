package game.content.rangingGuild

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemSecondClickEvent
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

on(LoginEvent::class) { ArcheryCompetition.refresh(plr) }
on(LogoutEvent::class) { ArcheryCompetition.logout(plr); RangingGuildGate.logout(plr) }
npc1(693) { ArcheryCompetition.talk(plr, targetNpc) }
npc1(694) { ArcheryRewards.open(plr, targetNpc) }
npc1(679) {
    plr.newDialogue().npc(679, "Welcome! You need a Ranged level of 40 to enter the guild.",
        "Speak to the Competition Judge inside to try the archery targets.").open()
}
object1(2513, InteractionPolicy.STANDARD_LINE_OF_SIGHT_BIF) { ArcheryCompetition.shoot(plr, gameObject) }
object1(2514, { _, _ -> InteractionPolicy(InteractionType.SIZE, 2) }) { RangingGuildGate.enter(plr, gameObject) }
button(568) { if (plr.overlays.getOverlay(ArcheryCompetition.Result::class.java) != null) plr.overlays.closeWindows() }
button(12032) { if (plr.overlays.getOverlay(ArcheryRewards.Exchange::class.java) != null) plr.overlays.closeWindows() }
on(WidgetItemFirstClickEvent::class).filter { widgetId == 12033 }.then {
    ArcheryRewards.select(plr, index, itemId, false)
}
on(WidgetItemSecondClickEvent::class).filter { widgetId == 12033 }.then {
    ArcheryRewards.select(plr, index, itemId, true)
}
