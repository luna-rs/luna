// Adapted from Lost City Content, revision 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
package game.content.sheepShearer

import game.player.Sound
import game.skill.crafting.shearing.SheepShearing
import game.skill.crafting.textileCrafting.Textile
import io.luna.game.model.Direction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.dialogue.Expression.*
import io.luna.game.model.mob.varp.PersistentVarp

/**
 * Handles Fred's offer, wool deliveries and the optional encounter with The Thing.
 *
 * @author lare96
 */
object SheepShearerDialogue {
    const val THE_THING = 3579
    const val LOOKING_FOR_QUEST = "I'm looking for a quest."
    const val ACCEPT = "Yes okay. I can do that."
    const val KNOW_SHEARING = "Of course!"
    const val KNOW_SPINNING = "I'm something of an expert actually!"
    private const val FRED = SheepShearer.FRED
    private const val LOOKING_FOR_COMBAT = "I'm looking for something to kill."
    private const val LOST = "I'm lost."
    private const val NOT_EXCITING = "That doesn't sound a very exciting quest."
    private const val ASK_ABOUT_THING = "What do you mean, The Thing?"
    private const val DECLINE = "No, I'll give it a miss."
    private const val WORRIED_ABOUT_THING = "Erm I'm a bit worried about this thing."
    private const val LEARN_SHEARING = "Err. No, I don't know actually."
    private const val LEARN_SPINNING = "I don't know how to spin wool, sorry."
    const val BACK = "I'm back!"
    private const val SAW_THING = "Fred! Fred! I've seen The Thing!"
    private const val I_HAVE_WOOL = "I have some."
    private const val LAST_BALL = "That's the last of them."
    private const val GENERAL_STORE_ICON = 5094
    private const val SPINNING_WHEEL_ICON = 7670
    private val wool = Textile.BALL_OF_WOOL.rawItem.id
    private val ball = Textile.BALL_OF_WOOL.processedItem.id

    internal fun hasSeenThing(player: Player): Boolean =
        player.varpManager.getValue(PersistentVarp.THE_THING_SHEARED) == 1

    fun talk(player: Player) {
        val dialogue = player.newDialogue()
        when (SheepShearer.stage(player)) {
            SheepShearer.NOT_STARTED -> dialogue
                .npc(FRED, ANGRY, "What are you doing on my land? You're not the one who keeps leaving all my gates open and letting out all my sheep are you?")
                .options(LOOKING_FOR_QUEST, ::offer, LOOKING_FOR_COMBAT, ::combat, LOST, ::directions).open()
            SheepShearer.COMPLETE -> dialogue.npc(FRED, ANGRY, "What are you doing on my land?")
                .options(LOOKING_FOR_COMBAT, ::combat, LOST, ::directions).open()
            else -> {
                if (hasSeenThing(player)) {
                    dialogue.npc(FRED, ANGRY, "What are you doing on my land?")
                        .options(BACK, { delivery(it, true) }, SAW_THING, ::reportThing).open()
                } else {
                    delivery(player)
                }
            }
        }
    }

    fun shearThing(player: Player, npc: Npc) {
        if (SheepShearing.SHEARS !in player.inventory) {
            player.sendMessage("You need a pair of shears to do this.")
            return
        }
        player.varpManager.setAndSendValue(PersistentVarp.THE_THING_SHEARED, 1)
        player.animation(Animation(893))
        player.playSound(Sound.SHEAR_SHEEP)
        npc.animation(Animation(3570))
        npc.navigator.cancel()
        npc.walking.clear()
        npc.navigator.step(Direction.between(player.position, npc.position), false)
        player.sendMessage("The... whatever it is... manages to get away from you!")
    }

    private fun offer(player: Player) {
        player.newDialogue().player(LOOKING_FOR_QUEST)
            .npc(FRED, "You're after a quest, you say? Actually I could do with a bit of help.")
            .npc(FRED, "My sheep are getting mighty woolly. I'd be much obliged if you could shear them. And while you're at it spin the wool for me too.")
            .npc(FRED, HAPPY, "Bring me 20 balls of wool. I'm sure I could sort out some sort of payment. Of course, there's the small matter of The Thing.")
            .options(ACCEPT, ::accept, NOT_EXCITING, ::unexciting, ASK_ABOUT_THING, ::explainThing).open()
    }

    private fun unexciting(player: Player) {
        player.newDialogue().player(NOT_EXCITING)
            .npc(FRED, "Well what do you expect if you ask a farmer for a quest? Now are you going to help me or not?")
            .options(ACCEPT, ::accept, DECLINE, { it.newDialogue().player(DECLINE).open() }).open()
    }

    private fun explainThing(player: Player) {
        player.newDialogue().player(QUIZZICAL, ASK_ABOUT_THING)
            .npc(FRED, "Well now, no one has ever seen The Thing. That's why we call it The Thing, 'cos we don't know what it is.")
            .npc(FRED, WORRIED, "Some say it's a black hearted shapeshifter, hungering for the souls of hard working decent folk like me. Others say it's just a sheep.")
            .npc(FRED, ANGRY, "Well I don't have all day to stand around and gossip. Are you going to shear my sheep or what!")
            .options(ACCEPT, ::accept, WORRIED_ABOUT_THING, {
                it.newDialogue().player(CONFUSED, WORRIED_ABOUT_THING)
                    .npc(FRED, SHIFTY, "I'm sure it's nothing to worry about. It's possible the other shearers aren't dead at all. And are just hiding in the woods or something.")
                    .player("I'm not convinced.").open()
            }).open()
    }

    private fun accept(player: Player) {
        SheepShearer.start(player)
        player.newDialogue().player(HAPPY, ACCEPT)
            .npc(FRED, HAPPY, "Good! Now one more thing, do you actually know how to shear a sheep?")
            .options(KNOW_SHEARING, ::knowsShearing, LEARN_SHEARING, ::explainShearing).open()
    }

    private fun knowsShearing(player: Player) {
        player.newDialogue().player(HAPPY, KNOW_SHEARING)
            .npc(FRED, "And you know how to spin wool into balls?")
            .options(KNOW_SPINNING, ::knowsSpinning, LEARN_SPINNING, ::explainSpinning).open()
    }

    private fun explainShearing(player: Player) {
        val dialogue = player.newDialogue().player(LEARN_SHEARING)
        if (SheepShearing.SHEARS in player.inventory) {
            dialogue.npc(FRED, HAPPY, "You're halfway there already! You have a set of shears in your inventory. Just use those on a sheep to shear it.")
                .player("That's all I have to do?")
                .npc(FRED, "Once you've collected some wool you'll need to spin it into balls.")
        } else {
            dialogue.npc(FRED, "First things first, you need a pair of shears. There's a pair in the house on the table.")
                .npc(FRED, "Or you could buy your own pair from the General Store in Lumbridge. Travel east on the road outside.")
                .give(Item(GENERAL_STORE_ICON), "General Stores are marked on the map by this symbol.", false)
                .npc(FRED, "Once you get some shears use them on the sheep in my field. Some sheep will run away, so keep trying.")
        }
        dialogue.npc(FRED, QUIZZICAL, "Do you know how to spin wool?")
            .options(LEARN_SPINNING, ::explainSpinning, KNOW_SPINNING, ::knowsSpinning).open()
    }

    private fun knowsSpinning(player: Player) {
        player.newDialogue().player(HAPPY, KNOW_SPINNING)
            .npc(FRED, "Well you can stop grinning and get to work then.")
            .npc(FRED, ANGRY, "I'm not paying you by the hour!").open()
    }

    private fun explainSpinning(player: Player) {
        player.newDialogue().player(LEARN_SPINNING)
            .npc(FRED, "The nearest Spinning Wheel can be found on the first floor of Lumbridge Castle. Just follow the road east.")
            .give(Item(SPINNING_WHEEL_ICON), "This icon denotes a Spinning Wheel on the world map.", false)
            .player(HAPPY, "Thank you!").open()
    }

    private fun delivery(player: Player, back: Boolean = false) {
        val dialogue = player.newDialogue()
        if (back) dialogue.player(BACK)
        dialogue.npc(FRED, "How are you doing getting those balls of wool?")
        when {
            ball in player.inventory -> dialogue.player(HAPPY, I_HAVE_WOOL)
                .npc(FRED, "Give 'em here then.").then(::handOver)
            wool in player.inventory -> dialogue.player("I've got some wool. I've not managed to make it into a ball though.")
                .npc(FRED, "You can find a spinning wheel on the first floor of Lumbridge Castle. Walk east on the road outside my house.")
            else -> dialogue.player("How many more do I need to give you?")
                .npc(FRED, "You need to collect ${moreWool(player)}.")
                .player(SAD, "I haven't got any at the moment.")
                .npc(FRED, HAPPY, "Ah well at least you haven't been eaten.")
        }
        dialogue.open()
    }

    private fun handOver(player: Player) {
        val given = SheepShearer.deliver(player)
        if (SheepShearer.stage(player) == SheepShearer.LAST_BALL && ball in player.inventory) {
            player.newDialogue().player(HAPPY, LAST_BALL)
                .npc(FRED, SAD, "I guess I'd better pay you then.").then(SheepShearer::finish).open()
        } else if (given > 0) {
            val balls = if (given == 1) "ball" else "balls"
            player.newDialogue().text("You give Fred $given $balls of wool")
                .player(HAPPY, "That's all I've got so far.")
                .npc(FRED, "I need ${moreWool(player)} before I can pay you.")
                .player("Ok, I'll work on it.").open()
        }
    }

    private fun moreWool(player: Player): String {
        val left = SheepShearer.ballsRemaining(player)
        return "$left more ${if (left == 1) "ball" else "balls"} of wool"
    }

    private fun reportThing(player: Player) {
        player.newDialogue().player(SHOCKED, SAW_THING)
            .npc(FRED, WORRIED, "You ... you actually saw it? Run for the hills! Grab as many chickens as you can!")
            .player(SHOCKED, "Fred!")
            .npc(FRED, WORRIED, "Oh, woe is me! The shapeshifter is coming! We're all doomed!")
            .player(ANGRY, "FRED! It's not a shapeshifter or any other kind of monster!")
            .npc(FRED, QUIZZICAL, "Well then, what is it?")
            .player(QUIZZICAL, "It's just two penguins. Penguins disguised as a sheep.")
            .npc(FRED, SHOCKED, "Have you been out in the sun too long?")
            .then { delivery(it) }.open()
    }

    private fun combat(player: Player) {
        player.newDialogue().player(HAPPY, LOOKING_FOR_COMBAT)
            .npc(FRED, ANGRY, "What, on my land? Leave my livestock alone you scoundrel!").open()
    }

    private fun directions(player: Player) {
        player.newDialogue().player(SAD, LOST)
            .npc(FRED, CONFUSED, "How can you be lost? Just follow the road east and south. You'll end up in Lumbridge fairly quickly.").open()
    }
}
