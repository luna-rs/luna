package game.skill.agility.roguesDen

import api.predef.*
import api.predef.ext.*
import engine.spawn.PersistentGroundItem
import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityApproach
import game.skill.agility.AgilityStep
import game.skill.agility.AgilityWalk
import game.skill.agility.roguesDen.RoguesDen.BRIAN
import game.skill.agility.roguesDen.RoguesDen.Exit
import game.skill.agility.roguesDen.RoguesDen.FLASH_POWDER
import game.skill.agility.roguesDen.RoguesDen.JEWEL
import game.skill.agility.roguesDen.RoguesDen.RIGHT_TILE
import game.skill.agility.roguesDen.RoguesDen.denAccess
import game.skill.agility.roguesDen.RoguesDen.denRun
import game.skill.agility.roguesDen.RoguesDenMap.Trap
import io.luna.game.action.impl.LockedAction
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.PlayerTickEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemSecondClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Entity
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import java.util.function.BiFunction

/**
 * The flash powder piles and mosaic tiles, with the ticks they take to come back.
 */
val GROUND_SPAWNS = listOf(
    Triple(FLASH_POWDER, 5, Position(2969, 4999, 1)), Triple(FLASH_POWDER, 5, Position(2970, 5000, 1)),
    Triple(FLASH_POWDER, 5, Position(3009, 5063, 1)), Triple(FLASH_POWDER, 5, Position(3009, 5070, 1)),
    Triple(RIGHT_TILE, 1, Position(3018, 5080, 1)), Triple(5569, 1, Position(3011, 5082, 1)),
    Triple(5570, 1, Position(3013, 5090, 1)), Triple(5571, 1, Position(3018, 5086, 1)))
val SPAWN_TICKS = 10

/**
 * The door that never opens, north of the safes.
 */
val SEALED_DOOR = Position(3021, 5055, 1)

/**
 * ROGUE_BLOCKING_GRILL, the bars lifting on a grill.
 */
val GRILL_OPEN = 2216

/**
 * The Rogue kit's four tools: spade, chisel, rope and tinderbox.
 */
val KIT_TOOLS = listOf("Spade" to 952, "Chisel" to 1755, "Rope" to 954, "Tinderbox" to 590)

/**
 * The tiles each side of a wall object: its own tile and the one beyond the wall.
 */
fun GameObject.ownTile(): Position = position
fun GameObject.beyond(): Position = position.translate(1, direction.toNormalDirection())

/**
 * Walks the player [tiles] a tile a tick, in [walk]'s animations, then runs [done].
 */
fun walkLegs(plr: Player, tiles: List<Position>, walk: Int = AgilityWalk.NORMAL, begin: (Int) -> Unit = {},
             done: () -> Unit = {}) {
    val steps = tiles.mapIndexed { i, tile -> AgilityStep(tile, 1, walkingAnimation = walk, begin = { begin(i) }) }
    plr.submitAction(AgilityAction(plr, steps, null).also { it.onSuccess = done })
}

/**
 * Takes the player through wall object [obj] from [from] to [to], refusing from any other tile.
 */
fun throughWall(plr: Player, obj: GameObject, from: Position, to: Position, open: () -> Boolean = { true },
                done: () -> Unit = {}) {
    if (plr.position != from) {
        plr.sendMessage("The door won't open!")
        return
    }
    if (!open()) return
    walkLegs(plr, listOf(to), done = done)
}

/**
 * Brian's lines and the jewel.
 */
fun brianLevels(plr: Player): Boolean {
    val skill = when {
        plr.thieving.staticLevel < 50 -> "Thieving"
        plr.agility.staticLevel < 50 -> "Agility"
        else -> return true
    }
    plr.newDialogue().npc(BRIAN, "Shame, I don't think I have anything for you. Train up",
                          "your $skill skill to at least 50 and I might be able to",
                          "help you out.").open()
    return false
}

fun givesJewel(plr: Player) {
    if (plr.inventory.computeAmountForId(JEWEL) == 0) plr.giveItem(Item(JEWEL))
    plr.denAccess = true
}

fun brianPlace(plr: Player, next: (Player) -> Unit) {
    plr.newDialogue().player("What is this place?")
        .npc(BRIAN, "Ah welcome to my humble home, well actually it belongs",
             "to mummsie but she's getting on a bit so I look after the",
             "place for her.")
        .npc(BRIAN, "So are you interested in a challenge?")
        .then { next(it) }
        .open()
}

fun brianFirst(plr: Player) {
    plr.newDialogue().npc(BRIAN, "Hi there, looking for a challenge are you?")
        .options("Yes actually, what've you got?", { brianChallenge(it) },
                 "What is this place?", { brianPlace(it) { p -> brianFirst(p) } },
                 "No thanks.", { it.newDialogue().player("No thanks.").open() })
        .open()
}

fun brianChallenge(plr: Player) {
    plr.newDialogue().player("Yes actually, what've you got?").then { p ->
        if (!brianLevels(p)) return@then
        p.newDialogue().npc(BRIAN, "Aha, I have the perfect thing for you! See if you can get",
                            "to the centre of my maze, the further you get the greater",
                            "the rewards. There's even some special prizes if you make",
                            "it right to the end.")
            .options("Ok that sounds good!", { brianAccept(it) },
                     "What is this place?", { brianPlace(it) { q -> brianFirst(q) } },
                     "Actually I think I'll pass thanks.", {
                         it.newDialogue().player("Actually I think I'll pass thanks.").open()
                     })
            .open()
    }.open()
}

fun brianAccept(plr: Player) {
    plr.newDialogue().player("Ok that sounds good!").then { p ->
        p.newDialogue().npc(BRIAN, "Great! Here's a jewel - it'll allow you to get out of the",
                            "maze at any time. However that's all you're allowed to",
                            "take in with you, no cheating!")
            .first { givesJewel(it) }
            .npc(BRIAN, "Oh one last thing, if you happen to see my harmonica I'd",
                 "really like to have it back.")
            .open()
    }.open()
}

fun brianAgain(plr: Player) {
    plr.newDialogue().npc(BRIAN, "Hi again, what can I do for you?")
        .options("I want to try the maze again!", { p ->
            p.newDialogue().player("I want to try the maze again!").then { q ->
                if (!brianLevels(q)) return@then
                if (q.inventory.computeAmountForId(JEWEL) > 0) {
                    q.newDialogue().npc(BRIAN, "Great, well you've already got a jewel so away you go!").open()
                } else {
                    q.newDialogue().npc(BRIAN, "Certainly, here's another jewel, good luck!")
                        .first { givesJewel(it) }.open()
                }
            }.open()
        }, "What is this place?", { brianPlace(it) { p -> brianAgain(p) } },
            "Nothing thanks.", { it.newDialogue().player("Nothing thanks.").open() })
        .open()
}

npc1(BRIAN) {
    if (plr.denAccess) brianAgain(plr) else brianFirst(plr)
}

/**
 * The doorway into the maze: the jewel and nothing else.
 */
object1(7256) {
    if (plr.denRun != null || plr.position != gameObject.ownTile()) {
        plr.sendMessage("The door won't open!")
        return@object1
    }
    when {
        plr.inventory.computeAmountForId(JEWEL) == 0 ->
            plr.newDialogue().npc(BRIAN, "And where do you think you're going? A little too eager",
                                  "I think. Come and talk to me before you go wandering",
                                  "around in there.").open()
        plr.inventory.any { it != null && it.id != JEWEL } || plr.equipment.size() > 0 ->
            plr.newDialogue().npc(BRIAN, "Tut tut tut, now you know you're not allowed to take",
                                  "anything except that jewel in with you.").open()
        else -> {
            RoguesDen.enter(plr)
            // The doorway is the grills' model, so its bars lift with theirs.
            gameObject.animate(GRILL_OPEN)
            walkLegs(plr, listOf(gameObject.beyond()))
        }
    }
}

/**
 * Grills open one way: from their own tile, into the tile beyond.
 */
object1(7255) {
    val obj = gameObject
    throughWall(plr, obj, obj.ownTile(), obj.beyond(), open = {
        obj.animate(GRILL_OPEN)
        true
    })
}

/**
 * The locked doors and gallery doors swing open into their twin without options and close behind the player.
 */
val LOCKED_DOOR = DoorType(7246, 7247, null, null, null, 3)
val GALLERY_DOOR = DoorType(7222, 7223, null, null, null, 3)

/**
 * Takes the player through a swinging door: from its own tile if [fromOwnTile], otherwise from the tile beyond, and
 * only if [allowed] lets them.
 */
fun throughDoor(plr: Player, door: GameObject, type: DoorType, fromOwnTile: Boolean, allowed: () -> Boolean = { true },
                done: () -> Unit = {}) {
    if (plr.actions.first(DoorPassageAction::class.java) != null) return
    plr.submitAction(object : DoorPassageAction(plr, door, type) {
        override fun checkAccess(entering: Boolean): Boolean {
            if (mob.denRun == null) return false
            if (entering != fromOwnTile) {
                mob.sendMessage("The door won't open!")
                return false
            }
            return allowed()
        }

        override fun onPassage(entering: Boolean) = done()
    })
}

/**
 * The contortion bars: 5 Agility, refused when there aren't 5 left, and one player at a time.
 */
val barsBusy = HashMap<Position, Long>()

fun barsStarts(obj: GameObject): List<Position> =
    if (obj.direction.id % 2 == 1) listOf(obj.position.translate(-1, 0), obj.position.translate(1, 0))
    else listOf(obj.position.translate(0, -1), obj.position.translate(0, 1))

fun crossBars(plr: Player, obj: GameObject) {
    val starts = barsStarts(obj)
    if (plr.denRun == null || plr.isLocked || plr.position !in starts) return
    if ((barsBusy[obj.position] ?: 0L) > world.currentTick) return
    if (plr.agility.level < 5) {
        plr.sendMessage("You don't have enough Agility skill left!")
        return
    }
    RoguesDen.spend(plr, Skill.AGILITY, 5)
    barsBusy[obj.position] = world.currentTick + 3
    val far = starts.first { it != plr.position }
    plr.submitAction(AgilityAction(plr, listOf(AgilityStep(far, 3, animation = 2240)), null))
}

object1(7251) {
    val obj = gameObject
    val start = barsStarts(obj).filter { it.isWithinDistance(plr.position, AgilityApproach.RADIUS) }
        .minByOrNull { it.computeLongestDistance(plr.position) } ?: return@object1
    if (plr.position == start) crossBars(plr, obj)
    else if (!plr.isLocked) plr.submitAction(AgilityApproach(plr, start) { crossBars(plr, obj) })
}

/**
 * The ledges: a tile at each end, the tiles in between, and the walk along them. Hanging ledges cost a point for
 * each hold moved between, the beam a point for each of its tiles.
 */
class Ledge(val ends: List<Position>, val tiles: List<Position>, val walk: Int, val beam: Boolean)

val LEDGES = listOf(
    Ledge(listOf(Position(2958, 5030, 1), Position(2958, 5035, 1)), (5031..5034).map { Position(2958, it, 1) },
          AgilityWalk.BALANCE.walk, true),
    Ledge(listOf(Position(2994, 5004, 1), Position(2988, 5004, 1)), (2993 downTo 2989).map { Position(it, 5004, 1) },
          853, false),
    Ledge(listOf(Position(2994, 5005, 1), Position(2988, 5005, 1)), (2993 downTo 2989).map { Position(it, 5005, 1) },
          853, false),
    Ledge(listOf(Position(2982, 5087, 1), Position(2991, 5087, 1)), (2983..2990).map { Position(it, 5087, 1) },
          853, false),
    Ledge(listOf(Position(2982, 5090, 1), Position(2991, 5090, 1)), (2983..2990).map { Position(it, 5090, 1) },
          853, false))

fun crossLedge(plr: Player, ledge: Ledge) {
    if (plr.denRun == null || plr.isLocked || plr.position !in ledge.ends) return
    val forward = plr.position == ledge.ends[0]
    val path = (if (forward) ledge.tiles else ledge.tiles.reversed()) + ledge.ends[if (forward) 1 else 0]
    val cost = if (ledge.beam) ledge.tiles.size else ledge.tiles.size - 1
    if (plr.agility.level < cost) {
        RoguesDen.fail(plr)
        return
    }
    walkLegs(plr, path, ledge.walk, begin = { leg ->
        val pays = if (ledge.beam) leg < ledge.tiles.size else leg in 1 until ledge.tiles.size
        if (pays) RoguesDen.spend(plr, Skill.AGILITY, 1)
    })
}

for (id in listOf(7239, 7240)) {
    object1(id) {
        val ledge = LEDGES.firstOrNull { gameObject.position in it.tiles } ?: return@object1
        val start = ledge.ends.filter { it.isWithinDistance(plr.position, AgilityApproach.RADIUS) }
            .minByOrNull { it.computeLongestDistance(plr.position) } ?: return@object1
        if (plr.position == start) crossLedge(plr, ledge)
        else if (!plr.isLocked) plr.submitAction(AgilityApproach(plr, start) { crossLedge(plr, ledge) })
    }
}

/**
 * Searching a trap: 1 Thieving, then it's safe for this player until they move on.
 */
fun search(plr: Player, obj: GameObject) {
    val run = plr.denRun ?: return
    val trap = RoguesDenMap.trapsAt[obj.position] ?: return
    if (run.leaving || obj.position in run.disarmed) return
    plr.submitAction(object : LockedAction(plr) {
        var ticks = 0

        override fun onLock() {
            mob.walking.clear()
            mob.face(obj.position)
        }

        override fun run(): Boolean {
            ticks++
            if (ticks == 1) {
                if (!RoguesDen.spend(mob, Skill.THIEVING, 1)) {
                    run.searching = null
                    RoguesDen.fail(mob, if (trap.type.wall) "You triggered a wall trap!" else "You triggered a floor trap!",
                                   trap.type.playerSeq, trap)
                    return true
                }
                mob.animation(Animation(when (trap.type) {
                    Trap.WALL_SPIKES -> 2246
                    Trap.WALL_GAS, Trap.WALL_CRUSHER -> 2245
                    else -> 2244
                }))
            }
            if (ticks < 3) return false
            run.disarmed[obj.position] = trap.triggers
            run.searching = null
            mob.sendMessage("You temporarily disarm the trap!")
            return true
        }
    })
}

for (trap in Trap.values()) {
    object5(trap.id) { search(plr, gameObject) }
}

/**
 * The locked doors: base Thieving 80 to pick, 10 Thieving points to spend.
 */
object1(7246) {
    if (plr.denRun == null) return@object1
    throughDoor(plr, gameObject, LOCKED_DOOR, true, allowed = {
        when {
            plr.thieving.staticLevel < 80 -> {
                plr.sendMessage("You need a base Thieving level of 80 to unlock this door.")
                false
            }
            !RoguesDen.spend(plr, Skill.THIEVING, 10) -> {
                RoguesDen.fail(plr)
                false
            }
            else -> true
        }
    })
}

/**
 * The puzzle doors open from their own side once solved.
 */
object1(7232) {
    if (plr.denRun == null) return@object1
    if (plr.position != gameObject.ownTile()) plr.sendMessage("The door won't open!")
    else RoguesDenPuzzles.openRotation(plr)
}

object1(7234) {
    if (plr.denRun == null) return@object1
    if (plr.position != gameObject.ownTile()) plr.sendMessage("The door won't open!")
    else RoguesDenPuzzles.openMosaic(plr)
}

fun throughPuzzleDoor(plr: Player, id: Int) {
    plr.overlays.closeWindows()
    val door = world.objects.findAll(plr.position).filter { it.id == id }.findFirst().orElse(null) ?: return
    walkLegs(plr, listOf(door.beyond()))
}

on(WidgetItemFirstClickEvent::class) {
    if (widgetId == RoguesDenPuzzles.ROTATION_SLOTS && RoguesDenPuzzles.rotate(plr, index, true)) {
        throughPuzzleDoor(plr, 7232)
    }
}

on(WidgetItemSecondClickEvent::class) {
    if (widgetId == RoguesDenPuzzles.ROTATION_SLOTS && RoguesDenPuzzles.rotate(plr, index, false)) {
        throughPuzzleDoor(plr, 7232)
    }
}

for ((button, tile) in RoguesDenPuzzles.MOSAIC_BUTTONS) {
    button(button) {
        if (plr.denRun == null || plr.inventory.computeAmountForId(tile) == 0) return@button
        if (tile == RIGHT_TILE) {
            plr.sendMessage("A perfect match!")
            throughPuzzleDoor(plr, 7234)
        } else {
            plr.overlays.closeWindows()
            RoguesDen.fail(plr, "You triggered a floor trap!", Trap.FLOOR_SPIKES.playerSeq)
        }
    }
}

/**
 * The viewing gallery: in through a gallery door from the maze, which gives up the run's progress, up the passage,
 * and out again only with the jewel.
 */
object1(7222) {
    val obj = gameObject
    val run = plr.denRun ?: return@object1
    if (obj.position == SEALED_DOOR) {
        plr.sendMessage("The door won't open!")
        return@object1
    }
    throughDoor(plr, obj, GALLERY_DOOR, false, done = { run.forfeited = true })
}

/**
 * A floor tile beside the gallery ladder next to passage [passage].
 */
fun galleryLanding(passage: GameObject): Position? {
    val up = passage.position.translate(0, 0, 1)
    val ladder = (listOf(up) + Direction.NESW.map { up.translate(1, it) }).firstOrNull { tile ->
        world.objects.findAll(tile).anyMatch { it.id == 7221 }
    } ?: return null
    return Direction.NESW.map { ladder.translate(1, it) }.firstOrNull { tile ->
        Direction.NESW.any { world.collisionManager.traversable(tile, EntityType.PLAYER, it) }
    }
}

object1(7220) {
    if (plr.denRun == null || plr.position != gameObject.beyond()) return@object1
    val landing = galleryLanding(gameObject) ?: return@object1
    plr.submitAction(AgilityAction(plr, listOf(AgilityStep(landing, 1, jump = true)), null))
}

object1(7221) {
    if (plr.denRun == null) return@object1
    val passage = world.objects.findAll(gameObject.position.translate(0, 0, -1)).filter { it.id == 7220 }
        .findFirst().orElse(null)
        ?: Direction.NESW.asSequence().mapNotNull { dir ->
            world.objects.findAll(gameObject.position.translate(1, dir).translate(0, 0, -1))
                .filter { it.id == 7220 }.findFirst().orElse(null)
        }.firstOrNull() ?: return@object1
    plr.move(passage.beyond())
}

/**
 * The passages through the maze's walls, in pairs: in at one, out beyond the other.
 */
object1(7219) {
    val obj = gameObject
    if (plr.denRun == null || plr.position != obj.beyond()) return@object1
    val toward = obj.direction.toNormalDirection().opposite()
    val partner = world.objects.findAll(obj.position.translate(2, toward)).filter { it.id == 7219 }
        .findFirst().orElse(null) ?: return@object1
    walkLegs(plr, listOf(obj.position, obj.position.translate(1, toward), partner.position, partner.beyond()))
}

/**
 * The centre safes: a roll on the player's Thieving, then out of the maze with the prize or without.
 */
object1(7237) {
    val run = plr.denRun ?: return@object1
    if (run.leaving) return@object1
    val safe = gameObject
    run.leaving = true
    plr.submitAction(object : LockedAction(plr) {
        var ticks = 0

        override fun onLock() {
            mob.walking.clear()
            mob.face(safe.position)
        }

        override fun run(): Boolean {
            when (++ticks) {
                1 -> {
                    mob.sendMessage("You start cracking the safe.")
                    mob.animation(Animation(2247))
                }
                2 -> mob.animation(Animation(2248))
                3 -> {
                    // The roll beats the player's Thieving or they slip half the time; the other half costs a level.
                    var slipped = false
                    if (rand(0, 199) > mob.thieving.level) {
                        if (rand().nextBoolean()) slipped = true
                        else RoguesDen.spend(mob, Skill.THIEVING, 1)
                    }
                    if (slipped) {
                        mob.sendMessage("You slip and trigger a trap!")
                    } else {
                        mob.sendMessage("You get some loot.")
                        mob.inventory.add(Item(if (rand(0, 7) < 3) 5558 else RoguesDen.ROGUE_PIECES.random()))
                    }
                }
                4 -> {
                    RoguesDen.leave(mob, Exit.SAFE)
                    return true
                }
            }
            return false
        }
    })
}

/**
 * The Rogue kit: one tool for one kit.
 */
item1(5558) {
    plr.newDialogue().options(KIT_TOOLS[0].first, { make(it, KIT_TOOLS[0].second) },
                              KIT_TOOLS[1].first, { make(it, KIT_TOOLS[1].second) },
                              KIT_TOOLS[2].first, { make(it, KIT_TOOLS[2].second) },
                              KIT_TOOLS[3].first, { make(it, KIT_TOOLS[3].second) })
        .title("What do you want to make?")
        .open()
}

fun make(plr: Player, tool: Int) {
    if (plr.inventory.remove(Item(5558))) plr.inventory.add(Item(tool))
}

/**
 * Flash powder thrown at a guard dazes him for a few seconds.
 */
val THROW = BiFunction<Player, Entity, InteractionPolicy> { _, _ ->
    InteractionPolicy(InteractionType.LINE_OF_SIGHT, RoguesDenNpcs.GUARD_SIGHT)
}

for (guard in RoguesDenNpcs.GUARDS) {
    useItem(FLASH_POWDER).onNpc(guard, THROW) {
        if (plr.denRun == null || !plr.inventory.remove(Item(FLASH_POWDER))) return@onNpc
        // The cache's flash powder spotanim has no seq, and the 377 client fails on a seq-less spotanim on a mob.
        targetNpc.graphic(Graphic(245, 100, 0))
        RoguesDenNpcs.daze(targetNpc)
    }
}

on(ServerLaunchEvent::class) {
    world.scheduleOnce(1) {
        RoguesDenMap.load()
        GROUND_SPAWNS.forEach { (id, amount, at) -> world.addItem(PersistentGroundItem(id, amount, at, SPAWN_TICKS)) }
    }
    world.schedule(1) { RoguesDenNpcs.tick() }
}

on(PlayerTickEvent::class) { RoguesDen.tick(plr) }

on(LoginEvent::class) { RoguesDen.login(plr) }
