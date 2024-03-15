package world.minigame.party_room.drop_party

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.InventoryOverlayInterface
import world.minigame.party_room.drop_party.DropPartyOption.depositItems

/**
 * An interface representing the party room chest interface.
 */
class DropPartyInterface : InventoryOverlayInterface(2156, 5063) {
    override fun onOpen(plr: Player) {
        plr.inventory.setSecondaryRefresh(5064)
        plr.inventory.refreshSecondary(plr)

        DropPartyOption.chest.items.refreshPrimary(plr)
        plr.depositItems.refreshPrimary(plr)
    }
}