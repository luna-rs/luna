package game.skill.fishing.catchFish

import api.predef.*
import io.luna.game.event.impl.NpcClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent

/**
 * Submits a [CatchFishAction] to start fishing.
 */
fun fish(msg: NpcClickEvent, tool: Tool) {
    msg.plr.submitAction(CatchFishAction(msg, tool))
}

// First click fishing spots.
on(NpcFirstClickEvent::class)
    .match(233, 234, 235, 236)
    .then { fish(this, Tool.FISHING_ROD) }

on(NpcFirstClickEvent::class)
    .match(309, 310, 311, 314, 315, 317, 318, 328, 329, 331)
    .then { fish(this, Tool.FLY_FISHING_ROD) }

on(NpcFirstClickEvent::class)
    .match(312, 321, 324, 333)
    .then { fish(this, Tool.LOBSTER_POT) }

on(NpcFirstClickEvent::class)
    .match(313, 322, 334)
    .then { fish(this, Tool.BIG_NET) }

on(NpcFirstClickEvent::class)
    .match(316, 319, 320, 323, 325, 326, 327, 330, 332)
    .then { fish(this, Tool.SMALL_NET) }

npc1(1174) {
    fish(this, Tool.MONKFISH_NET)
}

// Second click fishing spots.
on(NpcSecondClickEvent::class)
    .match(309, 310, 311, 314, 315, 316, 317, 318, 319, 320, 323, 325, 326, 327, 328, 329, 330, 331, 332)
    .then { fish(this, Tool.FISHING_ROD) }

on(NpcSecondClickEvent::class)
    .match(32, 312, 321, 324, 333)
    .then { fish(this, Tool.HARPOON) }

on(NpcSecondClickEvent::class)
    .match(313, 322, 334)
    .then { fish(this, Tool.SHARK_HARPOON) }
