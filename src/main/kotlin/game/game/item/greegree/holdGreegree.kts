package game.item.greegree

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.EquipmentChangeEvent
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.RegionChangedEvent
import io.luna.game.model.EntityState
import io.luna.game.model.item.Equipment

// Runs after the weapon handler, which gives the player the movement animations of what they now hold.
on(EquipmentChangeEvent::class, EventPriority.NORMAL) {
    if (index != Equipment.WEAPON) return@on
    val greegree = newItem?.let { Greegree.ITEMS[it.id] }
    when {
        greegree == null -> if (oldItem?.id in Greegree.ITEMS) Greegree.reset(plr)
        Greegree.worksAt(plr.position) -> greegree.transform(plr)
        else -> {
            // Out of Zooknock's reach it does nothing and goes back in the pack.
            plr.equipment.unequip(Equipment.WEAPON)
            plr.sendMessage("You attempt to use the monkey greegree but nothing happens.")
        }
    }
}

// A greegree held away from Ape Atoll or the zoo lets go of the player, falling to the floor if there's no room.
on(RegionChangedEvent::class) {
    val greegree = Greegree.held(plr) ?: return@on
    if (Greegree.worksAt(plr.position)) {
        if (plr.transformId != greegree.npc) greegree.transform(plr)
        return@on
    }
    // Checked first, since a failed unequip sends its own full-inventory message.
    if (plr.inventory.hasSpaceFor(plr.equipment[Equipment.WEAPON])) {
        plr.equipment.unequip(Equipment.WEAPON)
    } else {
        plr.equipment[Equipment.WEAPON] = null
        world.addItem(greegree.id, 1, plr.position, plr)
    }
    plr.sendMessage("The monkey greegree wrenches itself from your hand as its power begins to fade.")
}

on(LoginEvent::class) {
    // The weapon handler also sets the movement animations when the player logs in, so the monkey's are set on the
    // next tick.
    world.scheduleOnce(1) {
        val greegree = Greegree.held(plr)
        if (plr.state == EntityState.ACTIVE && greegree != null && Greegree.worksAt(plr.position)) {
            greegree.transform(plr)
        }
    }
}
