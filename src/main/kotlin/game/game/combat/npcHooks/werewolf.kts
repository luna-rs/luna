package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.npc1
import io.luna.game.model.mob.dialogue.Expression
import kotlin.random.Random

/**
 * Registers the citizens' native pre-damage transformation. Wolf forms use their ordinary melee profiles.
 *
 * @author lare96
 */
combat(*Werewolves.FORMS.keys.toIntArray()) {
    defend {
        if (Werewolves.beforeHit(npc, other, damage)) animationId = null
    }
}

// The human forms share one pool of Talk-to responses, as in Lost City's canafis_citizen script.
for (humanId in Werewolves.FORMS.keys) {
    npc1(humanId, focus = false) {
        // The citizen may transform while the player is walking over.
        if (targetNpc.id != humanId) return@npc1

        val dialogue = plr.newDialogue()
        when (Random.nextInt(10)) {
            0 -> dialogue.npc(humanId, Expression.ANGRY, "Try the inn. They get paid to put up with strangers.")
            1 -> dialogue.npc(humanId, Expression.ANGRY, "I was having a perfectly good day until you showed up.")
            2 -> dialogue.npc(humanId, Expression.ANGRY, "Go bother someone with fewer teeth.")
            3 -> dialogue.npc(humanId, Expression.ANGRY, "If you're selling something, keep walking.")
            4 -> dialogue.npc(humanId, Expression.ANGRY, "You're standing too close.")
            5 -> dialogue.npc(humanId, Expression.ANGRY, "I heard you coming from halfway across town.")
            6 -> dialogue.npc(humanId, Expression.ANGRY, "Who told you I wanted company?")
            7 -> dialogue.npc(humanId, Expression.ANGRY, "Go back the way you came while there's still light.")
            8 -> dialogue.npc(humanId, Expression.ANGRY, "You smell like someone from west of the river.")
                .player(Expression.QUIZZICAL, "You can tell where I've been by my smell?")
                .npc(humanId, Expression.ANGRY, "I can tell you're human.")
                .player(Expression.SHOCKED, "It must be the soap. I'll throw it out.")
            else -> dialogue.npc(humanId, Expression.ANGRY, "Keep pestering me and you'll see how sharp my teeth are.")
        }
        dialogue.open()
        targetNpc.interact(plr)
    }
}
