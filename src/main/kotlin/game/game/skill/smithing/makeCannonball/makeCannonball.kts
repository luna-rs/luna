package game.skill.smithing.makeCannonball

import api.predef.*
import api.predef.ext.*
import game.skill.smithing.Smithing
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.AMMO_MOULD
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.LEVEL
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.STEEL_BAR

/* Use a steel bar on a furnace to make cannonballs. */
for (furnaceId in Smithing.FURNACE_OBJECTS) {
    useItem(STEEL_BAR).onObject(furnaceId) {
        when {
            plr.smithing.level < LEVEL ->
                plr.sendMessage("You need a Smithing level of $LEVEL to make cannonballs.")

            !plr.inventory.contains(AMMO_MOULD) ->
                plr.sendMessage("You need a cannonball mould to make cannonballs.")

            else -> plr.submitAction(MakeCannonballAction(plr))
        }
    }
}
