package game.skill.crafting.snelmCrafting

import api.predef.*
import game.player.Sound
import game.skill.crafting.gemCutting.Gem.Companion.CHISEL
import io.luna.game.model.item.Item

/**
 * The Crafting level needed to chisel a snail shell into a snelm.
 */
val LEVEL = 15

/**
 * The Crafting experience for one snelm.
 */
val EXPERIENCE = 32.5

/**
 * Each blamish shell and the snelm chiselled from it. Round shells make round snelms and pointed shells make
 * pointed ones.
 */
val SHELL_TO_SNELM = mapOf(
    3345 to 3327, // Myre, round
    3347 to 3329, // Blood'n'tar, round
    3349 to 3331, // Ochre, round
    3351 to 3333, // Bruise blue, round
    3353 to 3335, // Broken bark, round
    3355 to 3337, // Myre, pointed
    3357 to 3339, // Blood'n'tar, pointed
    3359 to 3341, // Ochre, pointed
    3361 to 3343 // Bruise blue, pointed
)

for ((shell, snelm) in SHELL_TO_SNELM) {
    useItem(CHISEL).onItem(shell) {
        if (plr.crafting.level < LEVEL) {
            plr.sendMessage("You need a Crafting level of $LEVEL to make a snelm.")
            return@onItem
        }
        val index = plr.inventory.computeIndexForId(shell)
        if (index == -1) return@onItem
        plr.inventory[index] = Item(snelm)
        plr.playSound(Sound.CHISEL)
        plr.crafting.addExperience(EXPERIENCE)
        plr.sendMessage("You craft the shell into a snelm.")
    }
}
