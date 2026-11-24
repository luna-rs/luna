package game.content.chompyHunting

import api.combat.death.DeathHookHandler
import api.combat.npc.NpcCombatHandler.combat
import api.combat.player.PlayerCombatHandler.playerStopAttack
import api.predef.*
import api.predef.ext.*
import game.content.chompyHunting.ChompyHunting.BLOATED_TOAD
import game.content.chompyHunting.ChompyHunting.CHOMPY_BIRD
import game.content.chompyHunting.ChompyHunting.DEAD_CHOMPY
import game.content.chompyHunting.ChompyHunting.OGRE_BOW
import game.content.chompyHunting.ChompyHunting.SWAMP_TOAD
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.attack.PlayerRangedCombatAttack
import io.luna.game.model.mob.interact.InteractionPolicy

for (bubbles in ChompyHunting.SWAMP_BUBBLES) {
    for (bellows in ChompyHunting.ALL_BELLOWS) {
        useItem(bellows).onObject(bubbles) { ChompyHunting.fillBellows(plr, usedItemIndex) }
    }
}

for (bellows in ChompyHunting.ALL_BELLOWS) {
    useItem(bellows).onNpc(SWAMP_TOAD) { ChompyHunting.inflateToad(plr, targetNpc, usedItemIndex) }
}

// The toad's first option is called "Drop", but it places the toad as bait; its fifth, "Release", is a drop click.
item1(BLOATED_TOAD) { ChompyHunting.placeToad(plr, index) }
item4(BLOATED_TOAD) { ChompyHunting.releaseAll(plr) }

item3(OGRE_BOW) { ChompyHunting.checkKills(plr) }

// "Attack" is the chompy bird's fifth option, which the client sends as a fourth click.
npc4(CHOMPY_BIRD, { plr, target ->
    val attack = plr.combat.getNextAttack(target as Mob)
    plr.combat.firstAttack = attack
    attack.interactionPolicy
}, false) {
    if (targetNpc.combat.isAttackable) plr.combat.attack(targetNpc)
}

playerStopAttack { attack ->
    val chompy = attack.victim as? ChompyBird ?: return@playerStopAttack false
    !ChompyHunting.canShoot(this, chompy, attack is PlayerRangedCombatAttack)
}

combat(CHOMPY_BIRD) {
    afterDamage {
        if (rand(1) == 0) {
            speak("Screech!")
            val from = combat.lastCombatWith?.position ?: return@afterDamage
            val dx = Integer.signum(position.x - from.x).takeIf { it != 0 } ?: (rand(2) - 1)
            val dy = Integer.signum(position.y - from.y).takeIf { it != 0 } ?: (rand(2) - 1)
            navigator.navigate(position.translate(dx * 4, dy * 4), false)
        }
    }
}

DeathHookHandler.addNpcHook(CHOMPY_BIRD) {
    val chompy = victim as? ChompyBird
    death {
        chompy?.clearHint()
        val killer = source
        if (killer is Player && chompy?.isBaitedBy(killer) == true) ChompyHunting.recordKill(killer)
        val dead = Npc(victim.context, DEAD_CHOMPY, victim.position)
        dead.respawnTicks = -1
        if (victim.world.npcs.add(dead)) {
            victim.world.scheduleOnce(100) { if (dead.state == EntityState.ACTIVE) dead.world.npcs.remove(dead) }
        }
    }
    DeathHookHandler.defaultNpcHook?.invoke(this)
}

// "Pluck" is the dead bird's fourth option, which the client sends as a third click.
npc3(DEAD_CHOMPY, InteractionPolicy.STANDARD_SIZE_BIF, false) { ChompyHunting.pluck(plr, targetNpc) }
