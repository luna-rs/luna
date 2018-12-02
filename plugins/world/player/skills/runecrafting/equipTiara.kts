import api.*
import io.luna.game.event.impl.EquipmentChangeEvent
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import world.player.skills.runecrafting.Tiara

/**
 * Sends tiara altar config on login.
 */
fun loginUpdate(plr: Player) {
    val headId = plr.equipment.get(Equipment.HEAD)?.id
    val value = Tiara.ID_TO_TIARA[headId]?.config
    plr.sendConfig(491, value ?: 0)
}

/**
 * Sends tiara altar config on equipment change.
 */
fun equipmentUpdate(plr: Player, newItem: Item?) {
    val new = Tiara.ID_TO_TIARA[newItem?.id]
    plr.sendConfig(491, new?.config ?: 0)
}

/**
 * Forward to [loginUpdate].
 */
on(LoginEvent::class).run { loginUpdate(it.plr) }

/**
 * Forward to [equipmentUpdate] if the changed equipment was in the head slot.
 */
on(EquipmentChangeEvent::class).run {
    if (it.index == Equipment.HEAD) {
        equipmentUpdate(it.plr, it.newItem)
        it.terminate()
    }
}

