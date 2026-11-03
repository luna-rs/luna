package game.item.teleport

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.mob.Player

/**
 * The Magic whistle item id.
 */
val MAGIC_WHISTLE = ContextualTravelItems.WHISTLE

/**
 * The square under the watchtower north-west of Brimhaven where the whistle leads into the Fisher Realm. Ground
 * floor only.
 */
val MAGIC_WHISTLE_KARAMJA_SPOT = ContextualTravelItems.WHISTLE_MAINLAND

/**
 * The Fisher Realm, from which the whistle leads back to Karamja. Planes 0 to 2, so the Grail castle's upper floors
 * count.
 */
val MAGIC_WHISTLE_FISHER_REALM = ContextualTravelItems.WHISTLE_REALM

/**
 * Where the whistle puts the player in the Fisher Realm. One tile west of 2678,4715, which an oak covers in this map.
 */
val MAGIC_WHISTLE_REALM_LANDING = ContextualTravelItems.REALM_LANDING

/**
 * Where the whistle puts the player back on Karamja.
 */
val MAGIC_WHISTLE_KARAMJA_LANDING = ContextualTravelItems.MAINLAND_LANDING

/**
 * Blows the Magic whistle. On the Karamja spot or in the Fisher Realm it moves the player across at once; anywhere
 * else it does nothing. It is not a magic teleport, so there is no animation, Wilderness or Tele Block check, and the
 * whistle is never used up.
 *
 * @param plr The player blowing the whistle.
 */
fun blowMagicWhistle(plr: Player) {
    val position = plr.position
    when {
        position.z == 0 && MAGIC_WHISTLE_KARAMJA_SPOT.contains(position) ->
            // TODO Holy Grail: until Sir Percival has been given a whistle, go to the bleak Fisher Realm at 2806,4715.
            plr.move(MAGIC_WHISTLE_REALM_LANDING)
        position.z <= 2 && MAGIC_WHISTLE_FISHER_REALM.contains(position) -> plr.move(MAGIC_WHISTLE_KARAMJA_LANDING)
        else -> plr.newDialogue().text("The whistle makes no noise.", "It will not work in this location.").open()
    }
}

item1(MAGIC_WHISTLE) { blowMagicWhistle(plr) }
