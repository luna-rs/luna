package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import game.player.Sound
import game.skill.slayer.Slayer.drainCombatSkills
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
val START_GRAPHIC = Graphic(334, 500, 0)

/**
 * Creates the spectre projectile at cast time using Lost City's player_projectile profile.
 * Arrival is thirty-two client cycles plus ten per tile from the NPC's position to the target.
 */
val MAGIC_PROJECTILE: (Mob, Mob) -> LocalProjectile = { mob, other ->
    val duration = 32 + mob.position.computeLongestDistance(other.position) * 10
    LocalProjectile.followEntity(mob.context)
        .setSourceEntity(mob)
        .setTargetEntity(other)
        .setId(335)
        .setTiming(32, duration)
        .setLaunchOffset(4)
        .setStartHeight(61)
        .setEndHeight(40)
        .setInitialSlope(5)
        .build()
}

/**
 * The ending graphic displayed when the projectile reaches the victim.
 */
val END_GRAPHIC = Graphic(336, 320, 0)

/**
 * Handles the aberrant spectre special magic attack.
 *
 * Victims wearing a nose peg take a normal magic hit. Unprotected players take an always-accurate fourteen-damage
 * hit that ignores protection prayers. At launch, offensive skills lose eighty percent of their current levels;
 * defence, prayer, and agility lose forty percent. These drains are immediate in Lost City's spectre script.
 * Equipment is checked when the hit is calculated, not when an attack is first selected during pursuit.
 * The projectile is sent with the cast, and damage uses its arrival cycle divided by thirty without the spellbook
 * extra tick. Delivered hits retain the shared magic impact graphics and sound.
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
     * Protection captured during damage resolution immediately before launch, including the global equipment setting.
     */
    private var protected = true

    override fun usesProjectileTiming(): Boolean = true

    override fun attack() {
        if (victim is Player) victim.playSound(Sound.SLAYERDOORS_CLOSE)
        super.attack()
    }

    override fun calculateDamage(other: Mob?): CombatDamage {
        protected = !Luna.settings().skills().slayerEquipmentNeeded() || victim !is Player ||
                victim.equipment.head?.id == NOSE_PEG
        val request = CombatDamageRequest.builder(attacker, victim, CombatDamageType.MAGIC)

        if (!protected) {
            request.setBaseAccuracy(1.0)
            request.setFlatBonusAccuracy(1.0)
            request.setBaseMaxHit(0)
            request.setFlatBonusDamage(14)
            request.ignoreProtectionPrayers()
        }

        return request.build().resolve()
    }

    override fun onAttack(damage: CombatDamage?): CombatDamage? {
        if (damage != null && !protected && victim is Player && victim.isAlive) {
            victim.drainCombatSkills(80, 40)
            victim.sendMessage("You have been weakened.")
        }
        return damage
    }
}

// Registers aberrant spectre magic behaviour for all aberrant spectre npc variants.
combat(1604, 1605, 1606, 1607) {
    attack { AberrantSpectreMagicAttack(npc, other) }
}
