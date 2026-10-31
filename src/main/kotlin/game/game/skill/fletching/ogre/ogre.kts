package game.skill.fletching.ogre

import api.predef.*
import api.predef.ext.*
import game.skill.fletching.ogre.OgreFletching.ACHEY_LOGS
import game.skill.fletching.ogre.OgreFletching.BOW_STRING
import game.skill.fletching.ogre.OgreFletching.CHISEL
import game.skill.fletching.ogre.OgreFletching.FEATHER
import game.skill.fletching.ogre.OgreFletching.FLIGHTED_OGRE_ARROW
import game.skill.fletching.ogre.OgreFletching.KNIFE
import game.skill.fletching.ogre.OgreFletching.OGRE_ARROW_SHAFT
import game.skill.fletching.ogre.OgreFletching.UNSTRUNG_COMP_BOW
import game.skill.fletching.ogre.OgreFletching.WOLFBONE_ARROWTIPS
import game.skill.fletching.ogre.OgreFletching.WOLF_BONES
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueUtils
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.mob.overlay.NumberInput
import io.luna.net.msg.out.WidgetTextMessageWriter

/**
 * Opens the achey tree log make menu, labelled as in 2006 rather than with the item names.
 */
fun openAcheyMenu(plr: Player) {
    val menu = object : MakeItemDialogue(*AcheyProduct.IDS) {
        override fun make(plr: Player, id: Int, index: Int, forAmount: Int) =
            plr.submitAction(CutAcheyLogAction(plr, AcheyProduct.VALUES[index], forAmount))
    }
    menu.setOpenAction {
        val labels = DialogueUtils.makeItemTextWidgets(AcheyProduct.VALUES.size)
        AcheyProduct.VALUES.forEachIndexed { index, product ->
            it.queue(WidgetTextMessageWriter(product.label, labels[index]))
        }
    }
    plr.overlays.open(menu)
}

/**
 * Starts chiselling [amount] sets of wolf bones, or all of them, if the [ChiselBonesMenu] is open.
 */
fun chiselBones(plr: Player, amount: Int? = null) {
    if (plr.overlays[ChiselBonesMenu::class] != null) {
        plr.overlays.closeWindows()
        plr.submitAction(ChiselBonesAction(plr, amount ?: plr.inventory.computeAmountForId(WOLF_BONES)))
    }
}

useItem(KNIFE).onItem(ACHEY_LOGS) { openAcheyMenu(plr) }
useItem(CHISEL).onItem(WOLF_BONES) { plr.overlays.open(ChiselBonesMenu()) }
useItem(FEATHER).onItem(OGRE_ARROW_SHAFT) { OgreFletching.featherShafts(plr) }
useItem(WOLFBONE_ARROWTIPS).onItem(FLIGHTED_OGRE_ARROW) { OgreFletching.tipArrows(plr) }
useItem(BOW_STRING).onItem(UNSTRUNG_COMP_BOW) { OgreFletching.stringCompBow(plr) }
for (arrow in BrutalArrow.VALUES) {
    useItem(arrow.nails).onItem(FLIGHTED_OGRE_ARROW) { OgreFletching.nailArrows(plr, arrow) }
}

button(ChiselBonesMenu.MAKE_1) { chiselBones(plr, 1) }
button(ChiselBonesMenu.MAKE_5) { chiselBones(plr, 5) }
button(ChiselBonesMenu.MAKE_ALL) { chiselBones(plr) }
button(ChiselBonesMenu.MAKE_X) {
    if (plr.overlays[ChiselBonesMenu::class] != null) {
        plr.overlays.open(object : NumberInput() {
            override fun input(player: Player, value: Int) {
                player.overlays.closeWindows()
                player.submitAction(ChiselBonesAction(player, value))
            }
        })
    }
}
