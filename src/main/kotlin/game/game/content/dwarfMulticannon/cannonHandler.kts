package game.content.dwarfMulticannon

import api.predef.*
import game.content.dwarfMulticannon.DwarfMulticannon.CANNONBALL
import game.content.dwarfMulticannon.DwarfMulticannon.MAX_AMMO
import game.content.dwarfMulticannon.DwarfMulticannon.cannonPosition
import game.content.dwarfMulticannon.DwarfMulticannon.cannonStage
import game.content.dwarfMulticannon.SetUpCannonAction.Companion.PLACE_ANIMATION
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import kotlin.math.min

/**
 * Returns the cannon [obj] belongs to, if it is [plr]'s.
 */
fun ownCannon(plr: Player, obj: GameObject): Cannon? = Cannon.ALL[plr.username]?.takeIf { it.obj == obj }

/**
 * Picks up [plr]'s cannon, if [obj] is theirs and they have the space.
 */
fun pickUpCannon(plr: Player, obj: GameObject, notYours: String) {
    val cannon = ownCannon(plr, obj)
    when {
        cannon == null -> plr.sendMessage(notYours)
        plr.inventory.computeRemainingSize() < cannon.stage.ordinal + 1 ->
            plr.sendMessage("You need ${cannon.stage.freeSpace} to pick that up.")

        else -> cannon.pickUp(plr)
    }
}

/* Set up the base. */
item1(CannonStage.BASE.partId) {
    if (plr.cannonPosition != null) {
        plr.sendMessage("You cannot construct more than one Cannon at a time.")
        plr.sendMessage("If you have lost your Cannon, go and see the Dwarf Cannon engineer.")
        return@item1
    }
    val refusal = DwarfMulticannon.checkSetUp(plr.position)
    val step = if (refusal == null) DwarfMulticannon.findSetUpStep(plr.position) else null
    when {
        refusal != null -> plr.sendMessage(refusal)
        step == null -> plr.sendMessage("There isn't enough space to set up here.")
        else -> plr.submitAction(SetUpCannonAction(plr, plr.position, step))
    }
}

/* Add the stand, barrels and furnace, in that order. */
for (stage in CannonStage.entries) {
    val next = stage.next ?: break
    for (part in CannonStage.PARTS) {
        useItem(part).onObject(stage.objectId) {
            val cannon = ownCannon(plr, gameObject)
            when {
                cannon == null -> plr.sendMessage("That isn't your cannon!")
                part != next.partId -> plr.sendMessage("This cannon needs its ${next.partName}.")
                plr.inventory.remove(Item(part)) -> {
                    plr.animation(PLACE_ANIMATION)
                    cannon.build(next)
                    plr.cannonStage = next
                    plr.sendMessage("You add the ${next.partName}.")
                }
            }
        }
    }
}

/* Pick up an unfinished cannon. */
for (stage in listOf(CannonStage.BASE, CannonStage.STAND, CannonStage.BARRELS)) {
    object1(stage.objectId) {
        pickUpCannon(plr, gameObject, "That isn't your cannon!")
    }
}

/* Pick up a finished cannon. */
object2(CannonStage.FURNACE.objectId) {
    pickUpCannon(plr, gameObject, "This is not your cannon.")
}

/* Start firing. */
object1(CannonStage.FURNACE.objectId) {
    val cannon = ownCannon(plr, gameObject)
    when {
        cannon == null -> plr.sendMessage("That isn't your cannon!")
        cannon.firing -> plr.sendMessage("Your cannon is already firing.")
        cannon.ammo < 1 -> plr.sendMessage("Your cannon is out of ammo!")
        else -> cannon.startFiring()
    }
}

/* Load cannonballs. */
useItem(CANNONBALL).onObject(CannonStage.FURNACE.objectId) {
    val cannon = ownCannon(plr, gameObject)
    when {
        cannon == null -> plr.sendMessage("This is not your cannon.")
        cannon.ammo >= MAX_AMMO -> plr.sendMessage("The cannon is already full of ammo.")
        else -> {
            val count = min(MAX_AMMO - cannon.ammo, plr.inventory.computeAmountForId(CANNONBALL))
            plr.sendMessage("You load the cannon with $count cannonballs.")
            cannon.ammo += count
            plr.inventory.remove(Item(CANNONBALL, count))
        }
    }
}

/* The cannon stops firing when its owner logs out. */
on(LogoutEvent::class) {
    Cannon.ALL[plr.username]?.stopFiring()
}
