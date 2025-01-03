package world.player.crystalChest

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

/**
 * Spawn crystal chest object at home area.
 */
on(ServerLaunchEvent::class) {
    world.addObject(id = 172,
                    x = 3095,
                    y = 3247,
                    z = 0)
}

/**
 * Use all crystal keys when the chest is clicked on.
 */
object1(172) {
    plr.submitAction(OpenCrystalChestAction(plr, gameObject, false))
}

/**
 * Use only a single crystal key when the item is used on the object.
 */
useItem(989).onObject(172) {
    plr.submitAction(OpenCrystalChestAction(plr, gameObject, true))
}

/**
 * Open the make item dialogue when crystal key halves are used on each other.
 */
useItem(985).onItem(987) {
    plr.interfaces.open(object : MakeItemDialogueInterface(989) {
        override fun makeItem(player: Player, id: Int, index: Int, forAmount: Int) {
            plr.submitAction(MakeCrystalKeyActionItem(player, forAmount))
        }
    })
}