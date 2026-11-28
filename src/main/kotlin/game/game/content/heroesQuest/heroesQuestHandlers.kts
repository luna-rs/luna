package game.content.heroesQuest

import api.combat.death.DeathHookHandler
import api.predef.*
import game.content.heroesQuest.HeroesGang as Gang
import game.content.heroesQuest.HeroesQuest as Quest
import game.obj.entrances.Entrances
import io.luna.game.event.impl.DefaultTabOpenedEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

on(DefaultTabOpenedEvent::class).filter { tab == TabIndex.QUEST }.then { Quest.refresh(plr) }
button(Quest.JOURNAL_BUTTON) { Quest.journal(plr) }
npc1(Quest.ACHIETTIES) { Quest.achietties(plr, targetNpc) }

npc1(Gang.CHARLIE) { Gang.charlie(plr, targetNpc) }
npc1(Gang.GRUBOR) { Gang.grubor(plr, targetNpc) }
npc1(Gang.TROBERT) { Gang.trobert(plr, targetNpc) }
npc1(Gang.GARV) { Gang.garv(plr, targetNpc) }
npc1(Gang.GRIP) { Gang.grip(plr, targetNpc) }

useItem(Gang.SIDE_KEY).onObject(Gang.SIDE_DOOR) {
    if (plr.inventory[usedItemIndex]?.id == Gang.SIDE_KEY) Gang.useSideKey(plr, gameObject)
}
useItem(Gang.TREASURE_KEY).onObject(Gang.TREASURE_DOOR) {
    if (plr.inventory[usedItemIndex]?.id == Gang.TREASURE_KEY) Gang.useTreasureKey(plr, gameObject)
}
object1(Gang.CABINET) { Gang.inspectCabinet(plr, gameObject) }
object1(Gang.CABINET_OPEN) { Gang.inspectCabinet(plr, gameObject) }
object2(Gang.CABINET_OPEN) { Entrances.replace(gameObject, Gang.CABINET) }
object1(Gang.CHEST) { Entrances.replace(gameObject, Gang.CHEST_OPEN, 30) }
object1(Gang.CHEST_OPEN) { Gang.searchChest(plr, gameObject) }
object2(Gang.CHEST_OPEN) { Entrances.replace(gameObject, Gang.CHEST) }

DeathHookHandler.addNpcHook(Gang.GRIP) {
    death { Gang.gripDefeated(victim, source as? Player) }
    DeathHookHandler.defaultNpcHook?.invoke(this)
}
DeathHookHandler.addNpcHook(Quest.FIRE_BIRD) {
    death { (source as? Player)?.let { HeroesProofs.dropFeather(it, victim) } }
    DeathHookHandler.defaultNpcHook?.invoke(this)
}
