package game.combat.npcHooks.dragon

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import game.combat.npcHooks.dragon.KingBlackDragonBreath.Kind

// Lost City selects melee, ordinary fire and special breath equally at close range. Each special
// breath is equally likely; outside melee reach KBD uses only the ordinary fire projectile.
combat(50) {
    attack {
        if (!npc.isWithinDistance(other, 1)) {
            KingBlackDragonBreath(npc, other, Kind.FIRE, distant = true)
        } else when (rand().nextInt(3)) {
            0 -> KingBlackDragonBreath(npc, other, Kind.FIRE)
            1 -> KingBlackDragonBreath(npc, other, listOf(Kind.POISON, Kind.ICE, Kind.SHOCK).random())
            else -> melee(animationId = if (randBoolean()) 91 else 80, speed = 5)
        }
    }
}
