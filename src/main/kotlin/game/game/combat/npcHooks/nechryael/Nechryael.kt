package game.combat.npcHooks.nechryael

import api.predef.ext.schedule
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.LocalGraphic
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.collision.CollisionFlag
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.NpcAggressionProfile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.CombatRestriction
import io.luna.game.model.mob.combat.attack.MeleeCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.path.route.LineOfSight
import io.luna.net.msg.out.SoundMessageWriter
import java.util.Random
import java.util.concurrent.ThreadLocalRandom

/**
 * Nechryael summoning turns and their temporary death spawns, following Lost City's Nechryael script.
 * Death spawns deal typeless melee damage without claiming single combat or causing automatic retaliation.
 * Their lifetime belongs to the individual spawn and continues after the summoner dies.
 *
 * @author lare96
 */
object Nechryael {

    /**
     * A summon replaces three of every fifty attack turns and leaves the normal four-tick cooldown.
     * The roll occurs when the attack commits, rather than while pursuit repeatedly supplies attacks.
     */
    internal fun attack(npc: Npc, victim: Mob, random: Random = ThreadLocalRandom.current()) =
        object : MeleeCombatAttack<Npc>(npc, victim, npc.combatDef().attackAnimation, 1, 4) {
            private var summoning = false

            override fun onAttack(damage: CombatDamage): CombatDamage {
                summoning = victim is Player && random.nextInt(50) < 3
                return if (summoning) CombatDamageRequest.zero(attacker, victim).resolve() else damage
            }

            override fun attack() {
                if (summoning) summon(attacker, victim as Player, random) else super.attack()
            }
        }

    /**
     * Summons one child on a free tile one or two walking steps away. No fallback places a spawn inside a wall,
     * another mob or the summoner. Each checked step uses live walking collision, including diagonal corners.
     */
    internal fun summon(npc: Npc, victim: Player, random: Random = ThreadLocalRandom.current()): DeathSpawn? {
        if (!npc.isAlive || !victim.isAlive) return null
        npc.animation(Animation(1529))
        LocalGraphic(npc.context, 333, 92, 0, npc.position, ChunkUpdatableView.globalView()).display()
        val position = spawnTiles(npc).shuffled(random).firstOrNull() ?: return null
        val child = DeathSpawn(npc, victim, position)
        return child.takeIf { npc.world.npcs.add(it) }
    }

    /**
     * Finds the short line-of-walk footprint used by the source's map_findsquare search without a long route.
     */
    internal fun spawnTiles(npc: Npc): Set<Position> {
        val collision = npc.world.collisionManager
        val view = collision.view(false)
        val anchor = npc.position
        val tiles = LinkedHashSet<Position>()
        var frontier = setOf(anchor)
        repeat(2) {
            val next = LinkedHashSet<Position>()
            for (from in frontier) {
                for (direction in Direction.ALL_EXCEPT_NONE) {
                    val tile = from.translate(1, direction)
                    if (tile != anchor && collision.traversable(from, EntityType.NPC, direction, 1, false)) {
                        tiles += tile
                        next += tile
                    }
                }
            }
            frontier = next
        }
        return tiles.filterTo(LinkedHashSet()) { tile ->
            LineOfSight.hasLineOfWalk(view, anchor.z, anchor.x, anchor.y, tile.x, tile.y,
                1, 1, 1, 1, CollisionFlag.BLOCK_NPCS or CollisionFlag.BLOCK_PLAYERS)
        }
    }

    /**
     * A temporary, attackable death spawn. Ordinary combat is disabled because the source drives these attacks
     * independently of single-combat ownership and retaliation. A player who hits the child becomes its new victim.
     *
     * @author lare96
     */
    internal class DeathSpawn(parent: Npc, private var victim: Player, position: Position) :
        Npc(parent.context, 1614, position) {

        /**
         * Absolute removal and attack deadlines; neither is reset when the parent dies or a new player hits this child.
         */
        private var expiresAt = Long.MAX_VALUE
        private var nextAttackAt = Long.MIN_VALUE

        init {
            respawnTicks = -1
            combat.isDisabled = true
        }

        override fun getAggressionProfile(): NpcAggressionProfile? = null

        override fun onSpawn() {
            expiresAt = world.currentTick + 100
            world.schedule(1) { task ->
                if (state != EntityState.ACTIVE) {
                    task.cancel()
                } else if (world.currentTick >= expiresAt) {
                    world.npcs.remove(this)
                    task.cancel()
                } else if (isAlive) {
                    tick()
                }
            }
        }

        override fun onDamageApplied(damage: CombatDamage) {
            super.onDamageApplied(damage)
            val attacker = damage.attacker as? Player ?: return
            if (attacker.isAlive) victim = attacker
        }

        /**
         * Pursues a living, local opponent and attacks every four ticks once adjacent. Direct damage matches the
         * reference's damage_player queue: it ignores protection prayers and changes no combat target or timer.
         */
        internal fun tick() {
            if (!isAlive || !victim.isAlive || isActionLocked ||
                !position.isWithinDistance(victim.position, Position.VIEWING_DISTANCE) ||
                !CombatRestriction.permits(this, victim)) {
                navigator.cancel()
                return
            }
            if (!world.collisionManager.reached(position, victim, InteractionPolicy.STANDARD_SIZE)) {
                navigator.navigate(victim, false, true)
                return
            }
            navigator.cancel()
            interact(victim)
            if (world.currentTick < nextAttackAt) return
            nextAttackAt = world.currentTick + 4
            animation(Animation(combatDef().attackAnimation))
            val sound = combatDef().attackSound
            if (sound >= 0) {
                victim.queue(SoundMessageWriter(sound, 1, 0))
            }
            val damage = CombatDamageRequest.Builder(this, victim, CombatDamageType.MELEE)
                .ignoreProtectionPrayers().build().resolve().rawAmount
            val player = victim
            player.combat.impacts.schedule(0, {
                if (isAlive && player.isAlive && world.currentTick < expiresAt &&
                    CombatRestriction.permits(this, player)) player.damage(damage)
            }, { })
        }
    }
}
