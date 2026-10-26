package game.content.duelArena

import api.predef.*
import api.predef.ext.*
import api.combat.player.PlayerCombatHandler.playerStopAttack
import api.combat.player.PlayerCombatHandler.playerDefence
import io.luna.game.event.impl.*
import io.luna.game.event.impl.PlayerClickEvent.PlayerFirstClickEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.overlay.NumberInput

on(ServerLaunchEvent::class) { DuelArena.launch() }
on(LoginEvent::class) { DuelArena.login(plr) }
on(LogoutEvent::class) { DuelArena.leave(plr) }
on(PlayerFirstClickEvent::class, interaction = { _, _ -> InteractionPolicy.STANDARD_SIZE })
    .filter { DuelArena.inside(plr.position) && !DuelArena.arena(plr.position) }
    .then { DuelArena.challenge(plr, targetPlr) }

for (rule in DuelRules.entries) button(rule.button) { DuelArena.session(plr)?.toggle(plr, rule) }
for (id in listOf(6674, 6520)) button(id) {
    val duel = DuelArena.session(plr)
    if (duel != null && duel.confirmation == (id == 6520)) duel.accept(plr)
}
for (id in listOf(6676, 6558)) button(id) {
    DuelArena.session(plr)?.takeIf { !it.started }?.finish(null)
}
for (id in listOf(3111, 3112, 3113, 3114, 3203)) object1(id) { DuelArena.session(plr)?.forfeit(plr) }
npc1(957) {
    DuelArena.claim(plr)
    plr.sendMessage("Challenge another player in the grounds to arrange a duel. Here are your recent results.")
    DuelArena.scoreboard(plr)
}
object1(3192) { DuelArena.scoreboard(plr) }
for (id in 959..961) npc3(id) {
    if (DuelArena.session(plr) == null && DuelArena.inside(plr.position) && plr.isAlive && !plr.combat.inCombat()) {
        plr.hitpoints.adjustLevel(12)
        plr.sendMessage("You feel a little better.")
    }
}

for (type in listOf(WidgetItemClickEvent.WidgetItemFirstClickEvent::class,
    WidgetItemClickEvent.WidgetItemSecondClickEvent::class, WidgetItemClickEvent.WidgetItemThirdClickEvent::class,
    WidgetItemClickEvent.WidgetItemFourthClickEvent::class, WidgetItemClickEvent.WidgetItemFifthClickEvent::class))
on(type).filter { widgetId == 6574 || widgetId == 6669 }.then {
    val duel = DuelArena.session(plr) ?: return@then
    val event = this
    val revision = duel.revision
    val amount = when (event) {
        is WidgetItemClickEvent.WidgetItemFirstClickEvent -> 1
        is WidgetItemClickEvent.WidgetItemSecondClickEvent -> 5
        is WidgetItemClickEvent.WidgetItemThirdClickEvent -> 10
        is WidgetItemClickEvent.WidgetItemFourthClickEvent -> -1
        else -> 0
    }
    if (amount != 0) duel.stake(plr, index, itemId, amount, widgetId == 6669)
    else plr.overlays.open(object : NumberInput() {
        override fun input(player: Player, value: Int) {
            if (DuelArena.session(player) === duel && duel.revision == revision && value > 0)
                duel.stake(player, event.index, event.itemId, value, event.widgetId == 6669)
        }
    })
}

playerStopAttack { attack -> DuelArena.stopAttack(this, attack) }
playerDefence { DuelArena.blockImpact(player, other, source) }.then { damage = null }
