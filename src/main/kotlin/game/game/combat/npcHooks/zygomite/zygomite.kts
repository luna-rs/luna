package game.combat.npcHooks.zygomite

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import api.predef.ext.schedule
import io.luna.Luna
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.EntityState
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * Connects native fungi picking, charged sprays and inventory refills to their ordinary interaction packets.
 * Dormant forms stay anchored and safe from combat while the shared script waits for an owned spray finish.
 *
 * @author lare96
 */
for ((fungus, _) in Zygomites.FORMS) {
    npc1(fungus, false) { Zygomites.pick(plr, targetNpc) }
    combat(fungus) {
        poisonCanKill = false
        defend { damage = null }
    }
}

for (form in Zygomites.FORMS.values) {
    combat(form) {
        attack {
            if (!npc.world.collisionManager.reached(npc, other, InteractionPolicy.STANDARD_SIZE) ||
                rand(Zygomites.SPORE_CHANCE - 1) == 0) {
                Zygomites.sporeAttack(npc, other)
            } else {
                // Crush is a headbutt interpretation; accuracy is checked against magic defence either way.
                combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
                melee(magicDefence = true)
            }
        }
        poisonCanKill = !Luna.settings().skills().slayerEquipmentNeeded()
        defend {
            if (Luna.settings().skills().slayerEquipmentNeeded()) {
                if (npc.health > Zygomites.FINISH_HEALTH &&
                    npc.health - (damage?.rawAmount ?: 0) <= Zygomites.FINISH_HEALTH) {
                    other.ifPlayer { it.sendMessage("The zygomite is on its last legs! Finish it quickly!") }
                }
                damage = damage?.cappedAt(npc.health - 1)
            }
        }
    }
    for (spray in Zygomites.FULL_SPRAY until Zygomites.EMPTY_SPRAY) {
        useItem(spray).onNpc(form) { Zygomites.spray(plr, targetNpc, usedItemIndex) }
    }
}

useItem(Zygomites.FUNGICIDE).onItem(Zygomites.EMPTY_SPRAY) {
    val index = if (usedItemId == Zygomites.EMPTY_SPRAY) usedItemIndex else targetItemIndex
    val refill = if (usedItemId == Zygomites.FUNGICIDE) usedItemIndex else targetItemIndex
    Zygomites.refill(plr, index, refill)
}

on(ServerLaunchEvent::class) {
    world.schedule(1) {
        for (npc in world.npcs) if (npc.state == EntityState.ACTIVE && npc.id in Zygomites.FORMS) {
            npc.navigator.cancel()
            npc.walking.clear()
            npc.walking.isLocked = true
            npc.combat.isDisabled = true
            npc.combat.target = null
        }
    }
}
