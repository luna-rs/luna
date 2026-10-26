package game.content.pestControl

import api.combat.death.DeathHookHandler
import api.combat.npc.NpcCombatHandler
import api.combat.npc.dsl.NpcCombatReceiver
import api.combat.player.PlayerCombatHandler.playerDefence
import api.combat.player.PlayerCombatHandler.playerStopAttack
import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

on(ServerLaunchEvent::class) { PestControl.launch() }
on(LoginEvent::class) { PestControl.login(plr) }

npc1(3801) {
    plr.newDialogue().npc(3801, "The Void Knights need volunteers to defend their island.").options(
        "Take me to the outpost.", { PestControl.sail(it, true) },
        "Not right now.", { it.overlays.closeWindows() }).open()
}
npc2(3801) { PestControl.sail(plr, true) }
npc1(3800) { PestControl.sail(plr, false) }
npc2(3800) { PestControl.sail(plr, false) }
npc1(3802) {
    plr.newDialogue().npc(3802, "Board the lander to fight for the Void Knights.",
        "Bring a hammer and logs to repair the defences.").open()
}
npc1(3781) {
    plr.newDialogue().npc(3781, "Do you want to abandon this mission?").options(
        "Yes, take me back.", { PestControl.leave(it); it.overlays.closeWindows() },
        "I'll keep fighting.", { it.overlays.closeWindows() }).open()
}
for (id in 3786..3789) {
    npc1(id) { PestControlTraining.show(plr) }
    npc3(id) { PestControlTraining.show(plr) }
}
object1(14315) { PestControl.board(plr) }
object1(14314) { if (PestControl.onLander(plr.position)) PestControl.leave(plr) }
for (id in 14233..14248) object1(id) { PestControl.defences.gate(plr, gameObject) }
for (id in 14210..14248) {
    object3(id) { PestControl.defences.repair(plr, gameObject) }
    useItem(1511).onObject(id) { PestControl.defences.repair(plr, gameObject) }
    useItem(2347).onObject(id) { PestControl.defences.repair(plr, gameObject) }
}
for (index in PestControlTraining.SKILLS.indices) {
    for (id in listOf(18760 + index, 18767 + index)) button(id) {
        plr.overlays.getOverlay(PestControlTraining::class.java)?.select(plr, index)
    }
}
button(18779) { plr.overlays.getOverlay(PestControlTraining::class.java)?.confirm(plr) }
button(18759) { plr.overlays.closeWindows() }

playerStopAttack { attack ->
    (controllers.primary is PestControlController || attack.victim is PestControlNpc) &&
        !PestControl.canAttack(this, attack.victim)
}
playerDefence {
    (player.controllers.primary is PestControlController || other is PestControlNpc) &&
        (!PestControl.round.playing(player) || !PestControl.onIsland(player.position) || !PestControl.owns(other))
}.then { damage = null }

NpcCombatHandler.typeMap[PestControlNpc::class] = NpcCombatReceiver().apply {
    defend {
        val pest = npc as PestControlNpc
        if (!PestControl.owns(pest) || other is Player && !PestControl.canAttack(other, pest)) damage = null
    }
    attack {
        val projectile: (Mob, Mob) -> LocalProjectile = { source, target ->
            LocalProjectile.followEntity(ctx).setSourceEntity(source).setTargetEntity(target)
                .setId(if (npc.id in 3752..3761) 647 else 656)
                .setTicksToStart(3).setTicksToEnd(44).setStartHeight(36).setEndHeight(36).setInitialSlope(8).build()
        }
        when (npc.id) {
            in 3752..3761 -> object : MagicCombatAttack<PestControlNpc>(npc as PestControlNpc, other,
                Animation(3882), Graphic(646), projectile, Graphic(648), null, null, 5, 8) {
                override fun calculateDamage(other: Mob) = CombatDamageRequest.Builder(attacker, other, CombatDamageType.MAGIC)
                    .setBaseMaxHit(attacker.combatDef().maximumHit).build().resolve()
            }
            in 3762..3771 -> ranged(Graphic(655), projectile, animationId = 3920, range = 8, speed = 5)
            else -> melee()
        }
    }
}

for (id in 3727..3782) DeathHookHandler.addNpcHook(id) {
    if (victim is PestControlNpc) {
        preDeath {
            victim.animation(Animation(victim.combatDef().deathAnimation, Animation.AnimationPriority.IMMUTABLE))
            PestControl.npcDied(victim)
        }
        death { }
        postDeath(reset = false) { PestControl.remove(victim) }
    } else DeathHookHandler.defaultNpcHook?.invoke(this)
}
