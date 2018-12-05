import api.predef.*
import io.luna.game.event.impl.NpcClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent
import world.player.skills.fishing.FishAction
import world.player.skills.fishing.Tool

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
    .then { fish(it, Tool.FISHING_ROD) }

on(NpcFirstClickEvent::class)
    .match(309, 310, 311, 314, 315, 317, 318)
    .then { fish(it, Tool.FLY_FISHING_ROD) }

on(NpcFirstClickEvent::class)
    .match(312)
    .then { fish(it, Tool.LOBSTER_POT) }

on(NpcFirstClickEvent::class)
    .match(313)
    .then { fish(it, Tool.BIG_NET) }

on(NpcFirstClickEvent::class)
    .match(316, 319)
    .then { fish(it, Tool.SMALL_NET) }

on(NpcFirstClickEvent::class)
    .match(1174)
    .then { fish(it, Tool.MONKFISH_NET) }

/**
 * Second click fishing spots.
 */
on(NpcSecondClickEvent::class)
    .match(309, 316, 319, 310, 311, 314, 315, 317, 318)
    .then { fish(it, Tool.FISHING_ROD) }

on(NpcSecondClickEvent::class)
    .match(312)
    .then { fish(it, Tool.HARPOON) }

on(NpcSecondClickEvent::class)
    .match(313)
    .then { fish(it, Tool.SHARK_HARPOON) }
