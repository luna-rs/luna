package game.content.partyRoom.dropParty

import game.content.partyRoom.dropParty.DropPartyOption.depositItems
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.InventoryOverlayInterface

/**
 * An interface representing the party room chest interface.
 *
 * @author lare96
 */
class DropPartyInterface : InventoryOverlayInterface(2156, 5063) {
    override fun onOpen(plr: Player) {
        plr.inventory.setSecondaryWidget(5064)
        plr.inventory.updateSecondaryWidget(plr)

        DropPartyOption.chest.items.updatePrimaryWidget(plr)
        plr.depositItems.updatePrimaryWidget(plr)
    }

    override fun onClose(plr: Player) {
        plr.inventory.clearSecondaryWidget()

        // On logout the save is taken before windows close, so offers stay until the next login returns them.
        if (plr.state == EntityState.ACTIVE) {
            DropPartyOption.returnOffers(plr)
        }
    }
}