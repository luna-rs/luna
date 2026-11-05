package game.combat.npcHooks.tzhaar

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import game.player.Sound
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.attack.MeleeCombatAttack

/** The Fight Cave's monsters, the ones a Yt-MejKot may heal. */
val CAVE_MONSTERS = 2734..2746

/** The most a single heal restores. */
val MAX_HEAL = 10

/** Itself and the cave monsters beside it that are under half health. */
fun patients(mejKot: Npc): List<Npc> {
    val nearby = mejKot.world.locator.findViewableNpcs(mejKot) {
        it !== mejKot && it.id in CAVE_MONSTERS && JadAttack.besideEdge(mejKot, it)
    }
    return (nearby + mejKot).filter { it.isAlive && it.health * 2 < it.totalHealth }
}

/**
 * A heal in place of an attack: it uses the attack timer and keeps the target, but deals no damage.
 */
class Heal(mejKot: Npc, other: Mob) : MeleeCombatAttack<Npc>(mejKot, other, JadHealers.HEAL_ANIMATION, 1,
    mejKot.combatDef().attackSpeed) {

    override fun getNpcAttackSound() = Sound.SELFHEAL.id

    override fun attack() {
        val patient = patients(attacker).randomOrNull() ?: return
        attacker.animation(Animation(JadHealers.HEAL_ANIMATION))
        attacker.graphic(Graphic(JadHealers.HEAL_GRAPHIC))
        patient.health = (patient.health + rand().nextInt(MAX_HEAL) + 1).coerceAtMost(patient.totalHealth)
    }
}

/**
 * While it stands beside the player, a Yt-MejKot heals itself or a cave monster beside it that's under half health,
 * instead of attacking.
 *
 * @author TheLining
 */
combat(2741, 2742) {
    attack {
        if (JadAttack.besideEdge(npc, other) && patients(npc).isNotEmpty()) Heal(npc, other) else melee()
    }
}
