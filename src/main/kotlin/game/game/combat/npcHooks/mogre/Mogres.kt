package game.combat.npcHooks.mogre

import api.attr.Attr
import api.predef.slayer
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.collision.CollisionFlag
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.NpcAggressionProfile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.`object`.GameObject
import java.lang.ref.WeakReference

/**
 * Native fishing explosives summon land Mogres through the ordinary item-on-object interaction and combat pipeline.
 * The cache supplies the three spots and presentation assets; period descriptions establish Slayer 32 and one vial
 * per creature. Throw distance, emergence timing, the short shore search and abandonment distance are reconstructed
 * because Lost City's revision 377 encounter script is unavailable. Skippy completion is deliberately not required.
 *
 * @author lare96
 */
object Mogres {

    /**
     * Revision 377 land-Mogre, explosive and native Slayer requirement.
     */
    const val NPC_ID = 2801
    const val EXPLOSIVE = 6664
    const val SLAYER_LEVEL = 32

    /**
     * Reconstructed stand-off throwing band and temporary-creature abandonment distance.
     * A single live summon per player follows later descriptions, rather than recovered revision 377 script code.
     */
    const val MIN_THROW = 5
    const val MAX_THROW = 14
    const val ABANDON_DISTANCE = 15

    /**
     * Real cache-map objects, independent of any future authored bot training subzone.
     */
    internal val SPOTS = mapOf(
        10087 to Position(2982, 3113),
        10088 to Position(2996, 3106),
        10089 to Position(3005, 3117)
    )

    /**
     * Pending throws are owned by the player's action queue; live summons are referenced weakly and are not saved.
     */
    private var Player.mogreEncounter by Attr.nullableObj(Encounter::class)

    /**
     * Returns the native level refusal, including for another player's summoned creature.
     */
    internal fun failureMessage(player: Player): String? =
        if (player.slayer.level < SLAYER_LEVEL) "You need a Slayer level of 32 to fight Mogres." else null

    /**
     * Returns only the unfinished throw currently belonging to this player.
     */
    internal fun pendingThrow(player: Player): ThrowAction? =
        player.mogreEncounter?.pending?.takeUnless { it.isFinished }

    /**
     * Discards a dead or removed summon reference without interfering with the native death and loot sequence.
     */
    internal fun liveMogre(player: Player): MogreNpc? {
        val encounter = player.mogreEncounter ?: return null
        val npc = encounter.live?.get()
        if (npc?.state == EntityState.ACTIVE && npc.isAlive) return npc
        encounter.live = null
        return null
    }

    /**
     * Commits one owned explosive only after validating the exact slot, registered spot and available shore space.
     * Committed vials are spent even if arrival is interrupted, the shore becomes occupied or NPC insertion fails;
     * no delayed path refunds an item or retries a spawn. The exact pending-action identity prevents stale callbacks
     * from affecting a later throw. Ordinary explosives summon only and never inflict emergence damage.
     */
    internal fun throwExplosive(player: Player, spot: GameObject, index: Int): Boolean {
        if (!ready(player) || !registeredSpot(player, spot) ||
            index !in 0 until player.inventory.capacity()) return false
        val item = player.inventory[index] ?: return false
        if (item.id != EXPLOSIVE || item.amount < 1) return false
        val failure = failureMessage(player)
        if (failure != null) {
            player.sendMessage(failure)
            return false
        }
        val distance = player.position.computeLongestDistance(spot.position)
        if (distance < MIN_THROW) {
            player.sendMessage("You should stand a little further away before throwing that.")
            return false
        }
        if (distance > MAX_THROW || !player.world.collisionManager.raycast(player.position, spot.position)) {
            player.sendMessage("You can't throw the explosive there from here.")
            return false
        }
        if (pendingThrow(player) != null || liveMogre(player) != null) {
            player.sendMessage("You already have a Mogre or a fishing explosive to wait for.")
            return false
        }
        if (shoreTile(player, spot.position) == null) {
            player.sendMessage("There is no clear space for a Mogre to reach the shore.")
            return false
        }
        val encounter = player.mogreEncounter ?: Encounter().also { player.mogreEncounter = it }
        val flightTicks = 2 + distance / 2
        val action = ThrowAction(player, spot, player.world.currentTick + flightTicks)
        encounter.pending = action
        player.inventory[index] = if (item.amount == 1) null else item.withAmount(item.amount - 1)
        player.animation(Animation(385))
        LocalProjectile.followPath(player.context).setSourcePosition(player.position).setTargetPosition(spot.position)
            .setId(49).setStartHeight(30).setEndHeight(20).setTiming(0, flightTicks * 30).build().display()
        player.sendMessage("You hurl the shuddering vial into the water...")
        player.submitAction(action)
        return true
    }

    /**
     * Requires the exact active object still registered at its cache-map position; forged or replaced objects fail.
     */
    internal fun registeredSpot(player: Player, spot: GameObject): Boolean =
        spot.state == EntityState.ACTIVE && spot.world === player.world && SPOTS[spot.id] == spot.position &&
            player.position.z == spot.position.z &&
            player.world.objects.findAll(spot.position).anyMatch { it === spot }

    /**
     * Searches four tiles around the water for a free two-by-two land footprint, favoring the throwing player's side.
     * Every covered tile rejects terrain, objects, walls and live mob occupancy. Native size-two movement checks
     * additionally validate both leading edges. This bounded placement search does not create a route or camp area.
     */
    internal fun shoreTile(player: Player, spot: Position): Position? {
        val collision = player.world.collisionManager
        val view = collision.view(false)
        val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR or CollisionFlag.LOC or
            CollisionFlag.BLOCK_NPCS or CollisionFlag.BLOCK_PLAYERS or 0xff
        return (-4..4).flatMap { x -> (-4..4).map { y -> spot.translate(x, y) } }
            .filter { it != spot && it.isWithinDistance(player.position, ABANDON_DISTANCE) }
            .sortedWith(compareBy<Position> { it.computeLongestDistance(player.position) }
                .thenBy { it.computeLongestDistance(spot) }.thenBy { it.x }.thenBy { it.y })
            .firstOrNull { tile ->
                (0..1).all { x -> (0..1).all { y ->
                    view.get(tile.x + x, tile.y + y, tile.z) and blocked == 0
                } } && collision.traversable(tile.translate(-1, 0), EntityType.NPC, Direction.EAST, 2, false) &&
                    collision.traversable(tile.translate(0, -1), EntityType.NPC, Direction.NORTH, 2, false)
            }
    }

    /**
     * Basic native participant checks are repeated at arrival, after any walking, death, logout or level drain.
     */
    private fun ready(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked

    /**
     * Nonpersistent ownership prevents duplicate throws without adding a global encounter registry.
     *
     * @author lare96
     */
    private class Encounter {
        var pending: ThrowAction? = null
        var live: WeakReference<MogreNpc>? = null
    }

    /**
     * One bounded player action owns the committed projectile. The reconstructed arrival delay is two ticks plus
     * half the throw distance; client flight cycles match that deadline. Interrupting this action spends the already
     * thrown vial and clears only its own pending token.
     *
     * @author lare96
     */
    internal class ThrowAction(player: Player, private val spot: GameObject, internal val dueTick: Long) :
        Action<Player>(player, ActionType.SOFT) {

        override fun run(): Boolean {
            if (mob.mogreEncounter?.pending !== this) return true
            if (!ready(mob) || !registeredSpot(mob, spot) || failureMessage(mob) != null ||
                !mob.position.isWithinDistance(spot.position, ABANDON_DISTANCE)) return true
            if (world.currentTick < dueTick) return false
            val tile = shoreTile(mob, spot.position)
            if (tile == null) {
                mob.sendMessage("The water stirs, but no Mogre reaches the shore.")
                return true
            }
            val npc = MogreNpc(mob, tile)
            val encounter = mob.mogreEncounter ?: return true
            encounter.live = WeakReference(npc)
            if (!world.npcs.add(npc)) {
                if (encounter.live?.get() === npc) encounter.live = null
                mob.sendMessage("The water stirs, but no Mogre reaches the shore.")
                return true
            }
            npc.graphic(Graphic(68))
            npc.speak("Human scare all da fishies!")
            npc.combat.attack(mob)
            mob.sendMessage("...and a Mogre appears!")
            return true
        }

        override fun onFinished() {
            val encounter = mob.mogreEncounter
            if (encounter?.pending === this) encounter.pending = null
        }
    }

    /**
     * A temporary native Mogre with ordinary combat, loot and retaliation. Only passive aggression is suppressed;
     * another eligible player may fight it normally. Its own soft action removes abandoned encounters without a
     * world task, keeping another current player's legitimate fight alive even after the thrower leaves.
     *
     * @author lare96
     */
    internal class MogreNpc(owner: Player, position: Position) : Npc(owner.context, NPC_ID, position) {

        /**
         * Weak ownership avoids retaining a logged-out thrower for the remaining lifetime of somebody else's fight.
         */
        private val owner = WeakReference(owner)

        init {
            respawnTicks = -1
            combat.retreatPolicy = RetreatPolicy.NONE
        }

        override fun getAggressionProfile(): NpcAggressionProfile? = null

        override fun onSpawn() {
            submitAction(object : Action<MogreNpc>(this, ActionType.SOFT) {
                override fun run(): Boolean = mob.cleanup()

                override fun onFinished() {
                    if (mob.state == EntityState.ACTIVE && mob.isAlive) world.npcs.remove(mob)
                    mob.release()
                }
            })
        }

        /**
         * Ends abandoned lives, while recent incoming combat or a native current target keeps other players' fights.
         * Dead creatures are left to the normal death action, preserving drop and Slayer-credit ownership.
         */
        internal fun cleanup(): Boolean {
            if (state != EntityState.ACTIVE || !isAlive) {
                release()
                return true
            }
            val current = combat.target as? Player
            val recent = if (combat.isSingleCombatLocked) combat.lastCombatWith as? Player else null
            if (nearby(owner.get()) || nearby(current) || nearby(recent)) return false
            world.npcs.remove(this)
            release()
            return true
        }

        /**
         * A local living player keeps the encounter; no exclusive right to the original thrower is imposed.
         */
        private fun nearby(player: Player?): Boolean = player != null && player.state == EntityState.ACTIVE &&
            player.isAlive && player.world === world && player.position.z == position.z &&
            position.isWithinDistance(player.position, ABANDON_DISTANCE)

        /**
         * Removes only this creature's reference, leaving a later throw or replacement encounter untouched.
         */
        private fun release() {
            val encounter = owner.get()?.mogreEncounter ?: return
            if (encounter.live?.get() === this) encounter.live = null
        }
    }
}
