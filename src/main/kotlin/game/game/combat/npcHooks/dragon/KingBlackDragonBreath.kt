package game.combat.npcHooks.dragon

import api.predef.*
import api.predef.ext.*
import engine.combat.prayer.CombatPrayer
import engine.combat.status.hooks.ImmobilizedStatusEffect
import engine.combat.status.hooks.PoisonStatusEffect
import game.player.Sound
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * King Black Dragon breath mechanics from Lost City's area_wilderness/king_black_dragon.rs2.
 * The reference's estimated special-effect probabilities are retained.
 *
 * Attacks use a five-tick cooldown. Ordinary fire supports shield, prayer and antifire protection;
 * special breaths ignore antifire and can poison, freeze or drain every skill except Hitpoints.
 * Secondary effects require an accurate hit, including accurate hits that roll zero damage.
 *
 * @param npc The attacking King Black Dragon.
 * @param other The target receiving the breath.
 * @param kind The breath damage and secondary effect to use.
 * @param distant Whether to launch ordinary fire as a projectile with a ten-tile range.
 *
 * @author lare96
 */
class KingBlackDragonBreath(npc: Npc, other: Mob, private val kind: Kind, distant: Boolean = false) :
    MagicCombatAttack<Npc>(npc, other, Animation(if (distant) 84 else kind.animation),
        if (distant) null else kind.graphic?.let { Graphic(it, 92, 0) },
        if (distant) PROJECTILE else null,
        if (distant) Graphic(131, 124, 0) else if (kind == Kind.SHOCK) Graphic(197) else null,
        kind.sound, null, 5, if (distant) 10 else 1) {

    /**
     * Breath visuals and sounds in the revision 377 pack.
     *
     * @author lare96
     */
    enum class Kind(val animation: Int, val graphic: Int?, val sound: Sound) {
        FIRE(81, 0, Sound.DRAGONBREATH),
        POISON(83, 3, Sound.TOXICBREATH),
        ICE(82, 2, Sound.KINGICEBREATH),
        SHOCK(84, null, Sound.LIGHTNINGBREATH)
    }

    companion object {
        /**
         * Builds the revision 377 fire projectile for targets outside melee reach.
         */
        private val PROJECTILE: (Mob, Mob) -> LocalProjectile = { attacker, victim ->
            LocalProjectile.followEntity(ctx).setSourceEntity(attacker).setTargetEntity(victim)
                .setId(54).setTicksToStart(51).setTicksToEnd(16).setStartHeight(92)
                .setEndHeight(31).setInitialSlope(-5).build()
        }

        /**
         * Applies Lost City's ordered protection rules; antifire only reduces ordinary fire.
         * Shield protection takes precedence over Protect from Magic instead of stacking with it.
         *
         * @param kind The breath being resolved.
         * @param shield Whether an anti-dragon shield is equipped.
         * @param prayer Whether Protect from Magic is active.
         * @param antifire Whether an antifire potion effect is active.
         * @return The maximum damage after protection, never below zero.
         */
        fun maximumHit(kind: Kind, shield: Boolean, prayer: Boolean, antifire: Boolean): Int {
            val fire = kind == Kind.FIRE
            val base = if (shield) { if (fire) 15 else 10 }
                else if (prayer) { if (fire) 20 else 15 }
                else if (fire) 65 else 50
            return (base - if (fire && antifire) 15 else 0).coerceAtLeast(0)
        }
    }

    /**
     * Original accuracy result, retained before the magic attack converts splashes to zero damage.
     */
    private var accurate = false

    /**
     * Shield protection captured when damage is rolled, also used for secondary effect probability.
     */
    private var shield = false

    /**
     * Prayer protection captured when damage is rolled; shocking breath requires it with a shield
     * to reduce the secondary effect chance from one half to one eighth.
     */
    private var prayer = false

    override fun calculateDamage(other: Mob): CombatDamage {
        val player = other as? Player
        shield = player?.equipment?.shield?.id == 1540
        prayer = player?.combat?.prayers?.isActive(CombatPrayer.PROTECT_FROM_MAGIC) == true
        val damage = CombatDamageRequest.Builder(attacker, other, CombatDamageType.MAGIC)
            .setBaseMaxHit(maximumHit(kind, shield, prayer, player?.status?.isAntiFire() == true))
            .ignoreProtectionPrayers().build().resolve()
        // MagicCombatAttack turns splashes into numeric zero hits; preserve the original accuracy roll.
        accurate = damage.amount.isPresent
        return damage
    }

    override fun onDamageApplied(damage: CombatDamage?) {
        if (!accurate || victim !is Player || !victim.isAlive || kind == Kind.FIRE) return
        val protected = shield && (kind != Kind.SHOCK || prayer)
        if (rand().nextInt(if (protected) 8 else 2) != 0) return
        when (kind) {
            Kind.POISON -> if (!victim.status.isAntiPoison()) victim.status.add(PoisonStatusEffect(victim, 36))
            Kind.ICE -> victim.status.add(ImmobilizedStatusEffect(victim, 10.ticks))
            Kind.SHOCK -> {
                victim.sendMessage("You're zapped by the dragon's shocking breath!")
                victim.skills.filter { it.id != Skill.HITPOINTS }.forEach {
                    it.level = (it.level - 2).coerceAtLeast(if (it.id == Skill.PRAYER) 0 else 1)
                }
            }
            Kind.FIRE -> Unit
        }
    }
}
