package world.player.skill.magic

import api.predef.*
import com.google.common.collect.HashMultiset
import io.luna.game.model.LocalSound
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import world.player.Sounds
import world.player.skill.magic.teleportSpells.TeleportAction
import world.player.skill.magic.teleportSpells.TeleportStyle

/**
 * A collection of utility functions related to the Magic skill.
 */
object Magic {

    /**
     * Checks if [plr] meets the requirements defined by [level] and [requirements]. If they do, a list of runes and
     * items required for the spell to be cast will be returned. If they don't, [null] will be returned.
     */
    fun checkRequirements(plr: Player, level: Int, requirements: List<SpellRequirement>): List<Item>? {
        if (plr.magic.level < level) {
            plr.sendMessage("Your Magic level is not high enough for this spell.")
            return null
        }

        // Loop through requirements.
        val runesNeeded = HashMultiset.create<Rune>()
        val removeItems = ArrayList<Item>(4)
        for (req in requirements) {
            if (req is RuneRequirement) { // Store runes required.
                runesNeeded.add(req.rune, req.amount)
            } else if (req is ItemRequirement) { // Determine if we have the correct items.
                val item = Item(req.id, req.amount)
                if (!plr.inventory.contains(item)) {
                    plr.sendMessage("You do not have enough ${itemName(req.id)}s to cast this spell.")
                    return null
                }
                removeItems += item
            }
        }

        // Determine which runes need to be removed. First check for staves.
        val weaponId = plr.equipment.weapon?.id
        if (weaponId != null && runesNeeded.isNotEmpty()) {
            val equippedStaff = Staff.ID_TO_STAFF[weaponId]
            if (equippedStaff != null) {
                for (rune in equippedStaff.represents) {
                    // Staves make it so we require 0 runes.
                    runesNeeded.setCount(rune, 0)
                }
            }
        }

        // Then check for combination runes.
        for (item in plr.inventory) {
            if (runesNeeded.isEmpty()) {
                break
            }
            if (item == null) {
                continue
            }
            val combinationRune = CombinationRune.ID_TO_RUNE[item.id]
            if (combinationRune != null) {
                for (rune in combinationRune.represents) {
                    // Match combination runes with base runes.
                    val previousCount = runesNeeded.remove(rune, item.amount)
                    if (previousCount > 0) {
                        removeItems += Item(combinationRune.id, previousCount.coerceAtMost(item.amount))
                        break
                    }
                }
            }
        }

        // Then check for regular runes.
        for (item in plr.inventory) {
            if (runesNeeded.isEmpty()) {
                break
            }
            if (item == null) {
                continue
            }
            // Remove base runes if needed.
            val rune = Rune.ID_TO_RUNE[item.id]
            if (rune != null) {
                val previousCount = runesNeeded.remove(rune, item.amount)
                if (previousCount > 0) {
                    removeItems += Item(rune.id, previousCount.coerceAtMost(item.amount))
                }
            }
        }

        // Runes were leftover, meaning we didn't satisfy the requirements.
        for (rune in runesNeeded.elementSet()) {
            plr.sendMessage("You do not have enough ${itemName(rune.id)}s to cast this spell.")
            return null
        }
        return removeItems
    }

    /**
     * Function for helping [TeleportAction] move the player in a regular spellbook style.
     */
    internal fun regularStyle(action: TeleportAction): Boolean {
        val plr = action.mob
        return when (action.executions) {
            0 -> {
                val sound = LocalSound(ctx,
                                       Sounds.TELEPORT,
                                       plr.position,
                                       ChunkUpdatableView.globalView(),
                                       Position.VIEWING_DISTANCE / 2,
                                       100)
                sound.display()
                true
            }

            1 -> {
                plr.animation(Animation(714))
                true
            }

            2 -> {
                plr.graphic(Graphic(308, 50))
                true
            }

            3 -> true
            4 -> {
                plr.move(action.destination)
                plr.animation(Animation(715))
                false
            }

            else -> false
        }
    }

    /**
     * Function for helping [TeleportAction] move the player in ancient spellbook style.
     */
    internal fun ancientStyle(action: TeleportAction): Boolean {
        val plr = action.mob
        return when (action.executions) {
            0 -> {
                plr.animation(Animation(1979))
                true
            }

            1 -> {
                plr.graphic(Graphic(392))
                true
            }

            2 -> true
            3 -> true
            4 -> {
                plr.move(action.destination)
                false
            }

            else -> false
        }
    }

    /**
     * An extension function that enables the underlying player to teleport somewhere.
     */
    fun Player.teleport(destination: Position, style: TeleportStyle, onTeleport: () -> Unit = {}) {
        submitAction(object : TeleportAction(this@teleport, destination = destination, style = style) {
            override fun onTeleport() {
                onTeleport()
            }
        })
    }
}