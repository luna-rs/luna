package game.content.treasureTrails

import api.predef.*
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.Npc
import io.luna.game.model.`object`.GameObject
import io.luna.game.event.impl.ItemClickEvent.ItemFifthClickEvent
import io.luna.game.event.impl.LogoutEvent
import api.combat.npc.NpcCombatHandler.combat
import api.combat.player.PlayerCombatHandler.playerStopAttack
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.attack.MagicCombatAttack

for (clue in TrailClues.all) item1(clue.id) { TreasureTrails.read(plr, clue.id) }
for (id in TrailClues.byCasket.keys) item1(id) { TreasureTrails.open(plr, id) }
for ((id, clue) in TrailClues.byAuxiliary) item1(id) {
    if (clue.puzzle) TrailPuzzle.open(plr, id) else TreasureTrails.read(plr, id)
}
for (clue in TrailClues.medium.filter { it.key != -1 }) {
    val objects = if (clue.kind == EasyClues.Kind.DRAWER) EasyClues.drawer else EasyClues.closedChests
    for (id in objects) useItem(clue.key).onObject(id) { TreasureTrails.search(plr, gameObject, 1) }
}
on(ItemFifthClickEvent::class).filter { interfaceId == 6980 }.then {
    TrailPuzzle.move(plr, index, id)
}
item1(2574) { TrailNavigation.open(plr) }
for (id in 6952..6955) button(id) { TrailNavigation.button(plr, id) }
button(6959) { TrailNavigation.button(plr, 6959) }
on(LogoutEvent::class) { TrailGuardians.leave(plr, reset = false) }

combat(1007) {
    attack { MagicCombatAttack(npc, TrailGuardians.owner(npc) ?: other, CombatSpell.FLAMES_OF_ZAMORAK, 4) }
    defend { if (!TrailGuardians.mayAttack(other, npc)) damage = damage?.cappedAt(0) }
}
playerStopAttack { attack ->
    val target = attack.victim
    target is Npc && !TrailGuardians.mayAttack(this, target)
}

// Run after ordinary dialogue so a clue's result remains visible, without replacing NPC services.
val npcPolicy = InteractionPolicySupplier { player, target ->
    if (target is Npc && (target.id in TrailNavigation.suppliers || TrailClues.all.any { it.npcId == target.id && player.inventory.contains(it.id) }))
        InteractionPolicy.STANDARD_SIZE else InteractionPolicy.UNSPECIFIED
}
val objectPolicy = InteractionPolicySupplier { player, target ->
    if (target is GameObject && TrailClues.all.any {
            (it.kind != null || it.targetObjects.isNotEmpty()) && it.position == target.position && player.inventory.contains(it.id)
        }) InteractionPolicy.STANDARD_SIZE else InteractionPolicy.UNSPECIFIED
}
on(NpcFirstClickEvent::class, interaction = npcPolicy) {
    TreasureTrails.talk(plr, targetNpc)
}
on(ObjectFirstClickEvent::class, interaction = objectPolicy) {
    TreasureTrails.search(plr, gameObject, 1)
}
on(ObjectSecondClickEvent::class, interaction = objectPolicy) {
    TreasureTrails.search(plr, gameObject, 2)
}
