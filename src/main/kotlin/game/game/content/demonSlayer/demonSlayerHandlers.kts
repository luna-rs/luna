package game.content.demonSlayer

import api.combat.death.DeathHookHandler
import api.combat.npc.NpcCombatHandler.combat
import api.combat.player.PlayerCombatHandler.playerStopAttack
import api.predef.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority

on(LoginEvent::class) { DemonSlayer.refresh(plr) }
button(DemonSlayer.JOURNAL_BUTTON) { DemonSlayer.journal(plr) }

npc1(DemonSlayer.ARIS) { DemonSlayerDialogue.aris(plr) }
npc1(DemonSlayer.ROVIN) { DemonSlayerDialogue.rovin(plr) }
npc1(DemonSlayer.TRAIBORN) { DemonSlayerDialogue.traiborn(plr) }
object1(DemonSlayer.DRAIN) { DemonSlayer.searchDrain(plr) }
useItem(DemonSlayer.WATER_BUCKET).onObject(DemonSlayer.DRAIN) { DemonSlayer.pourWater(plr) }

playerStopAttack { attack -> (attack.victim as? Npc)?.let { Delrith.stopAttack(this, it) } == true }

combat(Delrith.DEMON) {
    poisonCanKill = false
    defend {
        val player = other as? Player
        if (player == null || !Delrith.canFight(player)) {
            damage = null
            animationId = null
        } else if (DemonSlayer.stage(player) == DemonSlayer.SILVERLIGHT_RECLAIMED &&
            (damage?.rawAmount ?: 0) >= npc.health) {
            damage = damage?.cappedAt(npc.health - 1)
            animationId = null
            Delrith.weaken(player, npc, source)
        }
    }
}

combat(Delrith.WEAKENED) {
    poisonCanKill = false
    defend {
        damage = null
        animationId = null
    }
}

DeathHookHandler.addNpcHook(Delrith.WEAKENED) {
    preDeath { victim.animation(Animation(Delrith.PORTAL_END, AnimationPriority.IMMUTABLE)) }
    death { victim.move(Position(1, 1)) }
    postDeath { }
}
