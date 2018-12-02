import api.*
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
    .args(233, 234, 235, 236)
    .run { fish(it, Tool.FISHING_ROD) }

on(NpcFirstClickEvent::class)
    .args(309, 310, 311, 314, 315, 317, 318)
    .run { fish(it, Tool.FLY_FISHING_ROD) }

on(NpcFirstClickEvent::class)
    .args(312)
    .run { fish(it, Tool.LOBSTER_POT) }

on(NpcFirstClickEvent::class)
    .args(313)
    .run { fish(it, Tool.BIG_NET) }

on(NpcFirstClickEvent::class)
    .args(316, 319)
    .run { fish(it, Tool.SMALL_NET) }

on(NpcFirstClickEvent::class)
    .args(1174)
    .run { fish(it, Tool.MONKFISH_NET) }

/**
 * Second click fishing spots.
 */
on(NpcSecondClickEvent::class)
    .args(309, 316, 319, 310, 311, 314, 315, 317, 318)
    .run { fish(it, Tool.FISHING_ROD) }

on(NpcSecondClickEvent::class)
    .args(312)
    .run { fish(it, Tool.HARPOON) }

on(NpcSecondClickEvent::class)
    .args(313)
    .run { fish(it, Tool.SHARK_HARPOON) }
