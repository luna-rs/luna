package world.player.skill.runecrafting.craftRune

import api.predef.*
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Graphic
import io.luna.game.model.mob.Player

/**
 * The rune essence identifier.
 */
val runeEssence = 1436

/**
 * The pure essence identifier.
 */
val pureEssence = 7936

/**
 * The Runecrafting animation.
 */
val craftAnimation = Animation(791)

/**
 * The Runecrafting graphic.
 */
val craftGraphic = Graphic(186, 100)

/**
 * Attempts to craft [rune] for [plr].
 */
fun craft(plr: Player, rune: Rune) {
    if (plr.runecrafting.level < rune.level) {
        plr.sendMessage("You need a Runecrafting level of ${rune.level} to craft these runes.")
        return
    }
    val inv = plr.inventory

    // Calculate the essence type needed, and the amount of it we have.
    var essenceId = if (rune.level > 20) pureEssence else runeEssence
    var essenceAmt = inv.computeAmountForId(essenceId)

    // We required rune essence, and we have 0 of it. Look for pure essence instead.
    if (essenceAmt == 0 && essenceId == runeEssence) {
        essenceId = pureEssence
        essenceAmt = inv.computeAmountForId(pureEssence)
    }

    // If amount is still have 0, we don't have the required essence.
    if (essenceAmt == 0) {
        plr.sendMessage("You need the proper essence to craft these runes.")
        return
    }

    // Now we can craft runes!
    val craftAmt = essenceAmt * (plr.runecrafting.level / rune.multiplier)

    inv.remove(Item(essenceId, essenceAmt))
    inv.add(Item(rune.id, craftAmt))

    plr.sendMessage("You bind the temple's power into ${itemDef(rune.id).name}s.")

    plr.animation(craftAnimation)
    plr.graphic(craftGraphic)

    plr.runecrafting.addExperience(rune.exp * essenceAmt)
}

/**
 * Intercept event and craft runes if object clicked was a Runecrafting altar.
 */
on(ObjectFirstClickEvent::class)
    .filter { objectDef(id).hasAction(0, "Craft-rune") }
    .then {
        val altar = Rune.ALTAR_TO_RUNE[id]
        if (altar != null) {
            craft(plr, altar)
        }
    }