package world.player.skill.crafting.armorCrafting

import api.predef.*
import io.luna.game.model.mob.Player

/**
 * The steel studs identifier.
 */
val steelStuds = CraftStuddedAction.STUDS

/**
 * The leather body identifier.
 */
val leatherBody = HideArmor.LEATHER_BODY.id

/**
 * The leather chaps identifier.
 */
val leatherChaps = HideArmor.LEATHER_CHAPS.id

/**
 * Craft studded [armor] for [plr].
 */
fun makeStudded(plr: Player, armor: HideArmor) {
    when (armor) {
        HideArmor.STUDDED_BODY ->
            plr.submitAction(CraftStuddedAction(plr, armor, leatherBody))
        HideArmor.STUDDED_CHAPS ->
            plr.submitAction(CraftStuddedAction(plr, armor, leatherChaps))
        else -> {
        }
    }
}

// Make studded body.
useItem(steelStuds)
    .onItem(leatherBody) {
        makeStudded(plr, HideArmor.STUDDED_BODY)
    }

// Make studded chaps.
useItem(steelStuds)
    .onItem(leatherChaps) {
        makeStudded(plr, HideArmor.STUDDED_CHAPS)
    }