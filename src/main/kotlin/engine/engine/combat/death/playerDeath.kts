package engine.combat.death

import api.combat.death.DeathHookHandler
import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.player.Jingles
import io.luna.Luna
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.SkullIcon
import io.luna.game.model.mob.block.Animation.AnimationPriority

/**
 * Items will be lost if the player is below this rank.
 */
val loseItemsBelow = RIGHTS_ADMIN

/**
 * Builds and registers the default death hook for all players.
 */
DeathHookHandler.setDefaultHook(Player::class) {
    preDeath {
        victim.lock()
        victim.sendMessage("Oh dear, you have died!")
        victim.animation(Animations.DEATH, AnimationPriority.HIGH)
        victim.playJingle(Jingles.DEATH_2)
    }

    death {
        // Admin+ don't lose any items in production mode.
        if (victim.rights < loseItemsBelow && !Luna.settings().game().betaMode()) {
            return@death
        }

        val removedItems = removeAll()
        if (removedItems.isNotEmpty()) {
            var keepAmount = 3
            if (victim.skullIcon == SkullIcon.WHITE) {
                keepAmount = 0
            }
            if (keepAmount > 0) {
                val keepItems = ArrayList<Item>()
                val iterator = removedItems.listIterator()
                while (iterator.hasNext()) {
                    val item = iterator.next()
                    if (keepAmount == 0) {
                        break
                    }
                    if (item.amount > 1) {
                        // Only keep 1 of stackable items.
                        keepItems += item.withAmount(1)
                        iterator.set(item.addAmount(-1))
                    } else {
                        keepItems += item
                    }
                    keepAmount--
                    iterator.remove()
                }
                victim.inventory.addAll(keepItems)
            }

            val sourcePlayer = source as? Player
            world.addItem(526, 1, victim.position, sourcePlayer)
            removedItems.forEach { world.addItem(it.id, it.amount, victim.position, sourcePlayer) }
        }
    }

    postDeath {
        val respawnPosition = Luna.settings().game().startingPosition()
        victim.move(respawnPosition)
        victim.unlock()
    }
}
