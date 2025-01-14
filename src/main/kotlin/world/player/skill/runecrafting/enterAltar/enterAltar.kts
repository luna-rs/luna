package world.player.skill.runecrafting.enterAltar

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player

/**
 * The entering an altar with a talisman animation.
 */
val talismanAnimation = Animation(827)

/**
 * Moves the player inside an altar using a talisman.
 */
fun talismanEnter(plr: Player, altar: Altar) {
    plr.sendMessage("You hold the ${itemName(altar.talisman)} towards the mysterious ruins.")
    plr.animation(talismanAnimation)
    plr.walking.isLocked = true

    world.scheduleOnce(3) {
        plr.sendMessage("You feel a powerful force take hold of you...")
        plr.move(altar.enter)
        plr.walking.isLocked = false
    }
}

/**
 * Moves the player inside an altar using a tiara.
 */
fun tiaraEnter(plr: Player, altar: Altar) {
    plr.sendMessage("You feel a powerful force take hold of you...")
    plr.move(altar.enter)
}

/**
 * Moves the player inside an altar using a portal.
 */
fun portalExit(plr: Player, altar: Altar) {
    plr.sendMessage("You step through the portal...")
    plr.move(altar.exit)
}

/**
 * Intercept event for entering with talismans.
 */
Altar.ALL.forEach {
    useItem(it.talisman).onObject(it.id) {
        talismanEnter(plr, it)
    }
}

/**
 * Intercept event for entering with tiaras.
 */
Altar.ALL.forEach {
    object1(it.id) {
        val headId = plr.equipment.get(Equipment.HEAD)?.id
        if (headId == it.tiara) {
            tiaraEnter(plr, it)
        }
    }
}

/**
 * Intercept event for exiting through altar portals.
 */
Altar.ALL.forEach {
    object1(it.portal) {
        portalExit(plr, it)
    }
}

