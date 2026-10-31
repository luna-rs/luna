package game.combat.npcHooks.giantMole

import api.combat.npc.NpcCombatHandler.combat
import game.combat.npcHooks.giantMole.GiantMole.burrowing
import game.player.Sound
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Player

combat(GiantMole.ID) {
    attack {
        combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
        melee()
    }
    defend {
        soundId = null
        val hit = damage ?: return@defend
        if (npc.burrowing) {
            // It's underground, out of reach.
            damage = null
            animationId = null
            return@defend
        }
        val player = other as? Player
        val amount = hit.rawAmount
        if (amount > 0 && amount < npc.health) player?.playSound(Sound.MOLE_HIT)
        if (GiantMole.shouldBurrow(npc, hit)) {
            animationId = null
            GiantMole.burrow(npc)
        }
    }
}
