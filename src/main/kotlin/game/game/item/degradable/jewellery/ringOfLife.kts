package game.item.degradable.jewellery

import api.predef.*
import game.item.degradable.DegradableItems
import game.item.degradable.DegradableItems.ringOfLifeActive
import game.skill.magic.Magic.teleport
import game.skill.magic.teleportSpells.TeleportStyle
import io.luna.Luna
import io.luna.game.event.impl.CombatDamageReceivedEvent
import io.luna.game.model.item.Equipment

// Activate ring of life if damage is taken which results in health falling below 10%.
on(CombatDamageReceivedEvent::class) {
    if (plr.equipment.ring?.id == 2570 && plr.healthPercent <= DegradableItems.RING_OF_LIFE_HEALTH_PERCENT &&
        !plr.combat.magic.isTeleBlocked) {
        plr.ringOfLifeActive = true
        plr.teleport(Luna.settings().game().startingPosition(), TeleportStyle.REGULAR) { plr.ringOfLifeActive = false }
        plr.sendMessage("Your Ring of Life saves you and is destroyed in the process.")
        plr.equipment[Equipment.RING] = null
    }
}