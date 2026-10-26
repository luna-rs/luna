package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.model.mob.combat.SpecialAttackType.MAGIC_SHORTBOW
import io.luna.game.model.mob.combat.damage.CombatDamageType.RANGED
import game.player.Sound
import io.luna.game.model.LocalProjectile
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.RangedAmmoRecovery
import io.luna.game.model.mob.combat.attack.PlayerRangedCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.util.Rational

/**
 * Percentage of a full special bar consumed by revision-377 Snapshot.
 */
val ENERGY_COST = 35

/**
 * Snapshot's poison chance for each positive hit from poisoned arrows.
 */
val POISON_CHANCE = Rational(1, 4)

/**
 * Implements Snapshot using the revision-377 two-arrow cost, independent rolls, and projectile profiles.
 * Both hits commit together and retain the original stance, poison, ammunition, and Smite context. Their recipient
 * owns delivery, so stopping or dying does not recall fired arrows; recipient cleanup still cancels pending damage.
 * Each arrow separately rolls recovery and retains its own landing deadline, independent of damage cancellation.
 *
 * Lost City's pvm_magic_shortbow and pvp_magic_shortbow scripts use visible Ranged plus ten for damage, an exact
 * ten-sevenths offensive roll, and a one-in-four poison chance. PvP uses different flight lengths and omits the
 * extra NPC impact tick. The weapon configuration charges 350 of its 1000 energy units, represented here as 35%.
 *
 * @author lare96
 */
class MagicShortbowCombatAttack(attacker: Player, victim: Mob) : PlayerRangedCombatAttack(attacker, victim) {

    /**
     * The original arrow item, retained even when the second consumption empties the equipment slot.
     */
    private lateinit var firedArrow: Item

    /**
     * The second independently resolved hit, prepared before either projectile is displayed.
     */
    private lateinit var secondDamage: CombatDamage

    override fun getAttackSound(): Sound = Sound.SNAPSHOT

    override fun getPoisonChance(): Rational = POISON_CHANCE

    override fun onAttack(damage: CombatDamage?): CombatDamage? {
        val arrows = attacker.equipment.get(Equipment.AMMUNITION)
        if (arrows == null || arrows.amount < 2) {
            attacker.sendMessage("There isn't enough ammo left in your quiver.")
            return null
        }
        if (!attacker.combat.specialBar.checkEnergy(ENERGY_COST)) {
            return null
        }
        val firstDamage = super.onAttack(damage) ?: return null
        // The base preparation validates compatibility and consumes the first arrow. Both removals are synchronous.
        attacker.equipment.set(Equipment.AMMUNITION, if (arrows.amount == 2) null else arrows.addAmount(-2))
        firedArrow = Item(arrows.id, 1)
        secondDamage = calculateDamage(victim)
        attacker.combat.specialBar.drain(ENERGY_COST, true)
        return firstDamage
    }

    override fun calculateDamage(other: Mob): CombatDamage {
        val ammunition = launchedAmmo ?: attacker.combat.ammoDef
        val rangedLevel = if (launchedAmmo == null) attacker.skill(Skill.RANGED).level else launchedRangedLevel
        val offensiveRoll = if (launchedAmmo == null) CombatFormula.calculateRangedAttackRoll(attacker) else launchedAttackRoll
        val boostedRoll = (offensiveRoll.toLong() * 10 / 7).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val maxHit = (((rangedLevel.toLong() + 10) * (ammunition.strength.toLong() + 64) + 320) / 640).toInt()
        return CombatDamageRequest.builder(attacker, other, CombatDamageType.RANGED)
            .setBaseAccuracy(CombatFormula.calculateRangedHitChance(boostedRoll, other))
            .setBaseMaxHit(maxHit)
            .build().resolve()
    }

    override fun attack() {
        attacker.animation(Animation(1074, AnimationPriority.HIGH))
        attacker.graphic(Graphic(256, 96, 0))
        launchArrow(nextDamage, false)
        launchArrow(secondDamage, true)
    }

    /**
     * Sends one arrow and queues its already-resolved damage against the original target. Recovery is world-owned
     * and scheduled before damage, so killing the recipient or ending a duel cannot delete the paid ammunition.
     *
     * @param damage The independent launch-time roll for this arrow.
     * @param second Whether to use the later projectile profile.
     */
    private fun launchArrow(damage: CombatDamage, second: Boolean) {
        val projectile = projectile(second)
        val travelTicks = projectile.endCycle / 30
        RangedAmmoRecovery.launch(attacker, firedArrow)?.land(launchedTargetPosition, travelTicks)
        projectile.display()
        victim.combat.impacts.schedule(travelTicks + if (victim is Npc) 1 else 0, {
            victim.submitAction(CombatDamageAction(damage, this, true))
        }, {})
    }

    /**
     * Builds the distance-dependent Snapshot projectile. NPC range is measured to the nearest occupied tile.
     * NPC profiles add three cycles to both flights; PvP adds ten to the first and zero to the second.
     *
     * @param second Whether this is the second arrow, starting thirty client cycles after the first.
     * @return The projectile whose duration also supplies ammunition and impact deadlines.
     */
    private fun projectile(second: Boolean): LocalProjectile {
        val origin = attacker.position
        val target = launchedTargetPosition
        val size = if (victim is Npc) victim.size().coerceAtLeast(1) else 1
        val dx = maxOf(target.x - origin.x, origin.x - (target.x + size - 1), 0)
        val dy = maxOf(target.y - origin.y, origin.y - (target.y + size - 1), 0)
        val start = if (second) 50 else 20
        val length = if (victim is Npc) 3 else if (second) 0 else 10
        return LocalProjectile.followEntity(attacker.context)
            .setSourceEntity(attacker)
            .setTargetEntity(victim)
            .setId(249)
            .setTiming(start, start + length + maxOf(dx, dy) * 3)
            .setStartHeight(40)
            .setEndHeight(36)
            .setInitialSlope(15)
            .setLaunchOffset(11)
            .build()
    }
}

/**
 * Registers the Magic shortbow's two-arrow Snapshot special attack.
 *
 * @author lare96
 */
attack(type = MAGIC_SHORTBOW, drain = ENERGY_COST, damageType = RANGED) {
    attack { MagicShortbowCombatAttack(attacker, victim) }
}
