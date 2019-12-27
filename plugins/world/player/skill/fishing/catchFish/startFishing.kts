package world.player.skill.fishing.catchFish

import api.predef.*
import io.luna.game.event.impl.NpcClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent
import world.player.skill.fishing.FishAction
import world.player.skill.fishing.Tool

/**
 * Submits a [FishAction] to start fishing.
 */
fun fish(msg: NpcClickEvent, tool: Tool) {
    msg.plr.submitAction(FishAction(msg, tool))
}

/**
 * First click fishing spots.
 */
on(NpcFirstClickEvent::class)
    .match(233, 234, 235, 236)
    .then { fish(this, Tool.FISHING_ROD) }

on(NpcFirstClickEvent::class)
    .match(309, 310, 311, 314, 315, 317, 318)
    .then { fish(this, Tool.FLY_FISHING_ROD) }

npc1(312) {
    fish(this, Tool.LOBSTER_POT)
}

npc1(313) {
    fish(this, Tool.BIG_NET)
}

on(NpcFirstClickEvent::class)
    .match(316, 319)
    .then { fish(this, Tool.SMALL_NET) }

npc1(1174) {
    fish(this, Tool.MONKFISH_NET)
}

/**
 * Second click fishing spots.
 */
on(NpcSecondClickEvent::class)
    .match(309, 316, 319, 310, 311, 314, 315, 317, 318)
    .then { fish(this, Tool.FISHING_ROD) }

npc2(312) {
    fish(this, Tool.HARPOON)
}

npc2(313) {
    fish(this, Tool.SHARK_HARPOON)
}
