package game.item.degradable.combat

import api.predef.*
import io.luna.game.event.impl.UseItemEvent.ItemOnNpcEvent
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.interact.InteractionPolicy

on(ItemOnNpcEvent::class, { _, target ->
    if (target is Npc && target.id in BarrowsRepair.REPAIRERS) InteractionPolicy.STANDARD_SIZE
    else InteractionPolicy.UNSPECIFIED
}).filter {
    targetNpc.id in BarrowsRepair.REPAIRERS
}.then {
    BarrowsRepair.offer(plr, targetNpc, usedItemIndex)
}
