package game.skill.cooking.prepareFood

import api.predef.*
import game.skill.Skills
import game.skill.cooking.cookFood.Cooking
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

val PITTA_BREAD = 1865
val UGTHANKI_KEBAB = 1883
val SMELLY_KEBAB = 1885
val RAW_OOMLIE = 2337
val PALM_LEAF = 2339
val WRAPPED_OOMLIE = 2341
val BURNT_OOMLIE = 2426
val SWAMP_TOAD = 2150
val TOADS_LEGS = 2152

/**
 * Makes [mix] once, if the player has the level and the knife it needs.
 */
fun make(plr: Player, mix: Mix) {
    if (plr.cooking.level < mix.lvl) {
        plr.sendMessage("You need a Cooking level of ${mix.lvl} to make ${itemName(mix.product).lowercase()}.")
        return
    }
    if (mix.knife && !plr.inventory.contains(Mix.KNIFE)) {
        plr.sendMessage("You need a knife to cut that up.")
        return
    }
    val used = listOf(mix.first, mix.second).filter { it !in mix.kept }.map { Item(it) }
    if (!plr.inventory.containsAll(used)) return
    plr.inventory.updateAll((listOf(mix.product) + mix.returned).map { Item(it) }, used)
    plr.sendMessage(mix.message)
    if (mix.exp > 0.0) plr.cooking.addExperience(mix.exp)
}

/**
 * Fills a pitta bread with kebab mix. A poor cook can spoil it, which gives the smelly kebab and no experience.
 */
fun makeKebab(plr: Player) {
    val used = listOf(Item(Mix.KEBAB_MIX), Item(PITTA_BREAD))
    if (!plr.inventory.containsAll(used)) return
    val good = Skills.success(118, 492, plr.cooking.level)
    plr.inventory.updateAll(listOf(Item(Mix.BOWL), Item(if (good) UGTHANKI_KEBAB else SMELLY_KEBAB)), used)
    plr.sendMessage("You put the mixture into the pitta bread.")
    if (good) {
        plr.sendMessage("You make a delicious ugthanki kebab.")
        plr.cooking.addExperience(40.0)
    } else {
        plr.sendMessage("You make an ugthanki kebab.")
    }
}

/**
 * Wraps a raw oomlie in a palm leaf so that it can be cooked.
 */
fun wrapOomlie(plr: Player) {
    if (plr.cooking.level < 50) {
        plr.sendMessage("You need a Cooking level of 50 to make wrapped oomlie meat.")
        return
    }
    val used = listOf(Item(RAW_OOMLIE), Item(PALM_LEAF))
    if (!plr.inventory.containsAll(used)) return
    plr.inventory.updateAll(listOf(Item(WRAPPED_OOMLIE)), used)
    plr.sendMessage("You carefully construct a small parcel out of the palm leaf.")
    plr.sendMessage("The palm leaf should protect the meat from being burnt.")
    plr.cooking.addExperience(10.0)
}

Mix.ALL.forEach { mix -> useItem(mix.first).onItem(mix.second) { make(plr, mix) } }

useItem(Mix.KEBAB_MIX).onItem(PITTA_BREAD) { makeKebab(plr) }

useItem(RAW_OOMLIE).onItem(PALM_LEAF) { wrapOomlie(plr) }

// Unwrapped oomlie meat is ruined by any heat.
Cooking.COOKING_OBJECTS.forEach { source ->
    useItem(RAW_OOMLIE).onObject(source) {
        if (plr.inventory.remove(usedItemIndex, Item(RAW_OOMLIE))) {
            plr.inventory.add(usedItemIndex, Item(BURNT_OOMLIE))
            plr.sendMessage("The meat is far too delicate to cook like this. Perhaps you should wrap it up to " +
                "protect it from the heat.")
        }
    }
}

item1(SWAMP_TOAD) {
    if (plr.inventory.remove(index, Item(SWAMP_TOAD))) {
        plr.inventory.add(index, Item(TOADS_LEGS))
        plr.sendMessage("You pull the legs off the toad.")
        plr.sendMessage("Poor toad. At least they'll grow back.")
    }
}
