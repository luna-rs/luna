package game.content.flourMill

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import game.player.Sound
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.PersistentVarp

/**
 * Grinds grain into flour at the flour mills.
 *
 * Grain goes into the hopper upstairs, the hopper controls grind it, and the flour collects in the flour bin on the
 * ground floor. The hopper and the bin belong to each player: the bin shows its contents through [PersistentVarp.FLOUR_BIN].
 *
 * @author TheLining
 */
object FlourMill {

    /**
     * The hopper at each mill.
     */
    val HOPPERS = setOf(2714, 2715, 2716, 2717)

    /**
     * The hopper controls at each mill.
     */
    val CONTROLS = setOf(2718, 2719, 2720, 2721)

    /**
     * The flour bin at each mill.
     */
    const val FLOUR_BIN = 1781

    /**
     * The most flour a flour bin holds.
     */
    const val BIN_CAPACITY = 30

    /**
     * Played when grain goes in the hopper and when the controls are pulled.
     */
    val OPERATE = Animation(832)

    const val GRAIN = 1947
    const val POT = 1931
    const val POT_OF_FLOUR = 1933

    /**
     * If this player's grain is waiting in a hopper. One hopper is shared by every mill, like the flour bin.
     */
    var Player.hopperFull by Attr.boolean().persist("mill_hopper_full")

    /**
     * How much flour is in this player's flour bin.
     */
    val Player.flourInBin: Int
        get() = varpManager.getValue(PersistentVarp.FLOUR_BIN)

    /**
     * Puts grain from [plr]'s inventory into the hopper.
     */
    fun fillHopper(plr: Player) {
        if (plr.hopperFull) {
            plr.sendMessage("There is already grain in the hopper.")
            return
        }
        if (plr.inventory.remove(Item(GRAIN))) {
            plr.hopperFull = true
            plr.animation(OPERATE)
            plr.sendMessage("You put the grain in the hopper.")
        }
    }

    /**
     * Grinds the grain in [plr]'s hopper into the flour bin.
     */
    fun operateControls(plr: Player) {
        plr.playSound(Sound.HOPPERLEVER)
        if (!plr.hopperFull) {
            plr.sendMessage("You operate the empty hopper. Nothing interesting happens.")
            return
        }
        if (plr.flourInBin >= BIN_CAPACITY) {
            plr.sendMessage("The flour bin downstairs is full, I should empty it first.")
            return
        }
        // TODO Recipe for Disaster: sweetcorn makes cornflour, and the bin must be empty before switching between them.
        val flour = plr.flourInBin + 1
        plr.hopperFull = false
        plr.varpManager.setAndSendValue(PersistentVarp.FLOUR_BIN, flour)
        plr.animation(OPERATE)
        plr.playSound(Sound.MILLSTONES)
        plr.sendMessage("You operate the hopper. The grain slides down the chute.")
        if (flour == BIN_CAPACITY) {
            plr.sendMessage("The flour bin downstairs is now full.")
        }
    }

    /**
     * Fills an empty pot in [plr]'s inventory from the flour bin.
     */
    fun emptyBin(plr: Player) {
        if (plr.flourInBin <= 0) {
            plr.sendMessage("The flour bin is already empty.")
            return
        }
        if (!plr.inventory.contains(POT)) {
            plr.sendMessage("You need an empty pot to hold the flour in.")
            return
        }
        if (plr.inventory.replace(POT, POT_OF_FLOUR)) {
            val left = plr.flourInBin - 1
            plr.varpManager.setAndSendValue(PersistentVarp.FLOUR_BIN, left)
            plr.sendMessage(if (left == 0) "You fill a pot with the last of the flour in the bin." else
                                "You fill a pot with flour from the bin.")
        }
    }
}
