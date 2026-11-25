package game.content.mageTrainingArena

import api.combat.death.DeathHookHandler
import api.predef.*
import api.predef.ext.*
import engine.combat.status.StatusEffectType
import game.content.mageTrainingArena.MageTrainingArena.arenaRoom
import game.player.Animations
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemSecondClickEvent
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.dialogue.DestroyItemDialogue
import io.luna.game.model.mob.interact.InteractionPolicy

val rooms = listOf(TelekineticTheatre, AlchemistsPlayground, EnchantingChamber, CreatureGraveyard)

// The doorway of light at the front of the entrance hall.
object1(MageTrainingArena.DOORWAY) {
    plr.move(if (plr.position.y < gameObject.position.y) MageTrainingArena.INSIDE_DOORWAY
             else MageTrainingArena.OUTSIDE_DOORWAY)
}

// The entrance hall portals and every room's exit teleport.
rooms.forEach { room -> object1(room.portal) { MageTrainingArena.enter(plr, room) } }
object1(MageTrainingArena.EXIT_TELEPORT) { if (plr.arenaRoom != null) MageTrainingArena.leave(plr, true) }

// The guardians.
npc1(MageTrainingArena.ENTRANCE_GUARDIAN) { ArenaDialogues.entranceGuardian(plr) }
npc1(MageTrainingArena.REWARDS_GUARDIAN) { ArenaDialogues.rewardsGuardian(plr) }
npc2(MageTrainingArena.REWARDS_GUARDIAN) {
    if (plr.arenaRoom == null) {
        if (MageTrainingArena.run { plr.mtaStarted }) RewardsShop.open(plr) else ArenaDialogues.rewardsGuardian(plr)
    }
}
listOf(MageTrainingArena.TELEKINETIC_GUARDIAN, MageTrainingArena.ALCHEMY_GUARDIAN,
       MageTrainingArena.ENCHANTMENT_GUARDIAN, MageTrainingArena.GRAVEYARD_GUARDIAN).forEach { id ->
    npc1(id) { ArenaDialogues.roomGuardian(plr, id) }
}
// The freed guardian stands on the exit square, inside the maze's walls, so it is spoken to from outside them.
npc1(MageTrainingArena.MAZE_GUARDIAN, InteractionPolicy.STANDARD_LINE_OF_SIGHT_BIF) {
    TelekineticTheatre.talkToGuardian(plr, targetNpc)
}

// The rewards shop's Value and Buy options.
on(WidgetItemFirstClickEvent::class) { if (widgetId == RewardsShop.ITEMS) RewardsShop.value(plr, index) }
on(WidgetItemSecondClickEvent::class) { if (widgetId == RewardsShop.ITEMS) RewardsShop.buy(plr, index) }

// The progress hat and the Arena book.
MageTrainingArena.HATS.forEach { item1(it) { ArenaDialogues.progressHat(plr) } }
item1(MageTrainingArena.ARENA_BOOK) { ArenaBook.read(plr) }

// Destroying a progress hat loses all its Pizazz Points.
on(ButtonClickEvent::class, EventPriority.NORMAL) {
    if (id != 14175) return@on
    val dialogue = plr.overlays[DestroyItemDialogue::class] ?: return@on
    if (dialogue.itemId in MageTrainingArena.HATS && plr.inventory.contains(dialogue.itemId)) {
        MageTrainingArena.wipePoints(plr)
    }
}

// The Alchemists' Playground.
AlchemistsPlayground.CUPBOARDS.forEach { id ->
    object1(id) { AlchemistsPlayground.search(plr, id) }
    object1(id + 1) { AlchemistsPlayground.search(plr, id) }
}
object1(10734) { AlchemistsPlayground.deposit(plr) }

// The Enchanting Chamber.
EnchantingChamber.Shape.values().forEach { shape -> object1(shape.pile) { EnchantingChamber.takeShape(plr, shape.pile) } }
object1(10803) { EnchantingChamber.deposit(plr) }

// The Creature Graveyard.
CreatureGraveyard.PILES.forEach { pile ->
    object1(pile) { if (plr.arenaRoom === CreatureGraveyard) CreatureGraveyard.grab(plr, pile, gameObject.position) }
}
object1(10735) { CreatureGraveyard.deposit(plr) }

// Dying in a room is safe: the player keeps everything but what belongs to the room, and wakes in the entrance hall.
DeathHookHandler.addPlayerHook(ArenaController::class.java) {
    preDeath { victim.animation(Animations.DEATH, AnimationPriority.IMMUTABLE) }
    death { }
    postDeath {
        if (victim.arenaRoom === CreatureGraveyard) CreatureGraveyard.died(victim)
        MageTrainingArena.leave(victim, false)
        victim.status.remove(StatusEffectType.POISONED)
        victim.combat.prayers.deactivateAll()
        victim.skills.resetAll()
    }
}

// Nobody comes back into a room from a save, even one made before a crash.
on(LoginEvent::class) {
    val room = rooms.firstOrNull { it.inside(plr.position) } ?: return@on
    MageTrainingArena.removeRoomItems(plr, room)
    plr.move(MageTrainingArena.ENTRANCE)
}

on(ServerLaunchEvent::class) {
    world.schedule(1) {
        AlchemistsPlayground.tick()
        EnchantingChamber.tick()
    }
}
