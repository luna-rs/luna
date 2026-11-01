package game.skill.agility.course

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.agility.*
import game.skill.agility.Agility.course
import game.skill.agility.AgilityCourse.WEREWOLF
import game.skill.agility.AgilityObstacle.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.EntityState
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

/**
 * The werewolves of the course: the Agility Boss by the stepping stones, the trainers along the course, the trainer
 * at the end of the zip line who takes the stick, and the guard by the trapdoor above.
 */
val AGILITY_BOSS = 1661
val AGILITY_TRAINER = 1663
val STICK_TRAINER = 1664
val TRAPDOOR_GUARD = 1665

/**
 * The stick the Agility Boss throws.
 */
val STICK = 4179

/**
 * The course's animations.
 */
val JUMP_STONES = 1604
val JUMP_HURDLE = 1603
val PIPE_SQUEEZE = 749
val PIPE_READY = 747
val ZIP_BITE = 1601
val ZIP_SLIDE = 1602
val ZIP_FALL = 766
val STICK_THROW = 1619

/**
 * The thrown stick's flight.
 */
val STICK_TRAVEL = 338

/**
 * The start bank and the five stepping stones in order. Each stone is jumped from the tile before it.
 */
val STONES = listOf(Position(3538, 9873), Position(3538, 9875), Position(3538, 9877), Position(3540, 9877),
    Position(3540, 9879), Position(3540, 9881))

/**
 * The stones are jumped to from two tiles away, across the water.
 */
val STONE_REACH = InteractionPolicy(InteractionType.DISTANCE, 2)

/**
 * The tiles past the pipes that the stick lands on.
 */
val STICK_TILES = (3537..3545).map { Position(it, 9911) }

/**
 * The zip line's start, inside its frame, and its end beside the trainer who takes the stick.
 */
val ZIP_START = Position(3528, 9910)
val ZIP_END = Position(3528, 9871)

/**
 * A point on the zip line where a slide can fail: [ticks] into the slide, over [air], landing on [ground] for
 * [minHit] to [maxHit] damage and [xp].
 */
class ZipFall(val ticks: Int, val air: Position, val ground: Position, val minHit: Int, val maxHit: Int,
              val xp: Double)

/**
 * The three points over the spike pit, earliest first. The earlier the fall, the higher the drop and the less of the
 * slide's experience.
 */
val ZIP_FALLS = listOf(
    ZipFall(3, Position(3528, 9895), Position(3526, 9895), 25, 45, 140.0),
    ZipFall(4, Position(3528, 9890), Position(3530, 9890), 15, 30, 160.0),
    ZipFall(5, Position(3528, 9886), Position(3530, 9886), 5, 15, 180.0))

val BACKWARDS = "Don't look backwards - keep going forwards!"
val GRRR = "Grrr - you don't belong in here, human!"

/**
 * What the trainers shout at a werewolf taking an obstacle.
 */
val TRAINER_LINES = listOf(
    "Remember - a slow wolf is a hungry wolf!!",
    "Get on with it - you need your whiskers plucking!!!!",
    "Claws first - think later.",
    "Imagine the smell of blood in your nostrils!!!",
    "I never really wanted to be an agility trainer...",
    "It'll be worth it when you hunt!!",
    "Let's see those powerful backlegs at work!!",
    "Let the bloodlust take you!!",
    "You're the slowest wolf I've ever had the misfortune to witness!!",
    "When you're done there's a human with your name on it!!")

/**
 * What the zip line's trainer shouts as a werewolf bites onto it.
 */
val ZIP_LINES = listOf(
    "Give my regards to the ground...",
    "Don't let the spikes or the blood put you off...",
    "Now for a true test of teeth...")

/**
 * What the trainer at the end of the zip line keeps reminding players.
 */
val STICK_TRAINER_LINES = listOf(
    "Remember - no stick, no agility bonus!",
    "Bring me the stick when you've finished the course!",
    "Don't forget to give me the stick when you're done!")

/**
 * The tick each werewolf last shouted on, so one doesn't talk over himself.
 */
val lastShout = mutableMapOf<Npc, Long>()

/**
 * Whether the werewolves take this player for one of them. In 2006 only wearing a Ring of Charos (4202, or the
 * unlocked 6465) did that.
 */
fun Player.passesAsWerewolf(): Boolean {
    // TODO Once Creature of Fenkenstrain is added, only players wearing a Ring of Charos pass.
    return true
}

/**
 * Has [npc] shout [line], unless he shouted in the last five ticks.
 */
fun shout(npc: Npc?, line: String) {
    if (npc == null || world.currentTick - (lastShout[npc] ?: Long.MIN_VALUE / 2) < 5) return
    lastShout[npc] = world.currentTick
    npc.speak(line)
}

/**
 * Has the trainer nearest this player shout at them as they take an obstacle: [line] at a werewolf, or a line about
 * humans at anyone else.
 */
fun Player.trainerShout(line: String = TRAINER_LINES.random(), human: String = "You shouldn't even be here, human!") {
    val trainer = world.locator.findNpcs(this, 10, true) { it.id == AGILITY_TRAINER }.firstOrNull()
    shout(trainer, if (passesAsWerewolf()) line else human)
}

/**
 * Sends [BACKWARDS] and turns the click away.
 */
fun Player.backwards(): Boolean {
    sendMessage(BACKWARDS)
    return false
}

/**
 * The Agility Boss throws a stick past the pipes as a lap starts, unless the player still holds one.
 */
fun Player.throwStick() {
    val boss = world.locator.findNpcs(this, 15, true) { it.id == AGILITY_BOSS }.firstOrNull()
    if (inventory.contains(STICK)) {
        boss?.speak("Why didn't you hand the stick over?!!?")
        return
    }
    val landing = STICK_TILES.random()
    if (boss != null) {
        boss.speak("FETCH!!!!!")
        boss.animation(Animation(STICK_THROW))
        // The stick leaves his paw about a tick into the throw and lands two ticks later.
        LocalProjectile.followPath(ctx).setSourcePosition(boss.position).setTargetPosition(landing)
            .setId(STICK_TRAVEL).setStartHeight(60).setEndHeight(0).setTiming(30, 90).build().display()
    }
    world.scheduleOnce(3) {
        if (state != EntityState.ACTIVE) return@scheduleOnce
        // Only the thrower ever sees the stick: it never turns public like a dropped item, and goes after 90 seconds.
        val stick = GroundItem(ctx, STICK, 1, landing, ChunkUpdatableView.localView(this))
        stick.setExpire(false)
        if (world.items.register(stick)) {
            world.scheduleOnce(150) { if (stick.state == EntityState.ACTIVE) world.items.unregister(stick) }
        }
    }
}

/**
 * Whether the deathslide drops this player: a random number from 0 to 160 is matched against their current Agility
 * plus Strength, less what they carry (never below nothing).
 */
fun Player.deathslideFails() = rand().nextInt(161) >= agility.level + strength.level - maxOf(weight, 0.0)

/**
 * The deathslide: the player bites on, slides down to the end, or falls off at [fall].
 */
fun Player.deathslide(fall: ZipFall?): AgilityAction {
    val steps = mutableListOf(
        AgilityStep(position, 1, jump = true),
        AgilityStep(position, 2, animation = ZIP_BITE, jump = true,
            message = "You bravely cling on to the death slide by your teeth ...",
            sounds = listOf(AgilitySound(Sound.BITE)),
            begin = { trainerShout(ZIP_LINES.random(), "Let's see how strong human teeth are...") }))
    val slide = { to: Position, ticks: Int ->
        AgilityStep(to, ticks, animation = ZIP_SLIDE, sounds = listOf(AgilitySound(Sound.DEATHSLIDE)),
            begin = { speak("WAAAAAAARRRGGGHHHH!!!!!!") })
    }
    if (fall == null) {
        // The slide's animation loops, so it is stopped as the player lands.
        steps += slide(ZIP_END, 8)
        steps += AgilityStep(ZIP_END, 1, jump = true, message = ".. and land safely on your feet.",
            begin = { animation(Animation(-1)) })
        return AgilityAction(this, steps, null)
    }
    steps += slide(fall.air, fall.ticks)
    steps += AgilityStep(fall.ground, 1, animation = ZIP_FALL, moveEnd = 15,
        message = ".. only to fall from a great height!")
    return AgilityAction(this, steps, null).also {
        it.onSuccess = {
            damage(rand(fall.minHit, fall.maxHit))
            agility.addExperience(fall.xp)
        }
    }
}

course(WEREWOLF) {
    obstacle(5138, WEREWOLF_STEPPING_STONE,
        interaction = { _, _ -> STONE_REACH },
        allow = { stone ->
            val here = STONES.indexOf(position)
            val target = STONES.indexOf(stone.position)
            when {
                here + 1 == target || here == -1 && target == 1 -> true
                here >= target || here == -1 && y > stone.y -> backwards()
                else -> {
                    sendMessage("I can't reach that!")
                    false
                }
            }
        },
        startTile = { stone -> STONES[STONES.indexOf(stone.position) - 1] }) { stone ->
        // Each stone is its own click. The hop is over a tick in, so the next stone can be clicked on landing.
        AgilityAction(this, listOf(AgilityStep(stone.position, 1, animation = JUMP_STONES, moveStart = 18,
            moveEnd = 30)), null, start = { if (stone.position == STONES[1]) throwStick() })
    }

    for (id in listOf(5133, 5134, 5135)) {
        obstacle(id, WEREWOLF_HURDLE,
            allow = { hurdle -> y < hurdle.y || backwards() },
            startTile = { hurdle -> Position(x.coerceIn(3537, 3543), hurdle.y - 1) }) { hurdle ->
            AgilityAction(this, listOf(AgilityStep(Position(x, hurdle.y + 1), 2, animation = JUMP_HURDLE,
                moveStart = 19, moveEnd = 50, sounds = listOf(AgilitySound(Sound.HURDLE)))), null,
                start = { trainerShout() })
        }
    }

    obstacle(5152, WEREWOLF_PIPE,
        allow = { pipe -> y < pipe.y || backwards() },
        startTile = { pipe -> Position(pipe.x, 9904) }) { pipe ->
        // The pipe runs under a rock: the player crawls into its south half and out of its north half, crouched in
        // between.
        AgilityAction(this, listOf(
            AgilityStep(Position(pipe.x, 9906), 2, animation = PIPE_SQUEEZE),
            AgilityStep(Position(pipe.x, 9908), 2, walkingAnimation = PIPE_READY, jump = true,
                sounds = listOf(AgilitySound(Sound.SQUEEZE_IN))),
            AgilityStep(Position(pipe.x, 9910), 3, animation = PIPE_SQUEEZE,
                sounds = listOf(AgilitySound(Sound.SQUEEZE_IN, tick = 3)))
        ), null, start = { trainerShout() })
    }

    obstacle(5136, WEREWOLF_SKULL_SLOPE,
        allow = { slope -> x > slope.x || backwards() },
        startTile = { slope -> Position(3533, slope.y) }) { slope ->
        AgilityAction(this, listOf(AgilityStep(position, 1, jump = true),
            AgilityStep(Position(3530, slope.y), 3, walkingAnimation = AgilityWalk.CLIMB.walk, begin = {
                if (inventory.contains(STICK)) trainerShout()
                else trainerShout("You idiot - you've forgotten the stick!!!!")
            })), null)
    }

    for (id in listOf(5139, 5140, 5141)) {
        obstacle(id, WEREWOLF_ZIP_LINE,
            allow = {
                if (equipment[Equipment.HEAD] == null) true else {
                    if (passesAsWerewolf()) {
                        newDialogue().npc(AGILITY_TRAINER, "You need to take your headgear off before you try",
                            "the Deathslide, otherwise you won't be able to get a", "good enough grip with your teeth.")
                            .open()
                    } else {
                        newDialogue().npc(AGILITY_TRAINER, "That headgear won't help you here, human!", "Take it off!")
                            .open()
                    }
                    false
                }
            },
            startTile = { ZIP_START },
            failsIf = { deathslideFails() },
            fail = { deathslide(ZIP_FALLS.random()) }) {
            deathslide(null)
        }
    }
}

npc1(STICK_TRAINER) {
    val sticks = plr.inventory.computeAmountForId(STICK)
    when {
        !plr.passesAsWerewolf() -> plr.newDialogue().npc(STICK_TRAINER, GRRR).open()
        sticks > 0 -> {
            plr.inventory.remove(Item(STICK, sticks))
            plr.agility.addExperience(190.0)
            plr.sendMessage("You give the stick to the werewolf.")
        }
        else -> plr.newDialogue()
            .npc(STICK_TRAINER, "Have you brought the stick yet?")
            .player("What stick?")
            .npc(STICK_TRAINER, "Come on, get round that course - I need", "something to chew!")
            .open()
    }
}

npc1(AGILITY_BOSS) {
    if (!plr.passesAsWerewolf()) {
        plr.newDialogue().npc(AGILITY_BOSS, GRRR).open()
        return@npc1
    }
    plr.newDialogue()
        .player("How do I use the agility course?")
        .npc(AGILITY_BOSS,
            "I'll throw you a stick, which you need to fetch as",
            "quickly as possible, from the area beyond the pipes.")
        .npc(AGILITY_BOSS,
            "Be wary of the deathslide - you must hang by your",
            "teeth, and if your strength is not up to the job you",
            "will fall into a pit of spikes. Also, I would advise not",
            "carrying too much extra weight.")
        .npc(AGILITY_BOSS,
            "Bring the stick back to the werewolf waiting at the end",
            "of the death slide to get your agility bonus.")
        .npc(AGILITY_BOSS,
            "I will throw your stick as soon as you jump onto the",
            "first stone.")
        .open()
}

npc1(TRAPDOOR_GUARD) {
    if (plr.passesAsWerewolf()) {
        // The doubled "and" is in Jagex's text.
        plr.newDialogue()
            .player("What's beneath the trapdoor?")
            .npc(TRAPDOOR_GUARD,
                "It's an agility course designed for lycanthropes like",
                "ourselves, my friend.")
            .player("Can I come in and use it?")
            .npc(TRAPDOOR_GUARD,
                "Certainly. The cavern contains two courses - on the",
                "west side is a Level 60 Agility Course, and and the",
                "east side is a Level 25 Skullball Course.")
            .open()
    } else {
        plr.newDialogue()
            .player("What's beneath the trapdoor?")
            .npc(TRAPDOOR_GUARD, "That's none of your business, human, and I'll", "never tell.")
            .player("Oh, come on - I'm only curious.")
            .npc(TRAPDOOR_GUARD,
                "If it wasn't my duty to stand here and guard our",
                "agility course from the likes of you, I would be",
                "relieving you of your life right now.")
            .player("So it's an agility course, then?")
            .npc(TRAPDOOR_GUARD, "No ... yes ... oh blast - you didn't hear me say", "anything, right?")
            .player("No problem. Can I come in?")
            .npc(TRAPDOOR_GUARD, "No, human - it's werewolves only.")
            .open()
    }
}

// The stick only lasts the lap, so a player who logs out loses it. The save is written before logout events run.
on(LoginEvent::class) {
    val sticks = plr.inventory.computeAmountForId(STICK)
    if (sticks > 0) plr.inventory.remove(Item(STICK, sticks))
}

// The trainer at the end of the zip line keeps reminding anyone nearby to bring him the stick.
on(ServerLaunchEvent::class) {
    var next = 0L
    world.schedule(1) {
        if (world.currentTick < next) return@schedule
        next = world.currentTick + rand(9, 15)
        world.npcs.filter { it.id == STICK_TRAINER }.forEach { trainer ->
            if (world.locator.findPlayers(trainer, 10) { true }.isNotEmpty()) {
                shout(trainer, STICK_TRAINER_LINES.random())
            }
        }
    }
}
