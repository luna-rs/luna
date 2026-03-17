package game.content.crystalChest

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue

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
    plr.overlays.open(object : MakeItemDialogue(989) {
        override fun make(player: Player, id: Int, index: Int, forAmount: Int) {
            plr.submitAction(MakeCrystalKeyActionItem(player, forAmount))
        }
    })
}