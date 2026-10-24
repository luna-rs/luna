package engine.combat.death

import api.combat.death.DeathHookHandler
import api.combat.death.DeathHookHandler.deathItems
import api.combat.death.PlayerDeathItems
import api.bot.social.BotWildernessLures
import api.predef.*
import api.predef.ext.*
import engine.bot.speech.BotReactions
import game.player.Animations
import game.player.Jingles
import game.skill.runecrafting.essencePouch.EssencePouch
import io.luna.Luna
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.DeathGroundItem
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation.AnimationPriority

/**
 * Builds and registers the default death hook for all players.
 */
DeathHookHandler.setDefaultHook(Player::class) {
    preDeath {
        victim.sendMessage("Oh dear, you have died!")
        victim.animation(Animations.DEATH, AnimationPriority.IMMUTABLE)
        victim.playJingle(Jingles.DEATH_2)
        BotReactions.reactToOtherDied(source, victim)
    }

    death {
        // Admin+ don't lose any items in production mode.
        if (!PlayerDeathItems.losesItems(victim, Luna.settings().game().betaMode())) {
            BotWildernessLures.onDeath(victim, source, false)
            return@death
        }

        EssencePouch.loseOnDeath(victim)
        val removedItems = removeAll(sort = false)
        if (removedItems.isNotEmpty()) {
            val selection = PlayerDeathItems.select(removedItems, PlayerDeathItems.keepCount(victim))
            BotWildernessLures.onDeath(victim, source, selection.lost.isNotEmpty())
            victim.inventory.addAll(selection.kept)

            val view =
                if (source is Player) ChunkUpdatableView.localView(source) else ChunkUpdatableView.localView(victim)
            world.addItem(DeathGroundItem(ctx, 526, 1, victim.position, view, victim))
            selection.lost.forEach {
                if(it.amount > 0) {
                    val groundItem = DeathGroundItem(ctx, it.id, it.amount, victim.position, view, victim)
                    world.addItem(groundItem)
                    // Retain every generated drop in the recovery collection, including earlier unrecovered drops.
                    victim.deathItems += groundItem
                }
            }
        } else BotWildernessLures.onDeath(victim, source, false)
    }

    postDeath {
        val respawnPosition = Luna.settings().game().startingPosition()
        victim.move(respawnPosition)
    }
}
