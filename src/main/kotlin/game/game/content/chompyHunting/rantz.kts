package game.content.chompyHunting

import api.predef.*
import game.content.chompyHunting.ChompyHunting.BLOATED_TOAD
import game.content.chompyHunting.ChompyHunting.COOKED_CHOMPY
import game.content.chompyHunting.ChompyHunting.OGRE_BOW
import game.content.chompyHunting.ChompyHunting.RAW_CHOMPY
import game.content.chompyHunting.ChompyHunting.boxText
import game.content.chompyHunting.ChompyHunting.chompyKills
import game.content.chompyHunting.ChompyHunting.lines
import game.content.questRewards.QuestRewards
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption

/**
 * Rantz, the ogre outside his cave in the Feldip Hills.
 */
val RANTZ = 1010

/**
 * A chompy bird hat: its rank, its feathers and the kills it needs.
 */
class Hat(val id: Int, val rank: String, val feathers: String, val kills: Int)

/**
 * The 18 chompy bird hats, from the fewest kills to the most.
 */
val HATS = listOf(
    Hat(2978, "Ogre Bowman", "1 Feather Plain", 30),
    Hat(2979, "Bowman", "1 Feather green", 40),
    Hat(2980, "Ogre Yeoman", "1 Feather Red", 50),
    Hat(2981, "Yeoman", "1 Feather Black", 70),
    Hat(2982, "Ogre Marksman", "1 Feather Rune", 95),
    Hat(2983, "Marksman", "1 Feather Gold", 125),
    Hat(2984, "Ogre Woodsman", "3 Feather plain", 170),
    Hat(2985, "Woodsman", "3 Feather Green", 225),
    Hat(2986, "Ogre Forester", "3 Feather Red", 300),
    Hat(2987, "Forester", "3 Feather black", 400),
    Hat(2988, "Ogre Bowmaster", "3 Feather Rune", 550),
    Hat(2989, "Bowmaster", "3 Feather Gold", 700),
    Hat(2990, "Ogre Expert", "5 Feather Plain", 1000),
    Hat(2991, "Expert", "5 Feather Green", 1300),
    Hat(2992, "Ogre Dragon Archer", "5 Feather Red", 1700),
    Hat(2993, "Dragon Archer", "5 Feather black", 2250),
    Hat(2994, "Expert Ogre Dragon Archer", "5 Feather rune", 3000),
    Hat(2995, "Expert Dragon Archer", "5 Feather Gold", 4000)
)

/**
 * The hats on each page of Rantz's hat menu.
 */
val HAT_PAGES = listOf(HATS.subList(0, 4), HATS.subList(4, 8), HATS.subList(8, 12), HATS.subList(12, 15),
                       HATS.subList(15, 18))

npc1(RANTZ) {
    // TODO Big Chompy Bird Hunting: until the quest is finished Rantz talks the player through it instead. Until the
    //  quest exists every player has finished it.
    if (!QuestRewards.owns(plr, OGRE_BOW)) sellBow(plr) else talk(plr)
}

useItem(RAW_CHOMPY).onNpc(RANTZ) {
    plr.newDialogue()
        .npc(RANTZ, *lines("Erk! You's creature can cook da chompy on da spit! Rantz not do it for you!"))
        .open()
}

useItem(COOKED_CHOMPY).onNpc(RANTZ) {
    plr.newDialogue()
        .give(Item(COOKED_CHOMPY), boxText("You offer Rantz the cooked chompy Bird."), false)
        .npc(RANTZ, *lines("Erm.. Da's very kindly of you creature!"))
        .first { it.inventory.remove(Item(COOKED_CHOMPY)) }
        .open()
}

useItem(BLOATED_TOAD).onNpc(RANTZ) {
    plr.newDialogue()
        .give(Item(BLOATED_TOAD), boxText("You show the bloated toad to Rantz."), false)
        .npc(RANTZ, *lines("Hey creature got da fatsy toady Is you's gonna stick da chompy?"))
        .open()
}

useItem(OGRE_BOW).onNpc(RANTZ) {
    plr.newDialogue()
        .npc(RANTZ, *lines("Yeah, it's a goodly stabbie chucker, but you's is too weedy to use it like Rantz."))
        .open()
}

/**
 * Rantz sells a replacement ogre bow to a player who has lost theirs.
 */
fun sellBow(plr: Player) {
    val cost = rand(500, 550)
    plr.newDialogue()
        .player(*lines("Hey, Rantz, I've lost the 'stabbie chucker', do you have another one please?"))
        .npc(RANTZ, *lines("Mee's has, but is not for freeness, dis one cost $cost of da gold coinses. " +
                           "Does you wants it?"))
        .options("Yes, I'll buy the bow.", {
            it.newDialogue().player(*lines("Yes, I'll buy the bow.")).then { plr ->
                when {
                    !QuestRewards.canPay(plr, cost) ->
                        plr.newDialogue()
                            .npc(RANTZ, *lines("Hey you don't got da coins... come back when you have."))
                            .then { talk(it) }
                            .open()
                    QuestRewards.give(plr, OGRE_BOW, cost) ->
                        plr.newDialogue()
                            .give(Item(OGRE_BOW), boxText("You hand over the coins, and Rantz hands over the bow."),
                                  false)
                            .player(*lines("Thanks."))
                            .then { talk(it) }
                            .open()
                }
            }.open()
        }, "No thanks...", {
            it.newDialogue().player(*lines("No thanks...")).then { plr -> talk(plr) }.open()
        })
        .open()
}

/**
 * Rantz's talk once the quest is done.
 */
fun talk(plr: Player) {
    plr.newDialogue()
        .npc(RANTZ, *lines("Hey deyr, t'anks for da chompy, it was scrumbly!"))
        .player(*lines("Hey, I hear you're giving away free hats!"))
        .npc(RANTZ, *lines("Huh, dat's a true thing but you's godda be da good shooter wiv da stabbie " +
                           "chucker and stick lotsa chompies for da hatsie."))
        .then { hatQuestions(it) }
        .open()
}

/**
 * The player's questions about the hats.
 */
fun hatQuestions(plr: Player) {
    plr.newDialogue()
        .options("What are the hats like?", { hatsLike(it) },
                 "Can you show me the hats please?", { showHats(it) },
                 "How many chompies do I have to stick?", { howMany(it) },
                 "Can I have a hat please?", { giveHats(it) },
                 "Ok, thanks.", { it.newDialogue().player(*lines("Ok, thanks.")).open() })
        .open()
}

fun hatsLike(plr: Player) {
    plr.newDialogue()
        .player(*lines("What are the hats like?"))
        .npc(RANTZ, "Huh der is this many colours:", "@blu@~ Rantz holds up six fingers. ~",
             "And der is dis many different hatsies:", "@blu@~ Rantz holds up three fingers. ~")
        .then { hatQuestions(it) }
        .open()
}

fun howMany(plr: Player) {
    plr.newDialogue()
        .player(*lines("How many Chompies do I have to stick?"))
        .npc(RANTZ, *lines("We'll dat's da fing wiv da hatsies, dey's many hatsies for da many chompies dat " +
                           "you stick. But you's godda get at least this many for a hat:"))
        .npc(RANTZ, "@blu@~ Rantz flashes his fingers.", "@blu@You think he means 30. ~")
        .then { hatQuestions(it) }
        .open()
}

fun showHats(plr: Player) {
    plr.newDialogue()
        .npc(RANTZ, "Ok you's creature, here's da hatsies!", "@blu@~ Rantz shows you the hats. ~")
        .then { hatPage(it, 0) }
        .open()
}

/**
 * One page of Rantz's hat menu. Picking a hat shows it and comes back to the same page.
 */
fun hatPage(plr: Player, page: Int) {
    val options = ArrayList<DialogueOption>()
    for (hat in HAT_PAGES[page]) {
        options += DialogueOption("${hat.rank} Hat.") { p ->
            p.newDialogue()
                .give(Item(hat.id), boxText("${hat.rank} hat. ${hat.feathers}. Kills : ${hat.kills}"), false)
                .then { hatPage(it, page) }
                .open()
        }
    }
    if (page < HAT_PAGES.size - 1) {
        options += DialogueOption("-- Hat Menu ${page + 2} --") { hatPage(it, page + 1) }
    } else {
        options += DialogueOption("-- Back to Hat Menu 1 --") { hatPage(it, 0) }
        options += DialogueOption("-- Close Hat Menu --") { hatQuestions(it) }
    }
    plr.newDialogue().options(options).title("*** Hat Menu ${page + 1} ***").open()
}

/**
 * Rantz looks at the notches on the player's bow and hands over every hat they have earned and don't have yet.
 */
fun giveHats(plr: Player) {
    val builder = plr.newDialogue()
        .player(*lines("Can I have a hat please?"))
        .npc(RANTZ, *lines("You's creature show me da bow den!"))
    if (!plr.inventory.contains(OGRE_BOW) && !plr.equipment.contains(OGRE_BOW)) {
        builder.player(*lines("Sorry Rantz, I don't have the bow."))
            .npc(RANTZ, *lines("Dat's da shame thing dat is! No flapper hat for you den!"))
            .open()
        return
    }
    builder.give(Item(OGRE_BOW), boxText("You show Rantz the marks on your bow."), false)
    val kills = plr.chompyKills
    val earned = HATS.filter { kills >= it.kills && !QuestRewards.owns(plr, it.id) }
    when {
        kills == 0 ->
            builder.npc(RANTZ, *lines("Hey, yous creature needs to hunt da chompie if you's wants der's flappy hat!"))
        kills < HATS[0].kills ->
            builder.npc(RANTZ, *lines("Hey, yous creature needs to hunt more chompies if you's wants der's " +
                                      "flappy hat!"))
        earned.isEmpty() ->
            builder.npc(RANTZ, *lines("Sorry, but der's no new hats for you this time! Go shoot some more chompies."))
        else -> {
            builder.npc(RANTZ, *lines("Hey, you did good wiv da stabbie chucker, der's a flappy hat for ya."))
                .then { handOver(it, earned, 0) }
        }
    }
    builder.open()
}

/**
 * Shows the player the hat at [next] in [hats] and gives it to them, then moves on to the one after it.
 */
fun handOver(plr: Player, hats: List<Hat>, next: Int) {
    if (next >= hats.size) return
    val hat = hats[next]
    plr.newDialogue()
        .give(Item(hat.id), "@blu@~ Rantz shows you a new hat that you can take ~\\n@blu@~ as a reward for " +
                            "your chompy bird kill total. ~\\n@dbl@~ This is the hat for @dre@${hat.rank}@dbl@ ~",
              false)
        .then {
            if (it.inventory.computeRemainingSize() < 1) {
                it.newDialogue()
                    .npc(RANTZ, "You's person has too much junk for da flappy hat...",
                         "Rantz keeps it insteads!  @blu@~ Rantz chuckles to himself. ~")
                    .open()
            } else {
                it.inventory.add(Item(hat.id))
                it.sendMessage("You take the hat.")
                handOver(it, hats, next + 1)
            }
        }
        .open()
}
