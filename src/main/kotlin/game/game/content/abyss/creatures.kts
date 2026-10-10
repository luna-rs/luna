package game.content.abyss

import api.combat.death.DeathHookHandler
import api.predef.*
import api.predef.ext.*
import game.skill.runecrafting.essencePouch.EssencePouch
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.DeathGroundItem
import io.luna.game.model.mob.Player

/**
 * The Abyssal leech, guardian and walker.
 */
val ABYSS_CREATURES = listOf(2263, 2264, 2265)

/**
 * The chance that a creature drops the killer's next essence pouch.
 */
val POUCH_CHANCE = 6 of 256

for (id in ABYSS_CREATURES) {
    DeathHookHandler.addNpcHook(id) {
        death {
            val killer = source
            if (killer is Player && rand(POUCH_CHANCE)) {
                val pouch = EssencePouch.nextDrop(killer)
                if (pouch != null) {
                    val view = ChunkUpdatableView.localView(killer)
                    world.addItem(DeathGroundItem(ctx, pouch.id, 1, victim.position, view, victim))
                }
            }
        }
        DeathHookHandler.defaultNpcHook?.invoke(this)
    }
}
