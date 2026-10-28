package game.content.randomEvents

import api.predef.*
import api.combat.npc.NpcCombatHandler.combat
import game.content.randomEvents.RandomEvents.Kind
import game.player.Sound
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.event.impl.PlayerTickEvent

// Register persistent attribute keys while loading scripts, before any player files are read.
val randomEvents = RandomEvents

on(LoginEvent::class) { randomEvents.login(plr) }
on(LogoutEvent::class) { randomEvents.logout(plr) }
on(PlayerTickEvent::class) {
    randomEvents.restore(plr)
    randomEvents.tick(plr)
}

Kind.entries.filter { it != Kind.SWARM }.forEach { kind ->
    npc1(kind.npcId) { randomEvents.talk(plr, targetNpc) }
}

combat(Kind.DRUNKEN_DWARF.npcId) {
    attack {
        val encounter = randomEvents.ownerOf(npc) as? VisitorRandomEvent
        if (encounter?.kind == Kind.DRUNKEN_DWARF && other === encounter.player) encounter.stoneAttack()
        else melee(launch = { null })
    }
}

combat(Kind.SWARM.npcId) {
    attack {
        npc.combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
        melee {
            val encounter = randomEvents.ownerOf(npc) as? SwarmRandomEvent
            if (it != null && encounter?.canAttack(other) == true) {
                encounter.player.playSound(Sound.SWARM_ATTACK)
                it
            } else null
        }
    }
}

cmd("random_event", RIGHTS_ADMIN) {
    val kind = when (args.firstOrNull()?.lowercase()) {
        "swarm" -> Kind.SWARM
        "genie" -> Kind.GENIE
        "old_man" -> Kind.MYSTERIOUS_OLD_MAN
        "dwarf" -> Kind.DRUNKEN_DWARF
        else -> null
    }
    if (kind == null) {
        plr.sendMessage("Usage: ::random_event genie|old_man|dwarf|swarm")
    } else if (!randomEvents.startEncounter(plr, kind)) {
        plr.sendMessage("A random event cannot start here right now.")
    }
}
