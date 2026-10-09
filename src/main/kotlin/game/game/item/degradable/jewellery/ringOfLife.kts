package game.item.degradable.jewellery

import api.predef.*
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.skill.magic.Magic.findLandingTile
import game.skill.magic.Magic.teleport
import game.skill.magic.teleportSpells.TeleportStyle
import io.luna.Luna
import io.luna.game.event.impl.DamageReceivedEvent
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.block.Hit.HitType

/**
 * The Ring of life item id.
 */
val RING_OF_LIFE = 2570

// Activate ring of life when a hit leaves the player alive on a tenth of their hitpoints or less. Poison doesn't count.
on(DamageReceivedEvent::class) {
    if (plr.equipment.ring?.id != RING_OF_LIFE || hit.type == HitType.POISON || !plr.isAlive ||
        plr.health > plr.totalHealth / 10 || plr.wildernessLevel > 30 || plr.status.isTeleBlocked()) {
        return@on
    }
    val destination = findLandingTile(Luna.settings().game().startingPosition(), 2)
    plr.teleport(destination, TeleportStyle.REGULAR, maxWildernessLevel = 30) {
        plr.sendMessage("Your Ring of Life saves you and is destroyed in the process.")
        plr.equipment[Equipment.RING] = null
    }
}
