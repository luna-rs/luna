package game.content.fishingTrawler

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemSecondClickEvent

object1(2178) { FishingTrawler.board(plr) }
object1(2179) { FishingTrawler.leave(plr); plr.move(FishingTrawler.DOCK) }
object1(2166) { FishingTrawler.claim(plr) }
on(WidgetItemFirstClickEvent::class).filter { widgetId == 4640 }.then {
    FishingTrawler.takeCatch(plr, index, itemId, false)
}
on(WidgetItemSecondClickEvent::class).filter { widgetId == 4640 }.then {
    FishingTrawler.takeCatch(plr, index, itemId, true)
}
object1(255) { plr.sendMessage("It seems the winch is jammed - I can't move it.") }
for (id in listOf(2159, 2160)) object1(id) { FishingTrawler.escape(plr) }
object1(2174) { FishingTrawler.ladder(plr, gameObject, true) }
object1(2175) { FishingTrawler.ladder(plr, gameObject, false) }
for (id in listOf(2167, 2172, 2173)) {
    object1(id) { FishingTrawler.repair(plr, gameObject) }
}
useItem(1941).onObject(2167) { FishingTrawler.repair(plr, gameObject) }
for (id in listOf(2172, 2173)) useItem(954).onObject(id) { FishingTrawler.repair(plr, gameObject) }
for (id in listOf(2164, 2165)) object1(id) { plr.sendMessage("The net is not damaged.") }
item1(583) { FishingTrawler.bail(plr, false) }
item1(585) { FishingTrawler.bail(plr, true) }
npc1(463) {
    plr.newDialogue().npc(463, "Good day to you! Fancy hitting the high seas?").options(
        "What fish do you catch?", {
            it.newDialogue().npc(463, "All sorts of fish from the sea bed!",
                "With enough experience you might find turtles or manta rays.",
                "You need at least level 15 Fishing to claim a catch.").open()
        },
        "Your boat doesn't look too safe.", {
            it.newDialogue().npc(463, "She's full of holes, but that won't stop us!",
                "Fill the leaks with swamp paste and bail out the flooded hold.",
                "Bring rope for the nets. Higher Crafting makes repairs easier.").open()
        },
        "What's swamp paste?", {
            it.newDialogue().npc(463, "Swamp tar mixed with flour, heated over a fire.",
                "You can find swamp tar in the swamps south of Lumbridge.").open()
        },
        "I'll help. Let's go!", {
            it.newDialogue().npc(463, "Meet me on board. Bring swamp paste, rope and a bailing bucket.",
                "The trip takes ten minutes. Collect your catch from the net when we return.",
                "If we sink, grab a floating barrel to make it back to shore.").open()
        }).open()
}
for (id in listOf(464, 465)) npc1(id) {
    plr.newDialogue().npc(id, "Whoooahh sailor!").options(
        "I've had enough, take me back.", { player ->
            player.newDialogue().npc(id, "We're too far out now. It'd be dangerous.").options(
                "I insist Murphy, take me back.", { FishingTrawler.shipwreck(it) },
                "Okay then Murphy, just keep us afloat.", { it.overlays.closeWindows() }).open()
        },
        "How are you doing Murphy?", {
            it.newDialogue().npc(id, "It's a fierce sea today. Keep those holes filled!").open()
        }).open()
}
on(LoginEvent::class) { FishingTrawler.login(plr) }
on(LogoutEvent::class) { FishingTrawler.abandon(plr) }
