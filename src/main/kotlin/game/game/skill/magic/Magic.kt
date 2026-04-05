package game.skill.magic

import api.predef.*
import com.google.common.collect.HashMultiset
import game.player.Sound
import game.skill.magic.teleportSpells.TeleportAction
import game.skill.magic.teleportSpells.TeleportStyle
import io.luna.Luna
import io.luna.game.model.LocalSound
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.util.StringUtils

/**
 * Contains utility logic for the Magic skill.
 *
 * @author lare96
 */
object Magic {

    /**
     * Checks whether [plr] meets the requirements needed to cast a spell.
     *
     * If the player satisfies all requirements, this returns the list of items that should be removed when the spell
     * is cast. This includes rune costs and any direct item costs defined by the spell.
     *
     * Returning `null` means the spell cannot be cast.
     *
     * Returning an empty list means the spell can be cast without removing any items, such as when beta mode is
     * enabled or the player has administrator rights.
     *
     * Requirement handling includes:
     * - Magic level validation
     * - Item requirements
     * - Equipment requirements
     * - Staff rune substitution
     * - Combination rune substitution
     * - Standard rune consumption
     *
     * @param plr The player attempting to cast the spell.
     * @param level The minimum Magic level required.
     * @param requirements The spell requirements to validate.
     * @param autocast Whether the check is being performed for autocasting.
     * @return The items to remove if casting is allowed, or `null` if the player does not meet the requirements.
     */
    fun checkRequirements(plr: Player,
                          level: Int,
                          requirements: List<SpellRequirement>,
                          autocast: Boolean = false): List<Item>? {
        if (Luna.settings().game().betaMode() || plr.rights >= PlayerRights.ADMINISTRATOR) {
            return emptyList()
        }
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
                    val pluralName = StringUtils.addPlural(itemName(req.id))
                    plr.sendMessage("You do not have enough $pluralName to cast this spell.")
                    return null
                }
                removeItems += item
            } else if (req is EquipmentRequirement) {
                if (!plr.equipment.contains(req.id)) {
                    val articleName = addArticle(itemName(req.id))
                    plr.sendMessage("You need $articleName equipped to cast this spell.")
                    return null
                }
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
                var countNeeded = 0
                for (rune in combinationRune.represents) {
                    val count = runesNeeded.remove(rune, item.amount)
                    countNeeded = countNeeded.coerceAtLeast(count.coerceAtMost(item.amount))
                }
                if (countNeeded > 0) {
                    removeItems += Item(combinationRune.id, countNeeded)
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
            val runeName = if (autocast) "rune" else itemName(rune.id)
            plr.sendMessage("You do not have enough ${runeName}s to cast this spell.")
            return null
        }
        return removeItems
    }

    /**
     * Checks whether [plr] meets the requirements needed to cast [spell].
     *
     * This is a convenience overload that uses the spell's configured level requirement and item requirement list.
     *
     * @param plr The player attempting to cast the spell.
     * @param spell The spell definition being checked.
     * @param autocast Whether the check is being performed for autocasting.
     * @return The items to remove if casting is allowed, or `null` if the player does not meet the requirements.
     */
    fun checkRequirements(plr: Player,
                          spell: CombatSpellDefinition,
                          autocast: Boolean = false) =
        checkRequirements(plr, spell.level, spell.required, autocast)

    /**
     * Processes a teleport using the regular spellbook style.
     *
     * This handles the staged teleport sequence for normal spellbook teleports, including sound playback, departure
     * animation, departure graphic, movement, and arrival animation.
     *
     * @param action The teleport action being processed.
     * @return `true` if the action should continue processing on the next execution step, or `false` if the teleport
     * sequence has finished.
     */
    fun regularStyle(action: TeleportAction): Boolean {
        val plr = action.mob
        return when (action.executions) {
            0 -> true

            1 -> {
                // Use a local sound so nearby players can hear.
                val sound = LocalSound.of(ctx,
                                          Sound.TELEPORT_REGULAR,
                                          plr.position,
                                          ChunkUpdatableView.globalView())
                sound.display()
                plr.animation(Animation(714))
                plr.graphic(Graphic(111, 92))
                true
            }

            2 -> true
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
     * Processes a teleport using the ancient spellbook style.
     *
     * This handles the staged teleport sequence for ancient teleports, including departure animation, sound playback,
     * departure graphic, and movement to the destination.
     *
     * @param action The teleport action being processed.
     * @return `true` if the action should continue processing on the next execution step, or `false` if the teleport
     * sequence has finished.
     */
    fun ancientStyle(action: TeleportAction): Boolean {
        val plr = action.mob
        return when (action.executions) {
            0 -> {
                plr.animation(Animation(1979))
                val sound = LocalSound.of(ctx,
                                          Sound.TELEPORT_ANCIENT,
                                          plr.position,
                                          ChunkUpdatableView.globalView())
                sound.display()
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
     * Teleports this player to [destination] using the given [style].
     *
     * This submits a [TeleportAction] for the player and invokes [onTeleport] when the action reaches its completion
     * hook.
     *
     * @param destination The target destination.
     * @param style The teleport style to use.
     * @param onTeleport A callback invoked when the teleport finishes.
     */
    fun Player.teleport(destination: Position,
                        style: TeleportStyle = TeleportStyle.REGULAR,
                        onTeleport: () -> Unit = {}) {
        submitAction(object : TeleportAction(this@teleport, destination = destination, style = style) {
            override fun onTeleport() {
                onTeleport()
            }
        })
    }
}