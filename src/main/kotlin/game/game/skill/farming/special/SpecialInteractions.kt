package game.skill.farming.special

import game.skill.farming.action.*
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Harvests the belladonna in the belladonna patch [patchObject], if the player is wearing gloves.
 */
fun pickBelladonna(plr: Player, patchObject: GameObject) {
    if (plr.equipment[Equipment.HANDS] == null) {
        plr.sendMessage("You need to wear gloves to pick this.")
        return
    }
    plr.submitAction(HarvestAction(plr, patchObject))
}
