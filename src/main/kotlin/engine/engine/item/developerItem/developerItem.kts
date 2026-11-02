package engine.item.developerItem

import api.predef.*
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.event.impl.UseItemEvent.*

item1(DeveloperItemHandler.ITEM_ID) { DeveloperItemHandler.position(plr) }
item2(DeveloperItemHandler.ITEM_ID) { DeveloperItemHandler.automation(plr) }
item3(DeveloperItemHandler.ITEM_ID) { DeveloperItemHandler.navigation(plr) }
item4(DeveloperItemHandler.ITEM_ID) { DeveloperItemHandler.repeat(plr) }

on(ItemOnPlayerEvent::class).filter { usedItemId == DeveloperItemHandler.ITEM_ID }.then {
    DeveloperItemHandler.player(plr, targetPlr)
}
on(ItemOnObjectEvent::class).filter { usedItemId == DeveloperItemHandler.ITEM_ID }.then {
    DeveloperInspection.objectInfo(plr, gameObject)
}
on(ItemOnNpcEvent::class).filter { usedItemId == DeveloperItemHandler.ITEM_ID }.then {
    DeveloperInspection.npcInfo(plr, targetNpc)
}
on(ItemOnGroundItemEvent::class).filter { usedItemId == DeveloperItemHandler.ITEM_ID }.then {
    DeveloperInspection.groundInfo(plr, groundItem)
}
on(ItemOnItemEvent::class).filter { usedItemId == DeveloperItemHandler.ITEM_ID }.then {
    DeveloperInspection.inventoryItem(plr, targetItemIndex, targetItemId)
}
on(LogoutEvent::class) { DeveloperItemHandler.forget(plr) }
