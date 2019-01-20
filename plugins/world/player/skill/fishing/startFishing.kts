import api.predef.*
import io.luna.game.event.entity.player.NpcClickEvent
import io.luna.game.event.entity.player.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.entity.player.NpcClickEvent.NpcSecondClickEvent
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

on(NpcFirstClickEvent::class)
    .match(312)
    .then { fish(this, Tool.LOBSTER_POT) }

on(NpcFirstClickEvent::class)
    .match(313)
    .then { fish(this, Tool.BIG_NET) }

on(NpcFirstClickEvent::class)
    .match(316, 319)
    .then { fish(this, Tool.SMALL_NET) }

on(NpcFirstClickEvent::class)
    .match(1174)
    .then { fish(this, Tool.MONKFISH_NET) }

/**
 * Second click fishing spots.
 */
on(NpcSecondClickEvent::class)
    .match(309, 316, 319, 310, 311, 314, 315, 317, 318)
    .then { fish(this, Tool.FISHING_ROD) }

on(NpcSecondClickEvent::class)
    .match(312)
    .then { fish(this, Tool.HARPOON) }

on(NpcSecondClickEvent::class)
    .match(313)
    .then { fish(this, Tool.SHARK_HARPOON) }
