package game.content.abyss

import api.predef.*
import game.skill.runecrafting.essencePouch.EssencePouch
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.game.model.mob.dialogue.Expression

/**
 * The Dark mage in the inner ring of the Abyss, who holds the portal open.
 */
val DARK_MAGE = 2262

/**
 * Offers [plr] the Dark mage's topics, leaving out the one just asked.
 */
fun darkMageOptions(plr: Player, asked: String) {
    val topics = listOf<Pair<String, (Player) -> Unit>>(
        "Why not?" to { askWhyNot(it) },
        "What are you doing here?" to { askWhatHeIsDoing(it) },
        "Ok, Sorry." to { apologise(it) }).filter { it.first != asked }
    plr.newDialogue().options(topics[0].first, topics[0].second, topics[1].first, topics[1].second).open()
}

/**
 * The player asks the Dark mage why he can't be disturbed.
 */
fun askWhyNot(plr: Player) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "Why not?")
        .npc(DARK_MAGE, "Well, if my concentration is broken while keeping this",
             "gate open, then, if we are lucky, everyone within a one",
             "mile radius will either have their heads explode, or will be",
             "consumed internally by the creatures of the Abyss.")
        .player(Expression.CONFUSED, "Erm... And if we are unlucky?")
        .npc(DARK_MAGE, "If we are unlucky, then the entire universe will begin",
             "to fold in upon itself, and all reality as we know it will",
             "be annihilated in a single stroke.")
        .npc(DARK_MAGE, Expression.ANGRY, "So leave me alone!")
        .then { darkMageOptions(it, "Why not?") }
        .open()
}

/**
 * The player asks the Dark mage what he is doing in the Abyss.
 */
fun askWhatHeIsDoing(plr: Player) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "What are you doing here?")
        .npc(DARK_MAGE, "Do you mean what am I doing here in Abyssal space,",
             "or are you asking me what I consider my ultimate role",
             "to be in this voyage that we call life?")
        .player(Expression.QUIZZICAL, "Um... the first one.")
        .npc(DARK_MAGE, "By remaining here and holding this portal open, I am",
             "providing a permanent link between normal space and",
             "this strange dimension that we call Abyssal space.")
        .npc(DARK_MAGE, "As long as this spell remains in effect, we have the",
             "capability to teleport into abyssal space at will.")
        .npc(DARK_MAGE, Expression.ANGRY, "Now leave me be! I can afford no distraction in my", "task!")
        .then { darkMageOptions(it, "What are you doing here?") }
        .open()
}

/**
 * The player leaves the Dark mage alone.
 */
fun apologise(plr: Player) {
    plr.newDialogue()
        .player(Expression.SAD, "Ok, sorry.")
        .npc(DARK_MAGE, Expression.ANGRY, "I am attempting to subdue the elemental mechanisms of",
             "the universe to my will.")
        .npc(DARK_MAGE, Expression.ANGRY, "Inane chatter from random idiots is not helping me", "achieve this!")
        .open()
}

/**
 * The player asks the Dark mage for help.
 */
fun askForHelp(plr: Player) {
    plr.newDialogue()
        .player("Sorry to disturb you, I just needed your help with", "something quickly.")
        .npc(DARK_MAGE, Expression.ANGRY, "What?", "Oh...", "Very well. What did you want?")
        .then { offerHelp(it) }
        .open()
}

/**
 * Lets [plr] ask for whatever the Dark mage can help them with.
 */
fun offerHelp(plr: Player) {
    // TODO Enter the Abyss: he also hands another Abyssal book to a player who has lost theirs, and only replaces
    //  the small pouch for a player who has finished the miniquest.
    val topics = ArrayList<DialogueOption>()
    if (!EssencePouch.SMALL.isOwnedBy(plr)) {
        topics += DialogueOption("Can I have a new essence pouch?") { replaceSmallPouch(it) }
    }
    if (EssencePouch.needsRepair(plr)) {
        topics += DialogueOption("Can you repair my pouches?") { repairPouches(it) }
    }
    if (topics.isEmpty()) {
        needNothing(plr)
    } else {
        topics += DialogueOption("Actually, I can't think of anything right now...") { needNothing(it) }
        plr.newDialogue().options(topics).open()
    }
}

/**
 * The Dark mage replaces the player's lost small pouch.
 */
fun replaceSmallPouch(plr: Player) {
    plr.newDialogue()
        .player("Can I have a new essence pouch?")
        .then {
            if (it.inventory.isFull) {
                it.newDialogue()
                    .npc(DARK_MAGE, Expression.ANGRY, "Don't waste my time if you don't have enough free",
                         "space to take it.")
                    .open()
            } else {
                val pouch = EssencePouch.SMALL
                pouch.discardContents(it)
                it.newDialogue()
                    .npc(DARK_MAGE, "Here. Be more careful with your belongings in future.")
                    .give(Item(pouch.id), "You have been given a pouch.", false)
                    .open()
            }
        }
        .open()
}

/**
 * The Dark mage restores all of the player's pouches.
 */
fun repairPouches(plr: Player) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "I think my essence pouches might be degrading...", "Can you restore them for me?")
        .then {
            EssencePouch.repairAll(it)
            it.newDialogue()
                .npc(DARK_MAGE, Expression.ANGRY, "A simple transfiguration spell should resolve that for you.",
                     "Now leave me be!")
                .open()
        }
        .open()
}

/**
 * The player has nothing to ask the Dark mage.
 */
fun needNothing(plr: Player) {
    plr.newDialogue()
        .player("Actually, I can't think of anything right now...")
        .npc(DARK_MAGE, Expression.ANGRY, "THEN STOP DISTRACTING ME!")
        .npc(DARK_MAGE, "Honestly, you have no idea of the pressure I am under",
             "attempting to keep this portal open!")
        .open()
}

npc1(DARK_MAGE) {
    plr.newDialogue()
        .player("Hello there.")
        .npc(DARK_MAGE, Expression.ANGRY, "Quiet! You must not break my concentration!")
        .options("Why not?", { askWhyNot(it) },
                 "What are you doing here?", { askWhatHeIsDoing(it) },
                 "Ok, Sorry.", { apologise(it) },
                 "I need your help with something...", { askForHelp(it) })
        .open()
}
