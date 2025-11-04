package game.minigame.partyRoom.dropParty

import game.minigame.partyRoom.dropParty.DropPartyOption.depositItems
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.InventoryOverlayInterface

/**
 * An interface representing the party room chest interface.
 *
 * @author lare96
 */
class DropPartyInterface : InventoryOverlayInterface(2156, 5063) {
    override fun onOpen(plr: Player) {
        plr.inventory.setSecondaryRefresh(5064)
        plr.inventory.refreshSecondary(plr)

        DropPartyOption.chest.items.refreshPrimary(plr)
        plr.depositItems.refreshPrimary(plr)
    }
}