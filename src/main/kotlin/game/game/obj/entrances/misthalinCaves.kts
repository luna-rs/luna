package game.obj.entrances

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.dialogue.Expression

/**
 * A rope.
 */
val ROPE = 954

/**
 * Reaching for the winch.
 */
val REACH = Animation(832)

/**
 * Whether the player has tied a rope to the winch in the level 2 dig.
 */
var Player.digSiteRope by Attr.boolean().persist("dig_site_rope")

/**
 * Whether the player has tied a rope to the winch in the private dig.
 */
var Player.digSitePrivateRope by Attr.boolean().persist("dig_site_private_rope")

/**
 * A winch down a shaft at the Dig Site. The two shafts cross over.
 *
 * @param id The winch.
 * @param cavern Where it leads.
 * @param arrival The message once the player is down.
 */
enum class Winch(val id: Int, val cavern: Position, val arrival: String) {
    LEVEL_2_DIG(id = 2350, cavern = Position(3370, 9764), arrival = "You find yourself in a cavern..."),
    PRIVATE_DIG(id = 2351, cavern = Position(3353, 9754), arrival = "You find yourself in a cavern.")
}

/**
 * Returns `true` if [plr] has tied a rope to [winch].
 */
fun roped(plr: Player, winch: Winch) =
    if (winch == Winch.LEVEL_2_DIG) plr.digSiteRope else plr.digSitePrivateRope

/**
 * Remembers that [plr] has tied a rope to [winch].
 */
fun tieRope(plr: Player, winch: Winch) {
    if (winch == Winch.LEVEL_2_DIG) plr.digSiteRope = true else plr.digSitePrivateRope = true
}

/**
 * [plr] climbs down the rope tied to [winch].
 */
fun climbDown(plr: Player, winch: Winch) {
    plr.animation(REACH)
    if (plr.agility.level < 10) {
        plr.sendMessage("You try to climb down the rope...")
        plr.sendMessage("You need an Agility level of 10 to squeeze down the shaft.")
        return
    }
    plr.sendMessage("You try to climb down the rope.")
    plr.sendMessage("You lower yourself into the shaft.")
    plr.agility.addExperience(2.0)
    Entrances.travel(plr, winch.cavern, delay = 2, arrival = winch.arrival)
}

// The winches down the shafts at the Dig Site, which need a rope tied to them once.
// TODO Once The Dig Site is added, the winches need the mine shaft permit (the workman stops players without it, or
//  "There is a sign on the winch." "Private area - invitation only."), and the shafts lead to the blocked copies of
//  the caverns until the blockage is blown up.
for (winch in Winch.values()) {
    object1(winch.id) {
        if (roped(plr, winch)) {
            climbDown(plr, winch)
            return@object1
        }
        plr.sendMessage("You operate the winch.")
        val descend = {
            plr.sendMessage("The bucket descends, but does not reach the bottom.")
            plr.animation(REACH)
            plr.newDialogue()
                .player(Expression.QUIZZICAL, "Hey, I think I could fit down here. I need something to",
                        "help me get all the way down.")
                .open()
        }
        // The private dig's bucket takes a moment longer.
        if (winch == Winch.PRIVATE_DIG) world.scheduleOnce(3) { descend() } else descend()
    }
    useItem(ROPE).onObject(winch.id) {
        if (roped(plr, winch)) {
            climbDown(plr, winch)
        } else if (plr.inventory.remove(ROPE)) {
            plr.sendMessage("You tie the rope to the bucket...")
            tieRope(plr, winch)
            plr.animation(REACH)
        }
    }
}

// Edgeville Dungeon's odd looking walls swing open like a double door (see double_doors.json) and push the player
// through to the other side.
for (wall in listOf(3209, 3211)) {
    object1(wall) {
        plr.move(Position(if (plr.position.x >= 3094) 3093 else 3094, gameObject.position.y))
    }
}

/**
 * A raw chicken, offered at the Chicken Shrine in Zanaris.
 */
val RAW_CHICKEN = 2138

/**
 * The tunnel down to the baby dragons' nest in the Evil Chicken's lair, and the same tunnel with a rope tied to it.
 */
val NEST_TUNNEL = 12253
val NEST_TUNNEL_ROPED = 12254

// The Chicken Shrine in Zanaris, which takes players who offer it a raw chicken to the Evil Chicken's lair (Recipe for
// Disaster).
// TODO Once Recipe for Disaster is added, the shrine only takes the chicken once the Wise Old Man has told the player
//  about the Evil Chicken.
useItem(RAW_CHICKEN).onObject(12093) {
    if (plr.inventory.remove(usedItemIndex, Item(RAW_CHICKEN))) {
        plr.move(EntranceLanding.near(2461, 4357).from(plr.position))
    }
}

// The tunnel down to the nest, which needs a rope tied to it for a while, and the rope back up.
useItem(ROPE).onObject(NEST_TUNNEL) {
    if (plr.inventory.remove(usedItemIndex, Item(ROPE))) {
        Entrances.replace(gameObject, NEST_TUNNEL_ROPED, 200)
    }
}
object1(NEST_TUNNEL_ROPED) {
    Entrances.travel(plr, EntranceLanding.near(2441, 4382).from(plr.position), delay = 1, animation = 827)
}
object1(12255) {
    Entrances.travel(plr, EntranceLanding.near(2457, 4380).from(plr.position), delay = 1, animation = 828)
}

// The portal out of the Evil Chicken's lair, back to the shrine.
object1(12260) { plr.move(EntranceLanding.near(2453, 4476).from(plr.position)) }

// Saved attributes are only kept if they're read during the session (luna-rs/luna#610).
on(LoginEvent::class) {
    plr.digSiteRope
    plr.digSitePrivateRope
}
