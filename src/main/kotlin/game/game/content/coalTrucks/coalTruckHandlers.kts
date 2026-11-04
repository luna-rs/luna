package game.content.coalTrucks

import api.predef.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject

on(LoginEvent::class) { CoalTrucks.restore(plr) }
object1(CoalTrucks.TRUCK) { CoalTrucks.withdraw(plr, gameObject) }
object2(CoalTrucks.TRUCK) { CoalTrucks.investigate(plr, gameObject) }

useItem(CoalTrucks.COAL).onObject(CoalTrucks.TRUCK) {
    CoalTrucks.deposit(plr, gameObject, usedItemIndex, usedItemId)
}

on(ItemOnObjectEvent::class, interaction = { _, target ->
    if (target is GameObject && target.id == CoalTrucks.TRUCK) InteractionPolicy.STANDARD_SIZE
    else InteractionPolicy.UNSPECIFIED
}).filter { objectId == CoalTrucks.TRUCK && usedItemId != CoalTrucks.COAL }
    .then { CoalTrucks.deposit(plr, gameObject, usedItemIndex, usedItemId) }
