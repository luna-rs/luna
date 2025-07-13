package world.player.skill.magic.telekineticGrab

import api.predef.*
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.LocalProjectile
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import world.player.Messages
import world.player.Sounds
import world.player.skill.magic.Magic
import world.player.skill.magic.Rune
import world.player.skill.magic.RuneRequirement

/**
 * A [RepeatingAction] implementation that handles the telegrab spell.
 */
class TelekineticGrabAction(plr: Player, private val groundItem: GroundItem) : LockedAction(plr) {

    override fun onLock() {
        mob.face(groundItem.position)
        if (!world.collisionManager.raycast(mob.position, groundItem.position)) {
            mob.sendMessage("I can't reach that!")
            complete()
            return
        }
        val item = groundItem.toItem()
        if (!mob.inventory.hasSpaceFor(item)) {
            mob.sendMessage(Messages.INVENTORY_FULL)
            complete()
            return
        }
        val removeItems = Magic.checkRequirements(
            mob, 33, listOf(
                RuneRequirement(Rune.AIR, 1),
                RuneRequirement(Rune.LAW, 1)
            )
        )
        if (removeItems == null) {
            complete()
            return
        }
        mob.inventory.removeAll(removeItems)
        mob.magic.addExperience(43.0)
    }


    override fun run(): Boolean =
        when (executions) {
            0 -> {
                mob.graphic(Graphic(142, 100))
                mob.animation(Animation(791))
                mob.playSound(Sounds.TELEGRAB)
                false
            }

            1 -> false
            2 -> {
                // todo https://github.com/luna-rs/luna/issues/377
                val projectile = LocalProjectile.followPath(ctx).setSourcePosition(mob.position)
                    .setTargetPosition(groundItem.position).setDurationTicks(2).setStartHeight(35)
                    .setId(143).build()
                projectile.display()
                false
            }

            3 -> false
            4 -> {
                val graphic = LocalGraphic(ctx, 144, 0, 0, groundItem.position, ChunkUpdatableView.globalView())
                graphic.display()

                val item = groundItem.toItem()
                if (groundItem.state == EntityState.INACTIVE) {
                    mob.sendMessage("You were too late!")
                } else if (!mob.inventory.hasSpaceFor(item)) {
                    mob.sendMessage(Messages.INVENTORY_FULL)
                } else if (world.items.unregister(groundItem)) {
                    mob.inventory.add(item)
                }
                true
            }

            else -> true
        }
}