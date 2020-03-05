package world.player.skill.runecrafting.enterAltar

import api.predef.*
import io.luna.game.event.impl.ItemOnObjectEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player

/**
 * The entering an altar with a talisman animation.
 */
val talismanAnimation = Animation(827)

/**
 * Moves the player inside an altar using a talisman.
 */
fun talismanEnter(plr: Player, altar: Altar) {
    plr.sendMessage("You hold the ${itemDef(altar.talisman).name} towards the mysterious ruins.")
    plr.animation(talismanAnimation)
    plr.walking.isLocked = true

    world.scheduleOnce(3) {
        plr.sendMessage("You feel a powerful force take hold of you...")
        plr.teleport(altar.enter)
        plr.walking.isLocked = false
    }
}

/**
 * Moves the player inside an altar using a tiara.
 */
fun tiaraEnter(plr: Player, headId: Int, objectId: Int) {
    val altar = Altar.TIARA_TO_ALTAR[headId]
    if (altar != null && altar.id == objectId) {
        plr.sendMessage("You feel a powerful force take hold of you...")
        plr.teleport(altar.enter)
    }
}

/**
 * Moves the player inside an altar using a portal.
 */
fun portalExit(plr: Player, altar: Altar) {
    plr.sendMessage("You step through the portal...")
    plr.teleport(altar.exit)
}

/**
 * Intercept event for entering with talismans.
 */
on(ItemOnObjectEvent::class) {
    val altar = Altar.TALISMAN_TO_ALTAR[itemId]
    if (altar != null && altar.id == objectId) {
        talismanEnter(plr, altar)
    }
}

/**
 * Intercept event for entering with tiaras.
 */
on(ObjectFirstClickEvent::class) {
    val headId = plr.equipment.get(Equipment.HEAD)?.id
    if (headId != null && itemDef(headId).name.contains("tiara")) {
        tiaraEnter(plr, headId, id)
    }
}

/**
 * Intercept event for exiting through altar portals.
 */
on(ObjectFirstClickEvent::class)
    .filter { objectDef(id).name == "Portal" }
    .then {
        val altar = Altar.PORTAL_TO_ALTAR[id]
        if (altar != null) {
            portalExit(plr, altar)
        }
    }

