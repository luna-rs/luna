package game.content.castleWars

import api.predef.*
import api.predef.ext.*
import game.content.castleWars.CastleWarsMatch.Team
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.event.impl.PlayerClickEvent.PlayerFifthClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemSecondClickEvent
import io.luna.game.model.mob.interact.InteractionPolicy
import api.combat.player.PlayerCombatHandler.playerStopAttack
import api.combat.player.PlayerCombatHandler.playerDefence

import io.luna.game.model.mob.Player

playerStopAttack { attack ->
    val controller = controllers.primary as? CastleWarsController
    controller != null && !controller.combat(attack.victim)
}
playerDefence {
    val controller = player.controllers.primary as? CastleWarsController
        ?: (other as? Player)?.controllers?.primary as? CastleWarsController
    controller != null && (other !is Player || !controller.game.canFight(player, other))
}.then { damage = null }


on(ServerLaunchEvent::class) { CastleWars.launch() }
on(LoginEvent::class) { CastleWars.game.login(plr) }
object1(4387) { CastleWars.enter(plr, Team.SARADOMIN) }
object1(4388) { CastleWars.enter(plr, Team.ZAMORAK) }
object1(4408) { CastleWars.enter(plr, null) }
for (id in listOf(4389, 4390)) object1(id) { CastleWars.game.exit(plr) }
object1(4406) { CastleWars.game.quit(plr, Team.SARADOMIN) }
object1(4407) { CastleWars.game.quit(plr, Team.ZAMORAK) }
for (id in listOf(4377, 4378, 4900, 4901, 4902, 4903)) object1(id) { CastleWars.flag(plr, gameObject) }
for (id in listOf(4415, 4417, 4418, 4419, 4420, 4471, 4472, 4911, 4912, 6280, 6281)) {
    object1(id) { CastleWars.route(plr, gameObject) }
}
for (id in CastleWarsGame.TABLES.keys) object1(id) { CastleWars.game.supply(plr, id, gameObject.position) }
item1(4049) { CastleWars.game.bandage(plr) }
useItem(4049).onPlayer { CastleWars.game.bandage(plr, targetPlr) }
on(PlayerFifthClickEvent::class, InteractionPolicy.STANDARD_SIZE_BIF)
    .filter { CastleWars.game.match.isPlaying(plr) }
    .then { CastleWars.game.takeFrom(plr, targetPlr) }
for (id in listOf(4469, 4470)) object1(id) { CastleWars.siege.spawnDoor(plr, gameObject) }
for (id in 4423..4434) {
    object1(id) { CastleWars.siege.mainDoor(plr, gameObject, repair = id >= 4431) }
    object2(id) { CastleWars.siege.mainDoor(plr, gameObject, attack = true) }
    useItem(4051).onObject(id) { CastleWars.siege.mainDoor(plr, gameObject, repair = true) }
}
for (id in 4465..4468) {
    object1(id) { CastleWars.siege.sideDoor(plr, gameObject) }
    object2(id) { CastleWars.siege.sideDoor(plr, gameObject, true) }
}
for (id in listOf(4437, 4438, 4448)) {
    object1(id) { CastleWars.siege.rocks(plr, gameObject) }
    useItem(4045).onObject(id) { CastleWars.siege.rocks(plr, gameObject, true) }
}
item1(4053) { CastleWars.siege.placeBarricade(plr) }
for (npc in 1532..1535) for (item in listOf(590, 4045, 1929)) {
    useItem(item).onNpc(npc) { CastleWars.siege.barricade(plr, targetNpc, item) }
}
for (id in listOf(4381, 4382, 4385, 4386, 4904, 4905)) {
    object1(id) { CastleWars.siege.catapult(plr, gameObject) }
    for (item in listOf(4043, 4045, 4051, 590, 1929)) {
        useItem(item).onObject(id) { CastleWars.siege.catapult(plr, gameObject, item) }
    }
}
for (id in 11321..11324) button(id) {
    val aim = plr.overlays.getOverlay(CatapultInterface::class.java) ?: return@button
    val sign = if (CastleWars.game.match.team(plr) == Team.SARADOMIN) 1 else -1
    when (id) {
        11321 -> aim.y = (aim.y + sign).coerceIn(-30, 30)
        11322 -> aim.y = (aim.y - sign).coerceIn(-30, 30)
        11323 -> aim.x = (aim.x - sign).coerceIn(-30, 30)
        11324 -> aim.x = (aim.x + sign).coerceIn(-30, 30)
    }
    aim.refresh(plr)
}
button(11329) {
    plr.overlays.getOverlay(CatapultInterface::class.java)?.let { CastleWars.siege.fire(plr, it) }
}
object1(4411) {
    if (CastleWars.game.match.isPlaying(plr) && !plr.status.isImmobilized() && plr.position.isWithinDistance(gameObject.position, 2)) {
        plr.animation(io.luna.game.model.mob.block.Animation(741))
        plr.move(gameObject.position)
    }
}
useItem(1925).onObject(4482) {
    if (CastleWars.game.match.isPlaying(plr)) plr.inventory.replace(1925, 1929)
}
for (id in listOf(4446, 4447)) useItem(954).onObject(id) { CastleWars.siege.rope(plr, gameObject) }
object1(4444) { CastleWars.siege.climbRope(plr, gameObject) }
object1(4483) { plr.bank.open() }
object1(4484) {
    plr.sendMessage("Castle Wars: Saradomin ${CastleWars.game.match.score(Team.SARADOMIN)}, Zamorak ${CastleWars.game.match.score(Team.ZAMORAK)}.")
}
npc1(1526) {
    plr.newDialogue().npc(1526, "Welcome to Castle Wars! Capture the enemy standard and bring it to your own.",
        "Leave hats, capes and food in the bank. Supplies are available inside.",
        "Matches last twenty minutes. Winners earn three tickets; a draw earns two.").options(
        "Exchange my tickets.", { CastleWarsRewards.open(it) },
        "Collect my tickets.", { CastleWars.game.claimTickets(it); it.overlays.closeWindows() },
        "I'm ready to play.", { it.overlays.closeWindows() }).open()
}
npc2(1526) { CastleWarsRewards.open(plr) }
on(WidgetItemFirstClickEvent::class).filter { widgetId == 12392 || widgetId == 13294 }.then {
    if (plr.overlays.getOverlay(CastleWarsRewardInterface::class.java) != null) {
        CastleWarsRewards.prices[itemId]?.let { plr.sendMessage("This item exchanges for $it Castle Wars tickets.") }
    }
}
on(WidgetItemSecondClickEvent::class).filter { widgetId == 12392 || widgetId == 13294 }.then {
    if (widgetId == 12392 && CastleWarsRewards.prices.keys.elementAtOrNull(index) == itemId ||
        widgetId == 13294 && plr.inventory.contains(index, itemId)) {
        CastleWarsRewards.exchange(plr, itemId, widgetId == 12392)
    }
}
