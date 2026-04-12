package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.util.RandomUtils
import io.luna.util.Rational

// Level 7 dark wizards.
combat(174) {
    attack {
        if (rand(4) == 0)
            magic(CombatSpell.CONFUSE)
        else
            magic(CombatSpell.WATER_STRIKE)
    }
}

// Level 11 dark wizards.
combat(3244, 3245) {
    attack {
        if (rand(4) == 0)
            magic(CombatSpell.CONFUSE)
        else
            magic(CombatSpell.EARTH_STRIKE)
    }
}

// Level 20 dark wizards.
combat(172) {
    attack {
        if (rand(4) == 0)
            magic(CombatSpell.WEAKEN)
        else
            magic(CombatSpell.FIRE_STRIKE)
    }
}

// Level 22 and 23 dark wizards.
combat(3242, 3243) {
    attack {
        if (rand(2) == 0 && !other.combat.isImmobilized)
            magic(CombatSpell.BIND)
        else if (RandomUtils.roll(Rational.UNCOMMON))
            magic(if (rand().nextBoolean()) CombatSpell.WIND_BOLT else CombatSpell.WATER_BOLT)
        else
            magic(CombatSpell.FIRE_STRIKE)
    }
}
