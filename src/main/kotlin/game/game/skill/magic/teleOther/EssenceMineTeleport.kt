package game.skill.magic.teleOther

import game.player.Sound
import io.luna.game.model.Entity
import io.luna.game.model.EntityType
import io.luna.game.model.Direction
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic

/**
 * Curse effects and landing positions used by the essence mine transport.
 *
 * @author lare96
 */
object EssenceMineTeleport {

    /**
     * Landing centers captured by Lost City, relative to map square 45,75.
     */
    internal val landingCenters = listOf(
        32 to 33, 35 to 31, 33 to 26, 30 to 24, 26 to 26, 28 to 35, 25 to 37, 32 to 38,
        43 to 46, 51 to 42, 41 to 56, 55 to 46, 14 to 39, 16 to 45, 19 to 58, 13 to 26,
        19 to 21, 16 to 9, 41 to 20, 45 to 18, 41 to 11, 54 to 21
    ).map { (x, y) -> Position(2880 + x, 4800 + y) }

    /**
     * Chooses a landing center and an unobstructed tile within one tile of it.
     */
    fun destination(world: World): Position {
        val center = landingCenters.random()
        val nearby = Direction.ALL.filter {
            it == Direction.NONE || world.collisionManager.traversable(center, EntityType.PLAYER, it)
        }.map { center.translate(1, it) }
        return nearby.random()
    }

    /**
     * Displays a Curse projectile and impact, without a player or wizard animation.
     * Client timing values are measured from projectile creation in 20ms cycles.
     */
    fun effects(source: Entity, target: Mob, portal: Boolean) {
        val start = if (portal) 0 else 61
        val end = if (portal) 30 else 100
        LocalProjectile.followEntity(source.context)
            .setSourceEntity(source)
            .setTargetEntity(target)
            .setId(109)
            .setStartHeight(if (portal) 15 else 31)
            .setEndHeight(31)
            .setTicksToStart(start)
            .setTicksToEnd(end)
            .setInitialSlope(16)
            .build().display()
        target.graphic(Graphic(110, 124, end))
        if (target is Player) target.playSound(if (portal) Sound.TELEPORT_ALL else Sound.CURSE_ALL)
    }
}
