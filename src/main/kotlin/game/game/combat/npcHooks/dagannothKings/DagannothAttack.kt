package game.combat.npcHooks.dagannothKings

import engine.combat.status.hooks.PoisonStatusEffect
import engine.controllers.Controllers.inMultiArea
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.LocalProjectile
import io.luna.game.model.LocalSound
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.CombatRestriction
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

/**
 * Waterbirth projectiles, with independent recipient-owned impacts and the Spinolyp's ranged-based magic roll.
 * Visual identifiers are from the revision 377 cache. The later documented king splash radii and Spinolyp
 * effects are reconstructed here; Supreme uses a symmetric three-tile radius for the ambiguous six-by-six reference.
 * Each recipient still passes through native accuracy, prayer and defence hooks.
 *
 * @author lare96
 */
class DagannothAttack(npc: Npc, victim: Mob, val style: Style) : CombatAttack<Npc>(npc, victim,
    InteractionPolicy(InteractionType.LINE_OF_SIGHT, if (style.spinolyp) 10 else 8), npc.combatDef().attackSpeed) {

    /**
     * The cache visuals and native damage family for each projectile attack.
     *
     * @author lare96
     */
    enum class Style(val type: CombatDamageType, val animation: Int, val projectile: Int,
                     val end: Int?, val radius: Int, val spinolyp: Boolean = false) {
        SUPREME(CombatDamageType.RANGED, 2855, 473, 474, 3),
        PRIME(CombatDamageType.MAGIC, 2854, 162, 477, 1),
        SPINOLYP_MAGIC(CombatDamageType.MAGIC, 2868, 94, 95, 0, true),
        SPINOLYP_RANGED(CombatDamageType.RANGED, 2868, 476, null, 0, true)
    }

    override fun getNpcAttackSound(): Int =
        if (style.type == CombatDamageType.RANGED) attacker.combatDef()?.rangedAttackSound ?: -1 else -1

    override fun calculateDamage(other: Mob): CombatDamage {
        val request = CombatDamageRequest.Builder(attacker, other, style.type)
            .setBaseMaxHit(attacker.combatDef().maximumHit)
        if (style == Style.SPINOLYP_MAGIC) {
            request.setBaseAccuracy(CombatFormula.calculateHitChance(attacker, other, CombatDamageType.RANGED))
        }
        return request.build().resolve()
    }

    override fun attack() {
        attacker.animation(Animation(style.animation))
        val launchPlane = attacker.position.z
        val primary = victim as? Player
        val recipients: List<Mob> = if (primary == null) listOf(victim) else {
            val nearby = attacker.world.locator.findPlayers(primary, style.radius) { true }
            selectTargets(attacker, primary, nearby, style.radius)
        }
        for (recipient in recipients) {
            val damage = if (recipient === victim) nextDamage else calculateDamage(recipient)
            val projectile = LocalProjectile.followEntity(attacker.context).setSourceEntity(attacker)
                .setSourcePosition(attacker.position.translate(attacker.size() / 2, attacker.size() / 2))
                .setTargetEntity(recipient).setId(style.projectile).setStartHeight(40).setEndHeight(31)
                .setTiming(10, 30 + attacker.position.computeLongestDistance(recipient.position) * 5)
                .setInitialSlope(16).build()
            projectile.display()
            recipient.combat.markAttackedBy(attacker)
            recipient.combat.impacts.schedule((projectile.endCycle / 30).coerceAtLeast(1), {
                if (recipient.isAlive && recipient.state == EntityState.ACTIVE &&
                    recipient.position.z == launchPlane && CombatRestriction.permits(attacker, recipient)) {
                    recipient.submitAction(CombatDamageAction(damage, this, true))
                }
            }, {})
        }
    }

    override fun onDamageApplied(damage: CombatDamage) {
        val recipient = damage.victim
        if (!damage.isAccurate && (style == Style.SUPREME || style.type == CombatDamageType.MAGIC)) {
            recipient.graphic(Graphic(85, 100))
        } else if (style.end != null) {
            recipient.graphic(Graphic(style.end, 100))
        }
        if (style == Style.SUPREME || style == Style.PRIME) {
            LocalSound.of(recipient, Sound.WATERWAVE_HIT).display()
        }
        if (recipient is Player) applySpinolypEffect(style, damage, recipient)
    }

    companion object {
        /**
         * Selects nearby multi-combat recipients without crossing planes, projectile blockers or controller limits.
         */
        internal fun selectTargets(npc: Npc, primary: Player, nearby: Iterable<Player>, radius: Int): List<Player> {
            if (!primary.isAlive || primary.state != EntityState.ACTIVE ||
                !CombatRestriction.permits(npc, primary)) return emptyList()
            if (radius == 0 || !npc.inMultiArea() || !primary.inMultiArea()) return listOf(primary)
            return listOf(primary) + nearby.filter {
                it !== primary && it.state == EntityState.ACTIVE && it.isAlive &&
                    it.position.isWithinDistance(primary.position, radius) && it.inMultiArea() &&
                    it.controllers.checkCombat(npc) && CombatRestriction.permits(npc, it) &&
                    npc.world.collisionManager.raycast(npc.position, it.position)
            }.distinct()
        }

        /**
         * Accurate magic drains prayer even on a zero; ranged poison requires delivered positive damage.
         */
        internal fun applySpinolypEffect(style: Style, damage: CombatDamage, recipient: Player) {
            if (!damage.isAccurate || !recipient.isAlive) return
            if (style == Style.SPINOLYP_MAGIC) {
                val prayer = recipient.skill(Skill.PRAYER)
                prayer.level = (prayer.level - 1).coerceAtLeast(0)
                if (prayer.level == 0) recipient.combat.prayers.deactivateAll()
            } else if (style == Style.SPINOLYP_RANGED && damage.rawAmount > 0) {
                recipient.status.add(PoisonStatusEffect(recipient, 26), false)
            }
        }
    }
}
