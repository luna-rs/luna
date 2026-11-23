package engine.combat.death

import api.bot.isRealBot

import api.combat.death.DeathHookHandler
import api.combat.death.DeathHookHandler.deathItems
import api.combat.death.PlayerDeathItems
import api.bot.social.BotWildernessLures
import api.predef.*
import api.predef.ext.*
import engine.bot.speech.BotReactions
import engine.bot.speech.BotPkingSpeechPool
import engine.controllers.Controllers.inWilderness
import engine.item.developerItem.DeveloperItemHandler
import game.bot.scripts.DeathReflexScript.Companion.lastKiller
import game.player.Animations
import game.player.Jingles
import game.skill.runecrafting.essencePouch.EssencePouch
import io.luna.Luna
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.DeathGroundItem
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.bot.Bot

/**
 * Builds and registers the default death hook for all players.
 */
DeathHookHandler.setDefaultHook(Player::class) {
    preDeath {
        victim.sendMessage("Oh dear, you have died!")
        victim.animation(Animations.DEATH, AnimationPriority.IMMUTABLE)
        victim.playJingle(Jingles.DEATH_2)
        BotReactions.reactToOtherDied(source, victim)
        if (victim.isRealBot()) {
            (victim as? Bot)?.let { engine.bot.coordinator.EquipmentDropJobs.death(it) }
            // The damage stack only counts players, so an NPC killer is the attacker of the last hit.
            val killer = source ?: victim.combat.lastDamageReceived?.attacker?.takeIf { it != victim }
            victim.lastKiller = killer
            if (killer != null) BotReactions.reactToDied(victim, killer)
        }
        if (source.isRealBot()) BotReactions.reactToKill(source, victim)
    }

    death {
        // Admin+ don't lose any items in production mode.
        if (!PlayerDeathItems.losesItems(victim, Luna.settings().game().betaMode())) {
            BotWildernessLures.onDeath(victim, source, false)
            return@death
        }

        DeveloperItemHandler.destroy(victim)
        EssencePouch.loseOnDeath(victim)
        val removedItems = removeAll(sort = false)
        if (removedItems.isNotEmpty()) {
            val selection = PlayerDeathItems.select(removedItems, PlayerDeathItems.keepCount(victim))
            BotWildernessLures.onDeath(victim, source, selection.lost.isNotEmpty())
            victim.inventory.addAll(selection.kept)
            if (victim.isEventBot) return@death

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
        if (source.isRealBot() && victim.inWilderness()) BotPkingSpeechPool.knockout(source, victim)
        val respawnPosition = Luna.settings().game().startingPosition()
        victim.move(respawnPosition)
    }
}
