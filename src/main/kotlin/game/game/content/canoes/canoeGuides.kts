package game.content.canoes

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder

/**
 * Has [guide] and [plr] say [chats] in turn, ending on whatever [ending] adds.
 */
fun say(plr: Player, guide: CanoeGuide, chats: List<GuideChat>, ending: (DialogueQueueBuilder) -> Unit = {}) {
    val talk = plr.newDialogue()
    for (chat in chats) {
        if (chat.guide) talk.npc(guide.id, *chat.lines) else talk.player(*chat.lines)
    }
    ending(talk)
    talk.open()
}

/**
 * Has [plr] ask [guide] about canoes with [ask]. What they hear depends on their Woodcutting level.
 */
fun teach(plr: Player, guide: CanoeGuide, ask: String) {
    val best = CanoeType.values().lastOrNull { plr.woodcutting.level >= it.level }
    val answer = if (best == null) CanoeGuide.UNSKILLED else guide.howTo + guide.advice.getValue(best)
    say(plr, guide, listOf(GuideChat(false, arrayOf(ask))) + answer)
}

/**
 * Has [guide] tell [plr] about themself, then offer to explain canoes.
 */
fun tellStory(plr: Player, guide: CanoeGuide) {
    say(plr, guide, guide.story) {
        it.options("Yes", { asker -> teach(asker, guide, guide.storyAsk) },
                   "No", { asker -> asker.newDialogue().player("No thanks, not right now.").open() })
    }
}

for (guide in CanoeGuide.values()) {
    npc1(guide.id) {
        say(plr, guide, listOf(GuideChat(false, arrayOf("Hello there."))) + guide.greeting) {
            it.options("Who are you?", { asker -> tellStory(asker, guide) },
                       "Can you teach me about Canoeing?", { asker -> teach(asker, guide, guide.menuAsk) })
        }
    }
}
