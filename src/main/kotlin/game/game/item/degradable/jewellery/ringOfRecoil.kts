package game.item.degradable.jewellery

import api.predef.*
import game.item.degradable.DegradableItems
import game.item.degradable.DegradableItems.ringOfRecoilCharges
import io.luna.game.event.impl.CombatDamageReceivedEvent
import io.luna.game.model.item.Equipment
import kotlin.math.floor

// Reflect damage whenever the player is wearing the ring and is hit for > 0.
on(CombatDamageReceivedEvent::class) {
    val rawAmount = damage.rawAmount
    if (plr.equipment.ring?.id == 2550 && damage.attacker.isAlive && rawAmount > 0) {
        var reflected = (floor(rawAmount * 0.10) + 1).toInt()
        val charges = plr.ringOfRecoilCharges
        if (reflected > charges) {
            reflected = charges
        }
        plr.ringOfRecoilCharges -= reflected
        damage.attacker.damage(reflected)
        if (plr.ringOfRecoilCharges <= 0) {
            plr.equipment[Equipment.RING] = null
            plr.sendMessage("Your Ring of Recoil shatters.")
            plr.ringOfRecoilCharges = DegradableItems.RING_OF_RECOIL_CHARGES
        }
    }
}