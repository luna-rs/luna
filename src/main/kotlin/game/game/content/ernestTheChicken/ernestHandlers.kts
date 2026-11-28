package game.content.ernestTheChicken

import api.predef.*
import game.player.Sound
import io.luna.game.event.impl.DefaultTabOpenedEvent
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

on(DefaultTabOpenedEvent::class).filter { tab == TabIndex.QUEST }.then { ErnestTheChicken.refresh(plr) }
on(LoginEvent::class) { ManorLeverPuzzle.refreshDoors(plr) }
button(ErnestTheChicken.JOURNAL_BUTTON) { ErnestTheChicken.journal(plr) }
npc1(ErnestTheChicken.VERONICA) { ErnestTheChicken.veronica(plr, targetNpc) }
npc1(ErnestTheChicken.PROFESSOR) { ErnestTheChicken.professor(plr, targetNpc) }

object1(DraynorManor.COMPOST) { plr.newDialogue().player("I'm not digging through that with my hands!").open() }
useItem(DraynorManor.SPADE).onObject(DraynorManor.COMPOST) { DraynorManor.dig(plr, gameObject) }
object1(DraynorManor.CLOSET) {
    plr.sendMessage("The door is locked.")
    plr.playSound(Sound.LOCKED)
}
useItem(DraynorManor.KEY).onObject(DraynorManor.CLOSET) { DraynorManor.crossCloset(plr, gameObject) }
useItem(DraynorManor.POISON).onItem(DraynorManor.FISH_FOOD) { DraynorManor.mixFood(plr) }
object1(DraynorManor.FOUNTAIN) { DraynorManor.searchFountain(plr) }
useItem(DraynorManor.POISONED_FISH_FOOD).onObject(DraynorManor.FOUNTAIN) { DraynorManor.feedFish(plr) }

for (id in listOf(DraynorManor.BOOKCASE_SOUTH, DraynorManor.BOOKCASE_NORTH)) {
    object1(id) { DraynorManor.searchBookcase(plr, gameObject) }
}
object1(DraynorManor.SECRET_LEVER, { _, _ -> ManorLeverPuzzle.leverReach }) {
    DraynorManor.pullSecretLever(plr, gameObject)
}
for (lever in ManorLeverPuzzle.Lever.entries) {
    object1(lever.id, { _, _ -> ManorLeverPuzzle.leverReach }) { ManorLeverPuzzle.pull(plr, lever) }
    object2(lever.id, { _, _ -> ManorLeverPuzzle.leverReach }) { ManorLeverPuzzle.inspect(plr, lever) }
}
for (door in ManorLeverPuzzle.Door.entries) {
    object1(door.id, { _, _ -> ManorLeverPuzzle.doorReach }) { ManorLeverPuzzle.cross(plr, door) }
}
