package game.content.barrows

import api.combat.death.DeathHookHandler
import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.AmmoType
import io.luna.game.model.mob.combat.CombatSpell

for (brother in BarrowsBrother.entries) {
    object1(brother.sarcophagusId) {
        if (plr.position.z != 3 || !BarrowsAreaListener.inside(plr.position)) return@object1
        if (BarrowsRun.state(plr) == 0) BarrowsRun.enterCrypt(plr)
        plr.sendMessage("You don't find anything.")
        if (BarrowsRun.tunnelBrother(plr) == brother) {
            val origin = plr.position
            plr.newDialogue().text("You've found a hidden tunnel, do you want to enter?")
                .options("Yeah I'm fearless!", { player ->
                    if (player.position == origin && player.isAlive && BarrowsRun.tunnelBrother(player) == brother) {
                        val chamber = BarrowsRun.chamber(player)
                        if (chamber != null) {
                            val destination = BarrowsEncounters.nearbyTile(player, chamber.arrival)
                            if (destination != null) player.move(destination)
                        }
                    }
                }, "No way, that looks scary!", {}).open()
        } else {
            BarrowsEncounters.spawn(plr, brother, "You dare disturb my rest!")
        }
    }

    DeathHookHandler.addNpcHook(brother.npcId) {
        val npc = victim
        if (npc is BarrowsBrotherNpc) {
            preDeath { npc.animation(Animation(npc.combatDef().deathAnimation)) }
            death {
                BarrowsEncounters.defeated(npc, source)
            }
        } else {
            DeathHookHandler.defaultNpcHook?.invoke(this)
        }
    }
}

for (chamber in BarrowsChamber.entries) {
    object1(chamber.ladderId) {
        if (plr.position.z == 0 && BarrowsAreaListener.inside(plr.position) && BarrowsRun.chamber(plr) == chamber) {
            BarrowsRun.tunnelBrother(plr)?.let { plr.move(it.tunnelExit) }
        }
    }
}

// Baseline attack styles. The contribution explicitly marks the special effects/combat pass unfinished.
combat(2025) { attack { magic(CombatSpell.FIRE_WAVE, speed = 6) } }
combat(2028) { attack { ranged(AmmoType.BOLT_RACK, range = 7, speed = 4) } }
