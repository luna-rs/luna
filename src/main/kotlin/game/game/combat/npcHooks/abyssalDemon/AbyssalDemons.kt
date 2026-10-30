package game.combat.npcHooks.abyssalDemon

import game.player.Sound
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.damage.CombatDamage
import java.util.Random
import java.util.concurrent.ThreadLocalRandom

/**
 * Lost City's Abyssal demon reaction to received numeric hits. The demon first rolls one in six to teleport itself;
 * only a failed self roll permits a separate one-in-three roll to pull its opponent beside it.
 * Self teleports show a departure effect and yield one tick before arriving, using the native timed lock.
 * Blocked destinations, departed opponents, death and protected player actions cancel the affected move.
 *
 * @author lare96
 */
object AbyssalDemons {

    /**
     * The source teleport outcomes, chosen before checking for a usable destination.
     *
     * @author lare96
     */
    internal enum class Teleport { NONE, SELF, PLAYER }

    /**
     * Preserves the source's conditional rolls: the overall player-pull probability is five in eighteen.
     */
    internal fun choose(random: Random): Teleport = when {
        random.nextInt(6) == 0 -> Teleport.SELF
        random.nextInt(3) == 0 -> Teleport.PLAYER
        else -> Teleport.NONE
    }

    /**
     * Reacts to numeric hits, including physical zero hits. Splashes, effect-only spells, lethal hits and inactive
     * combatants cannot trigger a move. Existing combat ownership takes priority over a secondary attacker.
     */
    internal fun afterHit(npc: Npc, damage: CombatDamage, random: Random = ThreadLocalRandom.current()) {
        if (damage.victim !== npc || damage.attacker !is Player || damage.amount.isEmpty ||
            damage.rawAmount < 0 || !npc.isAlive || npc.isActionLocked) return
        val opponent = npc.combat.target ?: damage.attacker
        val player = opponent as? Player ?: return
        if (!near(npc, player)) return
        when (choose(random)) {
            Teleport.NONE -> return
            Teleport.SELF -> {
                npc.graphic(Graphic(409))
                player.playSound(Sound.GRIM_REAPER_APPEAR)
                npc.navigator.cancel()
                npc.lock(1) {
                    if (npc.isAlive && !npc.isActionLocked && near(npc, player)) {
                        jump(npc, player, npc, random)
                    }
                }
            }
            Teleport.PLAYER -> {
                if (!player.isActionLocked) jump(npc, player, player, random)
            }
        }
    }

    /**
     * Rejects a stale projectile's opponent after death, logout, a plane change or departure from local combat.
     */
    private fun near(npc: Npc, player: Player): Boolean = npc.isAlive && player.isAlive &&
        npc.position.isWithinDistance(player.position, Position.VIEWING_DISTANCE)

    /**
     * Checks all eight adjacent destinations in random order using live walk collision, including diagonal corners
     * and the moving entity's footprint. A teleport never falls back to a blocked tile or changes floor.
     */
    private fun jump(npc: Npc, player: Player, mover: Mob, random: Random) {
        val anchor = if (mover === npc) player.position else npc.position
        val collision = npc.world.collisionManager
        val destination = Direction.ALL_EXCEPT_NONE.shuffled(random).firstNotNullOfOrNull { direction ->
            val tile = anchor.translate(1, direction)
            tile.takeIf {
                it != mover.position &&
                    collision.traversable(it, mover.type, Direction.NONE, mover.size(), false) &&
                    collision.traversable(anchor, mover.type, direction, mover.size(), false)
            }
        } ?: return
        mover.navigator.cancel()
        mover.move(destination)
        mover.graphic(Graphic(409))
        if (mover === player) player.playSound(Sound.GRIM_REAPER_APPEAR)
    }
}
