package game.skill.smithing.makeCannonball

import api.predef.*
import api.predef.ext.*
import game.skill.smithing.Smithing
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.AMMO_MOULD
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.CANNONBALL
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.LEVEL
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.STEEL_BAR
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue

/* Use a steel bar on a furnace to make cannonballs. */
for (furnaceId in Smithing.FURNACE_OBJECTS) {
    useItem(STEEL_BAR).onObject(furnaceId) {
        when {
            plr.smithing.level < LEVEL ->
                plr.sendMessage("You need a Smithing level of $LEVEL to make cannonballs.")

            !plr.inventory.contains(AMMO_MOULD) ->
                plr.sendMessage("You need a cannonball mould to make cannonballs.")

            else -> plr.overlays.open(object : MakeItemDialogue(CANNONBALL) {
                override fun make(plr: Player, id: Int, index: Int, forAmount: Int) =
                    plr.submitAction(MakeCannonballAction(plr, forAmount))
            })
        }
    }
}
