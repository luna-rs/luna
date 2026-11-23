package game.skill.cooking.gnomeCooking

import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.player.Sound
import game.skill.Skills
import game.skill.cooking.cookFood.CookSource
import game.skill.cooking.cookFood.Cooking
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Splits [text] into dialogue lines of at most [width] characters, breaking at spaces.
 */
fun wrap(text: String, width: Int = 52): List<String> {
    val lines = ArrayList<String>()
    var line = StringBuilder()
    for (word in text.split(' ')) {
        if (line.isNotEmpty() && line.length + word.length + 1 > width) {
            lines += line.toString()
            line = StringBuilder()
        }
        if (line.isNotEmpty()) line.append(' ')
        line.append(word)
    }
    if (line.isNotEmpty()) lines += line.toString()
    return lines
}

/**
 * Shows what a half baked dish or the shaker holds.
 */
fun inspect(plr: Player, intro: String, empty: String, header: String, contents: String) {
    val dialogue = plr.newDialogue().text(intro)
    if (contents.isEmpty()) {
        dialogue.text(empty).open()
        return
    }
    val lines = wrap("$contents.")
    dialogue.text(*(listOf(header) + lines.take(3)).toTypedArray())
    lines.drop(3).chunked(4).forEach { dialogue.text(*it.toTypedArray()) }
    dialogue.open()
}

/**
 * Turns the player to the oven and plays the cooking animation.
 */
fun useOven(plr: Player, oven: GameObject) {
    plr.face(oven.position)
    plr.animation(Animations.RANGE_COOKING)
    plr.playSound(Sound.FRY)
}

/**
 * Presses Gianne dough into the tin of [dish].
 */
fun shape(plr: Player, dish: GnomeDish) {
    val used = listOf(Item(GnomeCooking.GIANNE_DOUGH), Item(dish.tin))
    if (!plr.inventory.containsAll(used)) return
    plr.inventory.updateAll(listOf(Item(dish.raw)), used)
    plr.sendMessage(dish.shapeMessage)
}

/**
 * The first bake. A burnt dish comes out of its tin; a half baked one stays in it for the second bake.
 */
fun halfBake(plr: Player, oven: GameObject, index: Int, dish: GnomeDish) {
    if (!plr.inventory.remove(index, Item(dish.raw))) return
    useOven(plr, oven)
    if (Skills.success(118, 492, plr.cooking.level)) {
        // A fresh dish starts plain unless the player is still carrying an earlier one.
        if (!plr.inventory.contains(dish.halfBaked)) GnomeCooking.setFilling(plr, dish, emptyList())
        plr.inventory.add(index, Item(dish.halfBaked))
        plr.sendMessage(dish.halfBakeMessage)
        plr.cooking.addExperience(GnomeCooking.HALF_BAKE_EXP)
    } else {
        plr.inventory.add(index, Item(dish.burnt))
        plr.inventory.add(Item(dish.tin))
        plr.sendMessage(dish.burnMessage)
    }
}

/**
 * Adds one filling to the half baked [dish]. Gnome spice is shaken on and never used up.
 */
fun fill(plr: Player, dish: GnomeDish, ingredient: Int) {
    val filling = GnomeCooking.filling(plr, dish)
    if (filling.size >= GnomeCooking.CAPACITY) {
        plr.sendMessage(dish.fullMessage)
        return
    }
    if (ingredient != GnomeCooking.GNOME_SPICE && !plr.inventory.remove(Item(ingredient))) return
    GnomeCooking.setFilling(plr, dish, filling + ingredient)
    plr.sendMessage(dish.addMessage(ingredient, itemName(ingredient).lowercase()))
}

/**
 * The second bake, which turns the filled dish into whatever its filling makes and frees the tin.
 */
fun bake(plr: Player, oven: GameObject, index: Int, dish: GnomeDish) {
    val recipe = GnomeCooking.match(GnomeCooking.RECIPES.getValue(dish), GnomeCooking.filling(plr, dish))
    if (recipe != null && plr.cooking.level < recipe.lvl) {
        plr.sendMessage("You need a Cooking level of ${recipe.lvl} to make ${itemName(recipe.product).lowercase()}.")
        return
    }
    if (!plr.inventory.remove(index, Item(dish.halfBaked))) return
    useOven(plr, oven)
    GnomeCooking.setFilling(plr, dish, emptyList())
    plr.inventory.add(index, Item(recipe?.product ?: dish.spoilt))
    plr.inventory.add(Item(dish.tin))
    plr.sendMessage(dish.bakeMessage)
    if (recipe?.finished == true) plr.cooking.addExperience(GnomeCooking.COCKTAIL_EXP)
}

/**
 * Adds [topping] to the unfinished dish or cocktail [id]. The wrong topping spoils it.
 */
fun top(plr: Player, id: Int, topping: Int) {
    val unfinished = GnomeCooking.UNFINISHED.getValue(id)
    val index = plr.inventory.computeIndexForId(id)
    if (index == -1 || !plr.inventory.contains(topping)) return
    val name = itemName(topping).lowercase()
    val dish = unfinished.dish
    plr.sendMessage(when {
        dish != null && topping == GnomeCooking.POT_OF_CREAM -> "You pour thick cream over the ${dish.noun}."
        dish != null -> "You sprinkle the $name over the ${dish.noun}."
        topping == GnomeCooking.EQUA_LEAVES -> "You sprinkle the leaves over the drink."
        topping == GnomeCooking.POT_OF_CREAM -> "You pour the thick cream into the drink."
        topping == GnomeCooking.LEMON_CHUNKS -> "You add the diced lemon to the drink."
        topping == GnomeCooking.LEMON_SLICES -> "You add a lemon slice to the drink."
        topping == GnomeCooking.ORANGE_CHUNKS -> "You add the diced orange to the drink."
        topping == GnomeCooking.ORANGE_SLICES -> "You add an orange slice to the drink."
        topping == GnomeCooking.LIME_SLICES -> "You add a lime slice to the drink."
        else -> "You add the $name to the drink."
    })
    if (topping != GnomeCooking.GNOME_SPICE) plr.inventory.remove(Item(topping))
    val product = unfinished.next[topping]
        ?: if (dish != null) dish.spoilt else GnomeCooking.spoiltDrink(unfinished, topping)
    plr.inventory[index] = Item(product)
    if (product in GnomeCooking.FINISHED) {
        plr.cooking.addExperience(if (dish != null) GnomeCooking.DISH_EXP else GnomeCooking.COCKTAIL_EXP)
    }
}

/**
 * Adds one ingredient to the cocktail shaker. Milk leaves its bucket behind.
 */
fun mix(plr: Player, ingredient: Int) {
    val filling = GnomeCooking.filling(plr, null)
    if (filling.size >= GnomeCooking.CAPACITY) {
        plr.sendMessage("The cocktail shaker is too full to fit anymore ingredients.")
        return
    }
    val index = plr.inventory.computeIndexForId(ingredient)
    if (index == -1 || !plr.inventory.remove(index, Item(ingredient))) return
    val name = itemName(ingredient).lowercase()
    plr.sendMessage(when (ingredient) {
        GnomeCooking.EQUA_LEAVES -> "You sprinkle the equa leaves into your cocktail shaker..."
        GnomeCooking.LEMON, GnomeCooking.ORANGE, GnomeCooking.PINEAPPLE, GnomeCooking.LIME,
        GnomeCooking.DWELLBERRIES -> "You squeeze the juice from the $name into your cocktail shaker..."
        else -> "You pour the $name into your cocktail shaker..."
    })
    if (ingredient == GnomeCooking.BUCKET_OF_MILK) plr.inventory.add(index, Item(GnomeCooking.EMPTY_BUCKET))
    GnomeCooking.setFilling(plr, null, filling + ingredient)
}

/**
 * Pours the shaker into a cocktail glass, which makes whatever the mix adds up to.
 */
fun pour(plr: Player) {
    val filling = GnomeCooking.filling(plr, null)
    if (filling.isEmpty()) {
        plr.sendMessage("There's nothing to pour from the cocktail shaker.")
        return
    }
    val recipe = GnomeCooking.match(GnomeCooking.COCKTAILS, filling)
    if (recipe != null && plr.cooking.level < recipe.lvl) {
        plr.sendMessage("You need a Cooking level of ${recipe.lvl} to make that cocktail.")
        return
    }
    val index = plr.inventory.computeIndexForId(GnomeCooking.COCKTAIL_GLASS)
    if (index == -1) return
    plr.inventory[index] = Item(recipe?.product ?: GnomeCooking.ODD_COCKTAIL)
    GnomeCooking.setFilling(plr, null, emptyList())
}

/**
 * Warms an unfinished cocktail in the oven.
 */
fun warm(plr: Player, oven: GameObject, index: Int, drink: Int) {
    val warmed = GnomeCooking.WARMED.getValue(drink)
    if (plr.inventory[index]?.id != drink) return
    useOven(plr, oven)
    plr.inventory[index] = Item(warmed)
    plr.sendMessage("You briefly warm the drink in the oven.")
    if (warmed in GnomeCooking.FINISHED) plr.cooking.addExperience(GnomeCooking.COCKTAIL_EXP)
}

GnomeDish.entries.forEach { dish ->
    useItem(GnomeCooking.GIANNE_DOUGH).onItem(dish.tin) { shape(plr, dish) }

    GnomeCooking.FILLINGS.keys.forEach { ingredient ->
        useItem(ingredient).onItem(dish.halfBaked) { fill(plr, dish, ingredient) }
    }

    item1(dish.halfBaked) {
        inspect(plr, dish.inspectMessage, dish.plainMessage, dish.contentsMessage,
            GnomeCooking.describe(GnomeCooking.filling(plr, dish), GnomeCooking.FILLINGS))
    }

    Cooking.RANGES.forEach { oven ->
        useItem(dish.raw).onObject(oven) { halfBake(plr, gameObject, usedItemIndex, dish) }
        useItem(dish.halfBaked).onObject(oven) { bake(plr, gameObject, usedItemIndex, dish) }
    }
    Cooking.FIRES.forEach { fire ->
        useItem(dish.raw).onObject(fire) { plr.sendMessage(CookSource.RANGE_ONLY.refusal) }
        useItem(dish.halfBaked).onObject(fire) { plr.sendMessage(CookSource.RANGE_ONLY.refusal) }
    }
}

GnomeCooking.UNFINISHED.keys.forEach { id ->
    GnomeCooking.TOPPINGS.forEach { topping -> useItem(topping).onItem(id) { top(plr, id, topping) } }
}

GnomeCooking.MIXERS.keys.forEach { ingredient ->
    useItem(ingredient).onItem(GnomeCooking.COCKTAIL_SHAKER) { mix(plr, ingredient) }
}

useItem(GnomeCooking.COCKTAIL_SHAKER).onItem(GnomeCooking.COCKTAIL_GLASS) { pour(plr) }

item1(GnomeCooking.COCKTAIL_SHAKER) {
    inspect(plr, "You look into the cocktail shaker...", "The cocktail shaker is empty.",
        "The cocktail shaker contains:", GnomeCooking.describe(GnomeCooking.filling(plr, null), GnomeCooking.MIXERS))
}

GnomeCooking.WARMED.keys.forEach { drink ->
    Cooking.RANGES.forEach { oven -> useItem(drink).onObject(oven) { warm(plr, gameObject, usedItemIndex, drink) } }
    Cooking.FIRES.forEach { fire -> useItem(drink).onObject(fire) { plr.sendMessage(CookSource.RANGE_ONLY.refusal) } }
}
