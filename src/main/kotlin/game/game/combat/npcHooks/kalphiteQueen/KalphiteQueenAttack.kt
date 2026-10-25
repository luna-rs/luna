package game.combat.npcHooks.kalphiteQueen

import api.predef.ext.*
import engine.combat.prayer.CombatPrayer
import engine.controllers.Controllers.inMultiArea
import game.player.Sound
import io.luna.game.model.EntityType
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.net.msg.out.SoundMessageWriter
import java.util.OptionalInt

/**
 * Lost City's revision 377 Queen lightning chain and five-target spine attack.
 * Target positions and protection prayers are sampled at launch. Both styles bypass accuracy rolls.
 *
 * @author lare96
 */
class KalphiteQueenAttack(npc: Npc, other: Mob, private val magic: Boolean) :
    CombatAttack<Npc>(npc, other, InteractionPolicy(InteractionType.LINE_OF_SIGHT,
        if (npc.id == 1158) 15 else 10), 4) {

    override fun calculateDamage(other: Mob): CombatDamage =
        CombatDamageRequest.zero(attacker, other).resolve()

    override fun attack() {
        val flying = attacker.id == 1160
        attacker.animation(Animation(if (magic) {
            if (flying) 1170 else 1172
        } else if (flying) 1250 else 1251))
        if (magic) attacker.graphic(Graphic(if (flying) 279 else 278))

        val primary = victim as? Player ?: return
        if (magic) primary.queue(SoundMessageWriter(Sound.KALTHITE_LIGHTNING.id, 1, 30))
        val targets = targets(attacker, primary, magic)
        var previous = attacker.position.translate(2, 2)
        var duration = 0
        var distanceTravelled = 0
        for ((index, target) in targets.withIndex()) {
            val position = target.position
            val distance = previous.computeLongestDistance(position)
            val start = if (magic) duration else 0
            duration = start + distance * 5
            val hitDelay: Int
            if (magic) {
                if (index == 0) distanceTravelled += distance + 1
                hitDelay = distanceTravelled / 5
                if (index > 0) distanceTravelled += distance + 1
                target.graphic(Graphic(281, 124, if (index == 0) duration else start))
                target.queue(SoundMessageWriter(Sound.KALTHITE_LIGHTNING_IMPACT.id, 1,
                    if (index == 0) duration else start))
            } else {
                hitDelay = duration / 30
                target.queue(SoundMessageWriter(Sound.KALPHITE_QUEEN_SPINES.id, 1, 0))
            }
            val origin = previous
            val projectile = object : LocalProjectile.TargetBuilder(attacker.context) {
                override fun sourcePosition(): Position = origin
                override fun targetPosition(): Position = position
                override fun targetIndex(): OptionalInt = OptionalInt.of(-target.index - 1)
                override fun distanceFromSource(): Int = 11
            }
                .setId(if (magic) 280 else if (flying) 289 else 288)
                .setStartHeight(if (magic) { if (index == 0) 40 else 36 } else 30)
                .setEndHeight(if (magic) 36 else 31)
                .setTicksToStart(start).setTicksToEnd(duration.coerceAtLeast(start + 1))
                .setInitialSlope(if (magic) { if (index == 0) 15 else 0 } else 20).build()
            projectile.display()
            val damage = rollDamage(attacker, target, magic)
            if (!magic && damage.rawAmount > 0) {
                target.skill(Skill.PRAYER).level = (target.skill(Skill.PRAYER).level - 1).coerceAtLeast(0)
            }
            target.combat.lastCombatWith = attacker
            target.combat.resetCombatTimer()
            target.submitAction(CombatDamageAction(damage, this, hitDelay == 0, hitDelay.coerceAtLeast(1)))
            if (magic) previous = position
        }
    }

    companion object {
        /**
         * Finds targets around the original victim, preserving player-list order after the primary target.
         */
        fun targets(npc: Npc, primary: Player, magic: Boolean): List<Player> {
            val nearby = npc.world.locator.findViewable<Player>(EntityType.PLAYER, primary) { true }
            return selectTargets(npc, primary, nearby.sortedBy { it.index }, magic)
        }

        /**
         * Applies the blast radius and multi-combat rules to a snapshot of nearby players.
         */
        internal fun selectTargets(npc: Npc, primary: Player, nearby: Iterable<Player>, magic: Boolean): List<Player> {
            if (!primary.isAlive || primary.isLocked) return emptyList()
            if (!npc.inMultiArea() || !primary.inMultiArea()) return listOf(primary)
            val radius = if (magic) 10 else 5
            val others = nearby.filter {
                it !== primary && it.isAlive && !it.isLocked && it.inMultiArea() &&
                    it.position.isWithinDistance(primary.position, radius) && it.controllers.checkCombat(npc)
            }
            return (listOf(primary) + others).take(if (magic) Int.MAX_VALUE else 5)
        }

        /**
         * Rolls 0..31 directly, unless the matching protection prayer blocks the attack.
         */
        fun rollDamage(npc: Npc, target: Player, magic: Boolean): CombatDamage {
            val prayer = if (magic) CombatPrayer.PROTECT_FROM_MAGIC else CombatPrayer.PROTECT_FROM_MISSILES
            val type = if (magic) CombatDamageType.MAGIC else CombatDamageType.RANGED
            val maximum = if (target.combat.prayers.isActive(prayer)) 0 else 31
            return CombatDamageRequest.damage(npc, target, type, maximum).resolve()
        }
    }
}
