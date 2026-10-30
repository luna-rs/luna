package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.CombatAction
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import java.util.Random
import java.util.concurrent.ThreadLocalRandom

/**
 * The source's launch graphic. Its height uses the default proj_height parameter, independently of projectile heights.
 */
val ROCK_LAUNCH = Graphic(275, 96, 0)

/**
 * Lost City's projectile starts at client cycle 31 and ends at 31 + 15 + tile distance. The launch offset is 128
 * client units; the slope and both projectile heights retain the source's values.
 */
val ROCK: (Mob, Mob) -> LocalProjectile = { npc, other ->
    LocalProjectile.followEntity(npc.context)
        .setSourceEntity(npc)
        .setTargetEntity(other)
        .setId(276)
        .setTiming(31, 46 + npc.position.computeLongestDistance(other.position))
        .setStartHeight(36)
        .setEndHeight(36)
        .setInitialSlope(8)
        .setLaunchOffset(128)
        .build()
}

/**
 * Deduplicates the approach-mode speech roll for one NPC life and world tick. Respawning clears this transient
 * attribute along with the rest of the NPC's attributes; there is no separate recurring task to leave behind.
 */
private var Npc.lastSpeechRollTick by Attr.long { Long.MIN_VALUE }

/**
 * The source rolls speech on every active approach-mode tick, including attack cooldown and pursuit. Combat supplies
 * one attack per such tick; cached post-movement retries do not supply another. Extra inspections cannot roll twice.
 */
fun maybeThrowerSpeech(npc: Npc, victim: Mob, random: Random = ThreadLocalRandom.current()) {
    if (!npc.isAlive || !victim.isAlive || npc.combat.target !== victim ||
        !npc.actions.contains(CombatAction::class.java) || npc.lastSpeechRollTick == npc.world.currentTick) return
    npc.lastSpeechRollTick = npc.world.currentTick
    if (random.nextInt(35) == 0) npc.speak("Urg!")
}

/**
 * An always-accurate Thrower Troll rock, mitigated by Protect from Missiles. Its projectile is displayed at launch
 * and damage arrives after floor(end cycle / 30), matching the source's playerhit_n_ranged queue without an extra tick.
 * The victim owns that pending hit, so killing or removing the troll does not erase an already thrown rock.
 *
 * @author lare96
 */
class ThrowerTrollAttack(npc: Npc, victim: Mob) : CombatAttack<Npc>(npc, victim,
    InteractionPolicy(InteractionType.LINE_OF_SIGHT, 8), npc.combatDef().attackSpeed) {

    override fun calculateDamage(other: Mob): CombatDamage =
        CombatDamageRequest.Builder(attacker, other, CombatDamageType.RANGED)
            .setBaseAccuracy(1.0).build().resolve()

    override fun getNpcAttackSound(): Int = attacker.combatDef().rangedAttackSound

    override fun attack() {
        attacker.animation(Animation(attacker.combatDef().attackAnimation, Animation.AnimationPriority.HIGH))
        attacker.graphic(ROCK_LAUNCH)
        val projectile = ROCK(attacker, victim)
        projectile.display()
        if (victim is Player) victim.overlays.closeWindows()
        scheduleProjectileArrival(projectile.endCycle / 30)
    }

    override fun onProjectileReached() {
        victim.submitAction(CombatDamageAction(nextDamage, this, true))
    }
}

/**
 * Thrower Trolls on Death Plateau and in Trollheim share the same native approach and ranged attack script.
 *
 * @author lare96
 */
combat(1101, 1102, 1103, 1104, 1105, 1130, 1131, 1132, 1133, 1134) {
    attack {
        maybeThrowerSpeech(npc, other)
        ThrowerTrollAttack(npc, other)
    }
}
