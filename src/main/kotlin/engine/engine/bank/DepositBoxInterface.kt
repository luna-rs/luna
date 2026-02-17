package engine.bank

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.InventoryOverlayInterface

/**
 * An [InventoryOverlayInterface] implementation representing the deposit box interface.
 *
 * @author lare96 
 */
class DepositBoxInterface : InventoryOverlayInterface(4465, 197) {

    override fun onOpen(plr: Player) {
        plr.inventory.setSecondaryWidget(7423)
        plr.inventory.updateSecondaryWidget(plr)
    }

    override fun onClose(plr: Player) {
        plr.inventory.clearSecondaryWidget()
    }
}