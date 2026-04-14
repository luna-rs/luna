package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import game.player.Sound
import io.luna.Luna
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * The wearable nose peg item id.
 */
val NOSE_PEG = 4168

/**
 * The cast animation used by aberrant spectres for their magic attack.
 */
val CAST_ANIMATION = Animation(1507, AnimationPriority.HIGH)

/**
 * The starting graphic displayed when the attack is cast.
 */
val START_GRAPHIC = Graphic(334, 600, 0)

/**
 * Creates the aberrant spectre magic projectile.
 */
val MAGIC_PROJECTILE: (Mob, Mob) -> LocalProjectile = { mob, other ->
    // TODO Projectile looks a bit off.
    LocalProjectile.followEntity(ctx)
        .setSourceEntity(mob)
        .setTargetEntity(other)
        .setId(335)
        .setTicksToStart(44)
        .setTicksToEnd(3)
        .setStartHeight(43)
        .setEndHeight(31)
        .setInitialSlope(0)
        .build()
}

/**
 * The ending graphic displayed when the projectile reaches the victim.
 */
val END_GRAPHIC = Graphic(336, 500, 0)

/**
 * Handles the aberrant spectre special magic attack.
 *
 * Victims wearing a nose peg take a normal magic hit. Victims without a nose peg take a magic hit that ignores
 * protection prayers, and may also have one or more combat-related skills reduced when the projectile lands.
 *
 * @author lare96
 */
class AberrantSpectreMagicAttack(attacker: Npc, victim: Mob) : MagicCombatAttack<Npc>(
    attacker,
    victim,
    CAST_ANIMATION,
    START_GRAPHIC,
    MAGIC_PROJECTILE,
    END_GRAPHIC,
    Sound.GOO_HIT,
    null,
    attacker.combatDef().attackSpeed,
    6) {

    /**
     * Whether the victim is protected by a nose peg.
     */
    private val protected = !Luna.settings().skills().slayerEquipmentNeeded() ||
            (victim is Player && victim.equipment.head?.id == NOSE_PEG)

    override fun calculateDamage(other: Mob?): CombatDamage {
        val request = CombatDamageRequest.builder(attacker, victim, CombatDamageType.MAGIC)

        if (!protected) {
            // 25% Increased accuracy and ignores prayer when not wearing nose peg.
            request.setFlatBonusAccuracy(0.25)
            request.ignoreProtectionPrayers()
        }

        return request.build().resolve()
    }

    override fun onDamageApplied(damage: CombatDamage?) {
        // Weakens the player when not wearing a nose peg.
        if (!protected && victim is Player) {
            if (randBoolean()) {
                victim.attack.adjustLevel(-rand(10, 20))
            }
            if (randBoolean()) {
                victim.strength.adjustLevel(-rand(10, 20))
            }
            if (randBoolean()) {
                victim.defence.adjustLevel(-rand(10, 20))
            }
            if (randBoolean()) {
                victim.ranged.adjustLevel(-rand(10, 20))
            }
            if (randBoolean()) {
                victim.magic.adjustLevel(-rand(10, 20))
            }
            victim.sendMessage("You have been weakened.")
        }
    }
}

// Registers aberrant spectre magic behaviour for all aberrant spectre npc variants.
combat(1604, 1605, 1606, 1607) {
    attack { AberrantSpectreMagicAttack(npc, other) }
}