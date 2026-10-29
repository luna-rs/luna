package game.combat.npcHooks.tzhaar

import game.player.Sound
import io.luna.game.model.LocalGraphic
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import kotlin.math.sign

/**
 * TzTok-Jad's magic and ranged attacks.
 *
 * The attack starts on tick A with the tell animation. The fireball or the falling rocks are released on A + 3, and
 * the hit lands on A + 4 (magic) or A + 5 (ranged). Damage is rolled when the hit lands, so a protection prayer
 * switched on as late as that tick still blocks it.
 */
class JadAttack(jad: Npc, other: Mob, private val magic: Boolean) :
    CombatAttack<Npc>(jad, other, InteractionPolicy(InteractionType.LINE_OF_SIGHT, RANGE), SPEED) {

    companion object {

        /** Reach of the magic and ranged attacks, in tiles. */
        const val RANGE = 15

        /** Ticks between Jad's magic or ranged attacks. */
        const val SPEED = 8

        const val MAGIC_ANIMATION = 2656
        const val RANGED_ANIMATION = 2652

        const val LAUNCH_GRAPHIC = 447
        const val ROCKS_GRAPHIC = 451

        /** The fireball's head, body and tail, sent together so they draw as one comet. */
        val FIREBALL_PROJECTILES = listOf(448, 449, 450)

        const val MAGIC_MAX_HIT = 95
        const val RANGED_MAX_HIT = 97

        /** Ticks from the start of the attack to the hit. */
        const val MAGIC_HIT_DELAY = 4
        const val RANGED_HIT_DELAY = 5

        /** Client cycles (30 per tick) from the start of the attack to the fireball's release on A + 3. */
        const val RELEASE_CYCLES = 90

        /** Client cycles between the fireball's head, body and tail. */
        const val FIREBALL_STAGGER = 4

        /** Client cycles the fireball takes to reach the player. */
        const val FIREBALL_FLIGHT = 24

        /** Client cycles before the rocks appear: they start falling about 2.5 ticks before the hit. */
        const val ROCKS_DELAY = 74

        /**
         * True if [target] stands right beside one of Jad's edges, the only place he can melee from. A tile that
         * only touches one of his corners diagonally doesn't count.
         */
        fun besideEdge(jad: Npc, target: Mob): Boolean {
            val gapX = edgeGap(jad.position.x, jad.size(), target.position.x, target.size())
            val gapY = edgeGap(jad.position.y, jad.size(), target.position.y, target.size())
            return (gapX == 1 && gapY == 0) || (gapX == 0 && gapY == 1)
        }

        private fun edgeGap(start: Int, size: Int, otherStart: Int, otherSize: Int): Int =
            maxOf(0, otherStart - (start + size - 1), start - (otherStart + otherSize - 1))

        /**
         * The tile the fireball leaves from: the middle of Jad's body, moved one tile towards [target] so it comes
         * out of the mouth rather than his south-west corner.
         */
        fun launchTile(jad: Npc, target: Mob): Position {
            val half = jad.size() / 2
            val centre = jad.position.translate(half, half)
            val dx = (target.position.x - centre.x).sign
            val dy = (target.position.y - centre.y).sign
            return centre.translate(dx, dy)
        }
    }

    override fun calculateDamage(other: Mob): CombatDamage =
        CombatDamageRequest.zero(attacker, other).resolve()

    override fun attack() {
        val target = victim
        if (magic) {
            attacker.animation(Animation(MAGIC_ANIMATION))
            (target as? Player)?.playSound(Sound.FIREBREATH)
            attacker.graphic(Graphic(LAUNCH_GRAPHIC, 0, RELEASE_CYCLES))
            val source = launchTile(attacker, target)
            for ((index, id) in FIREBALL_PROJECTILES.withIndex()) {
                val start = RELEASE_CYCLES + index * FIREBALL_STAGGER
                LocalProjectile.followEntity(attacker.context)
                    .setSourceEntity(attacker)
                    .setSourcePosition(source)
                    .setTargetEntity(target)
                    .setId(id)
                    .setTicksToStart(start)
                    .setTicksToEnd(start + FIREBALL_FLIGHT)
                    .setStartHeight(100)
                    .setEndHeight(5)
                    .setInitialSlope(16)
                    .build()
                    .display()
            }
        } else {
            attacker.animation(Animation(RANGED_ANIMATION))
            // On the tile, not the player: a player shows one graphic at a time, and their own bow graphic would
            // replace the rocks before they fall.
            LocalGraphic(attacker.context, ROCKS_GRAPHIC, 0, ROCKS_DELAY, target.position,
                ChunkUpdatableView.globalView()).display()
        }

        target.combat.lastCombatWith = attacker
        target.combat.resetCombatTimer()
        val type = if (magic) CombatDamageType.MAGIC else CombatDamageType.RANGED
        val maxHit = if (magic) MAGIC_MAX_HIT else RANGED_MAX_HIT
        target.combat.impacts.schedule(if (magic) MAGIC_HIT_DELAY else RANGED_HIT_DELAY, {
            // Rolled here, not at launch, so the protection prayer is read on the tick the hit lands.
            val damage = CombatDamageRequest.builder(attacker, target, type)
                .setBaseMaxHit(maxHit)
                .build()
                .resolve()
            target.submitAction(CombatDamageAction(damage, this, true))
        }, {})
    }
}
