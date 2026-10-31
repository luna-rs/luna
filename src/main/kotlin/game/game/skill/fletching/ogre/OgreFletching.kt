package game.skill.fletching.ogre

import api.predef.*
import game.player.Messages
import game.player.Sound
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Ogre arrows, brutal arrows and the composite ogre bow. Cutting achey tree logs is a [CutAcheyLogAction] and chiselling
 * wolf bones a [ChiselBonesAction], each started from a menu; every other step happens on the spot when one item is
 * used on the other, with no menu and no animation.
 *
 * @author lare96
 */
object OgreFletching {

    const val ACHEY_LOGS = 2862
    const val OGRE_ARROW_SHAFT = 2864
    const val FLIGHTED_OGRE_ARROW = 2865
    const val WOLF_BONES = 2859
    const val WOLFBONE_ARROWTIPS = 2861
    const val OGRE_ARROW = 2866
    const val UNSTRUNG_COMP_BOW = 4825
    const val COMP_OGRE_BOW = 4827
    const val KNIFE = 946
    const val CHISEL = 1755
    const val HAMMER = 2347
    const val FEATHER = 314
    const val BOW_STRING = 1777

    /**
     * The level needed to make ogre arrows, and every step leading to them.
     */
    const val OGRE_ARROW_LEVEL = 5

    /**
     * Ogre arrow shafts cut from one achey tree log.
     */
    val SHAFTS_PER_LOG = 2..6

    /**
     * Wolfbone arrowtips chiselled from one set of wolf bones.
     */
    val TIPS_PER_BONE = 2..6

    /**
     * Feathers attached to each flighted ogre arrow.
     */
    const val FEATHERS_PER_ARROW = 4

    /**
     * The most arrows one use makes.
     */
    const val SET_AMOUNT = 6

    /**
     * Fletching experience per flighted ogre arrow, ogre arrow and wolfbone arrowtip.
     */
    const val FLIGHTED_EXP = 0.9
    const val OGRE_ARROW_EXP = 1.0
    const val TIP_EXP = 2.5

    /**
     * The level and experience for stringing the comp ogre bow.
     */
    const val COMP_BOW_LEVEL = 30
    const val COMP_BOW_EXP = 45.0

    /**
     * The chat message for a cut.
     */
    fun cutMessage(product: AcheyProduct, made: Int) =
        when (product) {
            AcheyProduct.OGRE_ARROW_SHAFTS -> "You carefully cut the wood into $made arrow shafts."
            AcheyProduct.COMP_OGRE_BOW -> "You carefully cut the wood into a composite ogre bow."
        }

    /**
     * Attaches four feathers to each of up to [SET_AMOUNT] ogre arrow shafts.
     *
     * @return `true` if any flighted ogre arrows were made.
     */
    fun featherShafts(plr: Player): Boolean {
        if (!hasLevel(plr, OGRE_ARROW_LEVEL, "attach this")) {
            return false
        }
        // TODO Ogre arrows need Big Chompy Bird Hunting completed. Check it here once quests exist.
        val inventory = plr.inventory
        val shafts = inventory.computeAmountForId(OGRE_ARROW_SHAFT)
        val feathers = inventory.computeAmountForId(FEATHER)
        val amount = minOf(shafts, feathers / FEATHERS_PER_ARROW, SET_AMOUNT)
        if (amount == 0 || !hasSpace(plr, FLIGHTED_OGRE_ARROW, shafts == amount,
                                     feathers == amount * FEATHERS_PER_ARROW)) {
            return false
        }
        inventory.removeAll(listOf(Item(OGRE_ARROW_SHAFT, amount), Item(FEATHER, amount * FEATHERS_PER_ARROW)))
        inventory.add(Item(FLIGHTED_OGRE_ARROW, amount))
        plr.sendMessage("You attach ${amount * FEATHERS_PER_ARROW} feathers to $amount ogre arrow shafts.")
        plr.fletching.addExperience(FLIGHTED_EXP * amount)
        return true
    }

    /**
     * Attaches wolfbone arrowtips to up to [SET_AMOUNT] flighted ogre arrows.
     *
     * @return `true` if any ogre arrows were made.
     */
    fun tipArrows(plr: Player): Boolean {
        if (!hasLevel(plr, OGRE_ARROW_LEVEL, "attach this")) {
            return false
        }
        // TODO Ogre arrows need Big Chompy Bird Hunting completed. Check it here once quests exist.
        val inventory = plr.inventory
        val flighted = inventory.computeAmountForId(FLIGHTED_OGRE_ARROW)
        val tips = inventory.computeAmountForId(WOLFBONE_ARROWTIPS)
        val amount = minOf(flighted, tips, SET_AMOUNT)
        if (amount == 0 || !hasSpace(plr, OGRE_ARROW, flighted == amount, tips == amount)) {
            return false
        }
        inventory.removeAll(listOf(Item(FLIGHTED_OGRE_ARROW, amount), Item(WOLFBONE_ARROWTIPS, amount)))
        inventory.add(Item(OGRE_ARROW, amount))
        plr.sendMessage("You make $amount ogre arrows.")
        plr.fletching.addExperience(OGRE_ARROW_EXP * amount)
        return true
    }

    /**
     * Hammers one nail into each of up to [SET_AMOUNT] flighted ogre arrows. A hammer must be in the inventory.
     *
     * @return `true` if any brutal arrows were made.
     */
    fun nailArrows(plr: Player, arrow: BrutalArrow): Boolean {
        if (!hasLevel(plr, arrow.level, "attach this")) {
            return false
        }
        // TODO Brutal arrows are learned in Zogre Flesh Eaters. Check it here once quests exist.
        val inventory = plr.inventory
        if (!inventory.contains(HAMMER)) {
            plr.sendMessage("Nothing interesting happens.")
            return false
        }
        val flighted = inventory.computeAmountForId(FLIGHTED_OGRE_ARROW)
        val nails = inventory.computeAmountForId(arrow.nails)
        val amount = minOf(flighted, nails, SET_AMOUNT)
        if (amount == 0 || !hasSpace(plr, arrow.id, flighted == amount, nails == amount)) {
            return false
        }
        inventory.removeAll(listOf(Item(FLIGHTED_OGRE_ARROW, amount), Item(arrow.nails, amount)))
        inventory.add(Item(arrow.id, amount))
        plr.sendMessage("You make $amount brutal arrows.")
        plr.fletching.addExperience(arrow.exp * amount)
        return true
    }

    /**
     * Strings one unstrung comp bow.
     *
     * @return `true` if a bow was strung.
     */
    fun stringCompBow(plr: Player): Boolean {
        if (!hasLevel(plr, COMP_BOW_LEVEL, "string this bow")) {
            return false
        }
        // TODO The comp ogre bow is learned in Zogre Flesh Eaters. Check it here once quests exist.
        val inventory = plr.inventory
        if (!inventory.containsAll(BOW_STRING, UNSTRUNG_COMP_BOW)) {
            return false
        }
        inventory.removeAll(listOf(Item(UNSTRUNG_COMP_BOW), Item(BOW_STRING)))
        inventory.add(Item(COMP_OGRE_BOW))
        plr.playSound(Sound.STRING_BOW)
        plr.sendMessage("You add a string to the bow.")
        plr.fletching.addExperience(COMP_BOW_EXP)
        return true
    }

    /**
     * Sends the level message and returns `false` if [plr]'s Fletching level is below [level].
     */
    private fun hasLevel(plr: Player, level: Int, what: String): Boolean {
        if (plr.fletching.level < level) {
            plr.sendMessage("You need a Fletching level of $level to $what.")
            return false
        }
        return true
    }

    /**
     * Checks for room for a stack of [product] once the inputs are taken, sending the full inventory message if
     * there is none. [firstUsedUp] and [secondUsedUp] tell if an input stack will be used up, freeing its slot.
     */
    private fun hasSpace(plr: Player, product: Int, firstUsedUp: Boolean, secondUsedUp: Boolean): Boolean {
        val inventory = plr.inventory
        val freed = (if (firstUsedUp) 1 else 0) + (if (secondUsedUp) 1 else 0)
        val needed = if (inventory.contains(product)) 0 else 1
        if (needed > inventory.computeRemainingSize() + freed) {
            plr.sendMessage(Messages.inventoryFull())
            return false
        }
        return true
    }
}
